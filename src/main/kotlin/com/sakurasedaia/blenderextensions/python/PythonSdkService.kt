package com.sakurasedaia.blenderextensions.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class PythonSdkService(private val project: Project) {

    fun ensureVirtualEnvironment(requestedPythonVersion: String? = null): Sdk? {
        val logger = BlenderLogger.getInstance(project)
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk
        
        if (projectSdk != null && isPythonSdk(projectSdk) && isVenv(projectSdk)) {
            if (requestedPythonVersion != null) {
                val currentVersion = projectSdk.versionString?.removePrefix("Python ")
                if (currentVersion != null && currentVersion.startsWith(requestedPythonVersion.substringBeforeLast("."))) {
                    return projectSdk
                }
                
                logger.log(LangManager.message("log.python.venv.recreating"))
                
                val recreatePermission = promptForVenvRecreation(requestedPythonVersion)
                if (recreatePermission != Messages.YES) {
                    return projectSdk
                }
                
                removeVirtualEnvironment(projectSdk)
            } else {
                return projectSdk
            }
        }

        val projectRoot = project.basePath?.let { Path.of(it) } ?: return null
        val venvDir = projectRoot.resolve(".venv")
        
        if (venvDir.exists()) {
            val pythonExe = getVenvPythonExecutable(venvDir)
            val existingSdk = ProjectJdkTable.getInstance().allJdks.find { it.homePath == pythonExe.toString() }
            removeVirtualEnvironment(existingSdk, venvDir)
        }
        
        if (!venvDir.exists()) {
            var uvExe = UvUtil.findUvExecutable(project)
            if (uvExe == null) {
                val installPermission = promptForUvInstallation()
                if (installPermission == Messages.YES) {
                    if (UvUtil.installUv(project)) {
                        uvExe = UvUtil.findUvExecutable(project)
                    }
                }
            }

            if (uvExe != null) {
                if (!UvUtil.createVenv(uvExe, venvDir, requestedPythonVersion, project)) {
                    logger.log(LangManager.message("log.python.venv.create.failed", "uv failed to create environment"))
                    return null
                }
            } else {
                logger.log(LangManager.message("log.python.uv.not.found"))
                return null
            }
        }

        val pythonExe = getVenvPythonExecutable(venvDir)
        if (!pythonExe.exists()) {
            logger.log(LangManager.message("log.python.venv.not.found", pythonExe.toString()))
            return null
        }

        return createAndSetProjectSdk(pythonExe)
    }

    private fun promptForVenvRecreation(requestedPythonVersion: String): Int {
        var result = Messages.NO
        val message = LangManager.message("dialog.permission.python.venv.recreate.message", requestedPythonVersion)
        val title = LangManager.message("dialog.permission.python.venv.recreate.title")
        
        if (ApplicationManager.getApplication().isDispatchThread) {
            result = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
        } else {
            ApplicationManager.getApplication().invokeAndWait {
                result = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
            }
        }
        return result
    }

    private fun promptForUvInstallation(): Int {
        var result = Messages.NO
        val message = LangManager.message("dialog.permission.uv.install.message")
        val title = LangManager.message("dialog.permission.uv.install.title")

        if (ApplicationManager.getApplication().isDispatchThread) {
            result = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
        } else {
            ApplicationManager.getApplication().invokeAndWait {
                result = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
            }
        }
        return result
    }

    private fun createAndSetProjectSdk(pythonExe: Path): Sdk? {
        val sdkType = SdkType.getAllTypes().find { isPythonSdkTypeName(it.name) } ?: return null
        val sdkName = "Python 3.x (BlenderExtensions)"
        
        var sdk = ProjectJdkTable.getInstance().allJdks.find { it.homePath == pythonExe.toString() }
        if (sdk == null) {
            val newSdk = ProjectJdkTable.getInstance().createSdk(sdkName, sdkType)
            val modificator = newSdk.sdkModificator
            modificator.homePath = pythonExe.toString()
            val version = PythonUtil.getPythonVersion(pythonExe, project)
            val fullVersion = version?.let { if (it.startsWith("Python")) it else "Python $it" }
            modificator.versionString = fullVersion
            
            // Set a better name including version
            val betterName = if (version != null) "uv (BlenderExtensions $version)" else "uv (BlenderExtensions)"
            modificator.name = betterName

            // Try to set PyUvSdkAdditionalData via reflection for "official" PyCharm uv integration
            try {
                val additionalDataClass = Class.forName("com.jetbrains.python.sdk.uv.PyUvSdkAdditionalData")
                val uvExe = UvUtil.findUvExecutable(project)
                if (uvExe != null) {
                    val constructor = additionalDataClass.getConstructor(java.lang.String::class.java)
                    val additionalData = constructor.newInstance(uvExe.toString())
                    
                    // SdkModificator has setSdkAdditionalData(SdkAdditionalData)
                    val setAdditionalDataMethod = modificator.javaClass.getMethod("setSdkAdditionalData", Class.forName("com.intellij.openapi.projectRoots.SdkAdditionalData"))
                    setAdditionalDataMethod.invoke(modificator, additionalData)
                    BlenderLogger.debug(project, "PythonSdkService: Successfully set PyUvSdkAdditionalData via reflection")
                }
            } catch (e: Exception) {
                BlenderLogger.debug(project, "PythonSdkService: PyUvSdkAdditionalData not available or failed to set: ${e.message}")
            }
            
            ApplicationManager.getApplication().invokeAndWait {
                ApplicationManager.getApplication().runWriteAction {
                    modificator.commitChanges()
                    ProjectJdkTable.getInstance().addJdk(newSdk)
                }
            }
            sdk = newSdk
        } else {
            // If SDK already exists, ensure it has the uv metadata if missing
            try {
                if (sdk.sdkAdditionalData == null) {
                    val additionalDataClass = Class.forName("com.jetbrains.python.sdk.uv.PyUvSdkAdditionalData")
                    val uvExe = UvUtil.findUvExecutable(project)
                    if (uvExe != null) {
                        val constructor = additionalDataClass.getConstructor(java.lang.String::class.java)
                        val additionalData = constructor.newInstance(uvExe.toString())
                        
                        ApplicationManager.getApplication().invokeAndWait {
                            ApplicationManager.getApplication().runWriteAction {
                                val modificator = sdk.sdkModificator
                                modificator.sdkAdditionalData = additionalData as com.intellij.openapi.projectRoots.SdkAdditionalData
                                modificator.commitChanges()
                            }
                        }
                        BlenderLogger.debug(project, "PythonSdkService: Successfully updated existing SDK with PyUvSdkAdditionalData")
                    }
                }
            } catch (e: Exception) {
                // Ignore errors on existing SDK update
            }
        }

        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = sdk
            }
        }
        return sdk
    }

    fun removeVirtualEnvironment(sdk: Sdk?, venvPath: Path? = null) {
        val logger = BlenderLogger.getInstance(project)
        val actualVenvPath = venvPath ?: sdk?.homePath?.let { getVenvRootFromExecutable(Path.of(it)) }

        logger.log(LangManager.message("log.python.venv.removing"))

        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                val rootManager = ProjectRootManager.getInstance(project)
                if (rootManager.projectSdk == sdk) {
                    rootManager.projectSdk = null
                }
            }
        }

        if (sdk != null) {
            ApplicationManager.getApplication().invokeAndWait {
                ApplicationManager.getApplication().runWriteAction {
                    ProjectJdkTable.getInstance().removeJdk(sdk)
                }
            }
        }

        if (actualVenvPath != null && actualVenvPath.exists()) {
            try {
                if (actualVenvPath.toFile().deleteRecursively()) {
                    logger.log(LangManager.message("log.python.venv.removed", actualVenvPath.toString()))
                } else {
                    logger.log(LangManager.message("log.python.venv.remove.failed", actualVenvPath.toString(), "Could not delete all files"))
                }
            } catch (e: Exception) {
                logger.log(LangManager.message("log.python.venv.remove.failed", actualVenvPath.toString(), e.message ?: "Unknown error"))
            }
        }
    }

    private fun getVenvPythonExecutable(venvDir: Path): Path {
        return if (BlenderHelper.isWindows()) venvDir.resolve("Scripts").resolve("python.exe") else venvDir.resolve("bin").resolve("python")
    }

    private fun getVenvRootFromExecutable(exePath: Path): Path {
        return if (exePath.parent?.fileName?.toString() in listOf("bin", "Scripts")) {
            exePath.parent.parent
        } else {
            exePath.parent
        }
    }

    fun isVenv(sdk: Sdk): Boolean {
        val additionalData = sdk.sdkAdditionalData
        if (additionalData != null && (additionalData.javaClass.simpleName.contains("Uv") || 
            additionalData.javaClass.name.contains("uv") ||
            additionalData.javaClass.simpleName.contains("Venv") ||
            additionalData.javaClass.name.contains("venv"))) {
            return true
        }

        // Try to check flavor via reflection for "official" PyCharm identification
        try {
            val sdkExtClass = Class.forName("com.jetbrains.python.sdk.PySdkExtKt")
            val getSdkFlavorMethod = sdkExtClass.getMethod("getSdkFlavor", Sdk::class.java)
            val flavor = getSdkFlavorMethod.invoke(null, sdk)
            if (flavor != null) {
                val flavorName = flavor.javaClass.simpleName
                if (flavorName.contains("Uv", ignoreCase = true) || flavorName.contains("Venv", ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore reflection errors
        }
        
        val homePath = sdk.homePath ?: return false
        val path = Path.of(homePath)
        return path.parent?.fileName?.toString() in listOf("bin", "Scripts") || 
               homePath.contains(".venv") || homePath.contains("venv") || homePath.contains("site-packages")
    }

    fun isPythonSdk(sdk: Sdk): Boolean {
        return isPythonSdkTypeName(sdk.sdkType.name) || sdk.sdkType.javaClass.simpleName.contains("Python", ignoreCase = true)
    }

    private fun isPythonSdkTypeName(name: String): Boolean {
        return name == "Python SDK" || name == "Python"
    }

    companion object {
        fun getInstance(project: Project): PythonSdkService = project.getService(PythonSdkService::class.java)
    }
}
