package com.sakurasedaia.blenderextensions.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.common.BlenderProjectPaths
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class PythonSdkService(private val project: Project) {

    fun ensureVirtualEnvironment(requestedPythonVersion: String? = null): Sdk? {
        val logger = BlenderLogger.getInstance(project)
        BlenderLogger.debug(project, "PythonSdkService: Starting ensureVirtualEnvironment(requestedPythonVersion=$requestedPythonVersion)")
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk
        
        if (projectSdk != null && isPythonSdk(projectSdk) && isVenv(projectSdk)) {
            BlenderLogger.debug(project, "PythonSdkService: Current project SDK is a Python venv: ${projectSdk.name}")
            if (requestedPythonVersion != null) {
                val currentVersion = projectSdk.versionString?.removePrefix("Python ")
                BlenderLogger.debug(project, "PythonSdkService: Checking version: current=$currentVersion, requested=$requestedPythonVersion")
                if (currentVersion != null && currentVersion == requestedPythonVersion) {
                    BlenderLogger.debug(project, "PythonSdkService: Version matches. Returning current SDK.")
                    return projectSdk
                }
                
                logger.log(LangManager.message("log.python.venv.recreating"))
                BlenderLogger.debug(project, "PythonSdkService: Version mismatch or recreation requested. Prompting for recreation.")
                
                val recreatePermission = promptForVenvRecreation(requestedPythonVersion, currentVersion ?: "Unknown")
                if (!recreatePermission) {
                    BlenderLogger.debug(project, "PythonSdkService: Recreation declined by user. Keeping current SDK.")
                    return projectSdk
                }
                
                BlenderLogger.debug(project, "PythonSdkService: Removing existing virtual environment for recreation.")
                removeVirtualEnvironment(projectSdk)
            } else {
                BlenderLogger.debug(project, "PythonSdkService: No specific version requested. Keeping current SDK.")
                return projectSdk
            }
        } else {
            BlenderLogger.debug(project, "PythonSdkService: No valid Python venv SDK found in ProjectRootManager.")
        }

        val venvDir = try { BlenderProjectPaths.getVenvDir(project) } catch (e: Exception) { return null }
        BlenderLogger.debug(project, "PythonSdkService: Checking for ${BlenderProjectPaths.VENV_NAME} at $venvDir")
        
        if (venvDir.exists()) {
            BlenderLogger.debug(project, "PythonSdkService: ${BlenderProjectPaths.VENV_NAME} directory exists.")
            val pythonExe = getVenvPythonExecutable(venvDir)
            if (pythonExe.exists()) {
                BlenderLogger.debug(project, "PythonSdkService: Python executable found at $pythonExe")
                val currentVersion = PythonUtil.getPythonVersion(pythonExe, project)
                BlenderLogger.debug(project, "PythonSdkService: ${BlenderProjectPaths.VENV_NAME} Python version: $currentVersion")
                if (requestedPythonVersion == null || (currentVersion != null && currentVersion.startsWith(requestedPythonVersion))) {
                    BlenderLogger.debug(project, "PythonSdkService: ${BlenderProjectPaths.VENV_NAME} is compatible. Registering/Setting as project SDK.")
                    // Even if it's the right version, we want to ensure it's properly registered
                    return createAndSetProjectSdk(pythonExe)
                }
                BlenderLogger.debug(project, "PythonSdkService: ${BlenderProjectPaths.VENV_NAME} is incompatible with requested version $requestedPythonVersion.")
            } else {
                BlenderLogger.debug(project, "PythonSdkService: Python executable NOT found in existing ${BlenderProjectPaths.VENV_NAME}.")
            }
            
            // If we reach here, the venv exists but is not compatible or requestedVersion is different
            val existingSdk = ProjectJdkTable.getInstance().allJdks.find { it.homePath == pythonExe.toString() }
            BlenderLogger.debug(project, "PythonSdkService: Removing incompatible/broken ${BlenderProjectPaths.VENV_NAME}.")
            removeVirtualEnvironment(existingSdk, venvDir)
        }
        
        if (!venvDir.exists()) {
            BlenderLogger.debug(project, "PythonSdkService: ${BlenderProjectPaths.VENV_NAME} does not exist. Attempting to create one.")
            var uvExe = UvUtil.findUvExecutable(project)
            if (uvExe == null) {
                BlenderLogger.debug(project, "PythonSdkService: uv executable not found. Prompting for installation.")
                val installPermission = promptForUvInstallation()
                if (installPermission) {
                    BlenderLogger.debug(project, "PythonSdkService: User agreed to install uv.")
                    if (UvUtil.installUv(project)) {
                        uvExe = UvUtil.findUvExecutable(project)
                        BlenderLogger.debug(project, "PythonSdkService: uv installed successfully. Path: $uvExe")
                    } else {
                        BlenderLogger.debug(project, "PythonSdkService: Failed to install uv.")
                    }
                } else {
                    BlenderLogger.debug(project, "PythonSdkService: User declined uv installation.")
                }
            } else {
                BlenderLogger.debug(project, "PythonSdkService: uv executable found at $uvExe")
            }

            if (uvExe != null) {
                BlenderLogger.debug(project, "PythonSdkService: Creating venv using uv (version: $requestedPythonVersion)")
                if (!UvUtil.createVenv(uvExe, venvDir, requestedPythonVersion, project)) {
                    logger.log(LangManager.message("log.python.venv.create.failed", "uv failed to create environment"))
                    BlenderLogger.debug(project, "PythonSdkService: uv failed to create virtual environment.")
                    return null
                }
                BlenderLogger.debug(project, "PythonSdkService: venv created successfully by uv.")
            } else {
                logger.log(LangManager.message("log.python.uv.not.found"))
                BlenderLogger.debug(project, "PythonSdkService: Cannot create venv because uv is missing.")
                return null
            }
        }

        val pythonExe = getVenvPythonExecutable(venvDir)
        if (!pythonExe.exists()) {
            logger.log(LangManager.message("log.python.venv.not.found", pythonExe.toString()))
            BlenderLogger.debug(project, "PythonSdkService: venv was supposed to be created but Python exe not found at $pythonExe")
            return null
        }

        BlenderLogger.debug(project, "PythonSdkService: Finalizing setup with $pythonExe")
        return createAndSetProjectSdk(pythonExe)
    }

    private fun promptForVenvRecreation(requestedPythonVersion: String, currentVersion: String): Boolean {
        val message = LangManager.message("dialog.permission.python.venv.recreate.message", requestedPythonVersion, currentVersion, project.name)
        val title = LangManager.message("dialog.permission.python.venv.recreate.title")

        return MessageDialogBuilder.yesNo(title, message)
            .ask(project)
    }

    private fun promptForUvInstallation(): Boolean {
        val message = LangManager.message("dialog.permission.uv.install.message")
        val title = LangManager.message("dialog.permission.uv.install.title")

        return MessageDialogBuilder.yesNo(title, message)
            .ask(project)
    }

    private fun createAndSetProjectSdk(pythonExe: Path): Sdk? {
        val sdkName = "Python 3.x (BlenderExtensions)"
        BlenderLogger.debug(project, "PythonSdkService: Entering createAndSetProjectSdk(pythonExe=$pythonExe)")
        
        var sdk = ProjectJdkTable.getInstance().allJdks.find { it.homePath == pythonExe.toString() }
        if (sdk == null) {
            BlenderLogger.debug(project, "PythonSdkService: SDK not found in ProjectJdkTable. Creating new SDK.")
            val sdkType = findPythonSdkType()
            if (sdkType == null) {
                BlenderLogger.debug(project, "PythonSdkService: Failed to resolve Python SDK type for $pythonExe")
                return null
            }
            BlenderLogger.debug(project, "PythonSdkService: Using SDK type: ${sdkType.name}")
            val newSdk = ApplicationManager.getApplication().runReadAction<Sdk> {
                ProjectJdkTable.getInstance().createSdk(sdkName, sdkType)
            }
            val modificator = newSdk.sdkModificator
            modificator.homePath = pythonExe.toString()
            val version = PythonUtil.getPythonVersion(pythonExe, project)
            val fullVersion = version?.let { if (it.startsWith("Python")) it else "Python $it" }
            modificator.versionString = fullVersion
            BlenderLogger.debug(project, "PythonSdkService: Detected version: $fullVersion")
            
            // Set a better name including version
            val betterName = if (version != null) "uv (BlenderExtensions $version)" else "uv (BlenderExtensions)"
            modificator.name = betterName
            BlenderLogger.debug(project, "PythonSdkService: Setting SDK name to: $betterName")

            // Try to set PyUvSdkAdditionalData via reflection for "official" PyCharm uv integration
            try {
                BlenderLogger.debug(project, "PythonSdkService: Attempting to set PyUvSdkAdditionalData via reflection.")
                val additionalDataClass = runCatching { Class.forName("com.jetbrains.python.sdk.uv.PyUvSdkAdditionalData") }.getOrNull()
                    ?: runCatching { Class.forName("com.jetbrains.python.sdk.uv.impl.PyUvSdkAdditionalData") }.getOrNull()

                val uvExe = UvUtil.findUvExecutable(project)
                if (additionalDataClass != null && uvExe != null) {
                    val constructor = additionalDataClass.getConstructor(java.lang.String::class.java)
                    val additionalData = constructor.newInstance(uvExe.toString())
                    
                    // SdkModificator has setSdkAdditionalData(SdkAdditionalData)
                    val setAdditionalDataMethod = modificator.javaClass.getMethod("setSdkAdditionalData", Class.forName("com.intellij.openapi.projectRoots.SdkAdditionalData"))
                    setAdditionalDataMethod.invoke(modificator, additionalData)
                    BlenderLogger.debug(project, "PythonSdkService: Successfully set PyUvSdkAdditionalData via reflection")

                    // Safely trigger SDK path update via official PythonSdkUpdater
                    ApplicationManager.getApplication().invokeAndWait {
                        try {
                            BlenderLogger.debug(project, "PythonSdkService: Triggering PythonSdkUpdater.update via reflection.")
                            val updaterClass = Class.forName("com.jetbrains.python.sdk.PythonSdkUpdater")
                            val updateMethod = updaterClass.getMethod("update", Sdk::class.java, Project::class.java)
                            updateMethod.invoke(null, newSdk, project)
                            BlenderLogger.debug(project, "PythonSdkService: Successfully triggered PythonSdkUpdater.update via reflection")
                        } catch (e: Exception) {
                            BlenderLogger.debug(project, "PythonSdkService: Failed to trigger PythonSdkUpdater.update: ${e.message}")
                        }
                    }
                } else {
                    BlenderLogger.debug(project, "PythonSdkService: uv additional data class or uv executable not found, skipping PyUvSdkAdditionalData.")
                }
            } catch (e: Exception) {
                BlenderLogger.debug(project, "PythonSdkService: PyUvSdkAdditionalData not available or failed to set: ${e.message}")
            }
            
            ApplicationManager.getApplication().invokeAndWait {
                ApplicationManager.getApplication().runWriteAction {
                    BlenderLogger.debug(project, "PythonSdkService: Committing changes and adding SDK to ProjectJdkTable.")
                    modificator.commitChanges()
                    ProjectJdkTable.getInstance().addJdk(newSdk)
                }
            }
            sdk = newSdk
        } else {
            BlenderLogger.debug(project, "PythonSdkService: SDK already exists in ProjectJdkTable: ${sdk.name}")
            // If SDK already exists, ensure it has the uv metadata if missing
            try {
                if (sdk.sdkAdditionalData == null) {
                    BlenderLogger.debug(project, "PythonSdkService: Existing SDK missing additional data. Attempting to update.")
                    val additionalDataClass = runCatching { Class.forName("com.jetbrains.python.sdk.uv.PyUvSdkAdditionalData") }.getOrNull()
                        ?: runCatching { Class.forName("com.jetbrains.python.sdk.uv.impl.PyUvSdkAdditionalData") }.getOrNull()

                    val uvExe = UvUtil.findUvExecutable(project)
                    if (additionalDataClass != null && uvExe != null) {
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

                        // Safely trigger SDK path update via official PythonSdkUpdater for existing SDK
                        ApplicationManager.getApplication().invokeAndWait {
                            try {
                                val updaterClass = Class.forName("com.jetbrains.python.sdk.PythonSdkUpdater")
                                val updateMethod = updaterClass.getMethod("update", Sdk::class.java, Project::class.java)
                                updateMethod.invoke(null, sdk, project)
                                BlenderLogger.debug(project, "PythonSdkService: Successfully triggered PythonSdkUpdater.update for existing SDK via reflection")
                            } catch (e: Exception) {
                                BlenderLogger.debug(project, "PythonSdkService: Failed to trigger PythonSdkUpdater.update for existing SDK: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                BlenderLogger.debug(project, "PythonSdkService: Failed to update existing SDK metadata: ${e.message}")
                // Ignore errors on existing SDK update
            }
        }

        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                BlenderLogger.debug(project, "PythonSdkService: Setting ProjectRootManager.projectSdk to ${sdk?.name}")
                ProjectRootManager.getInstance(project).projectSdk = sdk
                
                // Also set it for all modules to be sure
                ModuleManager.getInstance(project).modules.forEach { module ->
                    BlenderLogger.debug(project, "PythonSdkService: Setting module SDK for ${module.name}")
                    ModuleRootModificationUtil.setModuleSdk(module, sdk)
                }
            }
        }
        
        // Force sync project SDK to ensure it's picked up
        project.save()
        BlenderLogger.debug(project, "PythonSdkService: Assigned SDK ${sdk?.name} as project interpreter")
        return sdk
    }

    fun removeVirtualEnvironment(sdk: Sdk?, venvPath: Path? = null) {
        val logger = BlenderLogger.getInstance(project)
        val actualVenvPath = venvPath ?: sdk?.homePath?.let { getVenvRootFromExecutable(Path.of(it)) }
        BlenderLogger.debug(project, "PythonSdkService: Entering removeVirtualEnvironment(sdk=${sdk?.name}, venvPath=$venvPath)")
        BlenderLogger.debug(project, "PythonSdkService: Resolved actualVenvPath=$actualVenvPath")

        logger.log(LangManager.message("log.python.venv.removing"))

        // 1. Deactivate and unregister from the project
        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                val rootManager = ProjectRootManager.getInstance(project)
                if (rootManager.projectSdk == sdk || (sdk != null && rootManager.projectSdk?.homePath == sdk.homePath)) {
                    BlenderLogger.debug(project, "PythonSdkService: Unsetting ProjectRootManager.projectSdk")
                    rootManager.projectSdk = null
                }
            }
        }

        // 2. Remove from global JDK table
        if (sdk != null) {
            ApplicationManager.getApplication().invokeAndWait {
                ApplicationManager.getApplication().runWriteAction {
                    BlenderLogger.debug(project, "PythonSdkService: Removing SDK ${sdk.name} from ProjectJdkTable")
                    ProjectJdkTable.getInstance().removeJdk(sdk)
                }
            }
        } else if (actualVenvPath != null) {
            // Also try to find by path even if sdk is null
            val pythonExe = getVenvPythonExecutable(actualVenvPath)
            val existingByPath = ProjectJdkTable.getInstance().allJdks.find { it.homePath == pythonExe.toString() }
            if (existingByPath != null) {
                BlenderLogger.debug(project, "PythonSdkService: Found existing SDK by path ${existingByPath.homePath}, removing from ProjectJdkTable")
                ApplicationManager.getApplication().invokeAndWait {
                    ApplicationManager.getApplication().runWriteAction {
                        ProjectJdkTable.getInstance().removeJdk(existingByPath)
                    }
                }
            }
        }

        // 3. Delete files
        if (actualVenvPath != null && actualVenvPath.exists()) {
            if (!isSafeToDelete(actualVenvPath)) {
                logger.log(LangManager.message("log.python.venv.remove.unsafe", actualVenvPath.toString()))
                BlenderLogger.debug(project, "PythonSdkService: SKIPPING deletion of unsafe path: $actualVenvPath")
                return
            }

            try {
                BlenderLogger.debug(project, "PythonSdkService: Deleting venv directory: $actualVenvPath")
                if (actualVenvPath.toFile().deleteRecursively()) {
                    logger.log(LangManager.message("log.python.venv.removed", actualVenvPath.toString()))
                    BlenderLogger.debug(project, "PythonSdkService: venv directory deleted successfully.")
                } else {
                    logger.log(LangManager.message("log.python.venv.remove.failed", actualVenvPath.toString(), "Could not delete all files"))
                    BlenderLogger.debug(project, "PythonSdkService: Failed to delete all files in venv directory.")
                }
            } catch (e: Exception) {
                logger.log(LangManager.message("log.python.venv.remove.failed", actualVenvPath.toString(), e.message ?: "Unknown error"))
                BlenderLogger.debug(project, "PythonSdkService: Exception while deleting venv directory: ${e.message}")
            }
        } else {
            BlenderLogger.debug(project, "PythonSdkService: venv directory does not exist or path is null, skipping file deletion.")
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

    internal fun isSafeToDelete(path: Path): Boolean {
        val absPath = path.toAbsolutePath()
        val projectPath = project.basePath?.let { Path.of(it).toAbsolutePath() }

        // 1. Never delete project root
        if (projectPath != null && absPath == projectPath) {
            BlenderLogger.debug(project, "Safety: Refusing to delete project root: $absPath")
            return false
        }

        // 2. Never delete system paths
        if (isSystemPath(absPath)) {
            BlenderLogger.debug(project, "Safety: Refusing to delete system path: $absPath")
            return false
        }

        // 3. Must contain pyvenv.cfg to be considered a deletable venv
        val hasCfg = absPath.resolve(BlenderProjectPaths.PYVENV_CFG_NAME).exists()

        // 4. Or it must be a specific sandbox directory
        val name = absPath.fileName?.toString() ?: ""
        val isSandbox = name == BlenderProjectPaths.SANDBOX_NAME

        if (!hasCfg && !isSandbox) {
            BlenderLogger.debug(project, "Safety: Path does not look like a venv (no pyvenv.cfg) and is not sandbox: $absPath")
            return false
        }

        // 5. Check if it's within project for extra safety
        val isInsideProject = projectPath != null && absPath.startsWith(projectPath)
        if (!isInsideProject) {
            // If outside project, we should be even more careful.
            val userHome = System.getProperty("user.home")?.let { Path.of(it).toAbsolutePath() }
            if (userHome != null) {
                if (absPath == userHome) return false
                val commonDirs = listOf("Documents", "Desktop", "Downloads", "Music", "Pictures", "Videos")
                if (commonDirs.any { absPath == userHome.resolve(it) }) return false
            }
        }

        return true
    }

    private fun isSystemPath(path: Path): Boolean {
        val pathStr = path.toAbsolutePath().toString().lowercase()
        val systemPrefixes = if (BlenderHelper.isWindows()) {
            listOf("c:\\windows", "c:\\program files", "c:\\program files (x86)")
        } else {
            listOf("/usr", "/bin", "/sbin", "/etc", "/lib", "/var", "/root", "/proc", "/sys", "/dev")
        }
        return systemPrefixes.any { pathStr.startsWith(it) }
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
        val venvRoot = getVenvRootFromExecutable(path)

        // Safety check: is it a system path?
        if (isSystemPath(venvRoot)) return false

        // Check for pyvenv.cfg
        if (venvRoot.resolve(BlenderProjectPaths.PYVENV_CFG_NAME).exists()) return true

        return homePath.contains(BlenderProjectPaths.VENV_NAME) || homePath.contains("venv") || homePath.contains("site-packages")
    }

    fun isPythonSdk(sdk: Sdk): Boolean {
        return isPythonSdkTypeName(sdk.sdkType.name) || sdk.sdkType.javaClass.simpleName.contains("Python", ignoreCase = true)
    }

    private fun isPythonSdkTypeName(name: String): Boolean {
        return name == "Python SDK" || name == "Python"
    }

    private fun findPythonSdkType(): SdkType? {
        val candidates = SdkType.EP_NAME.extensionList.distinctBy { "${it.javaClass.name}:${it.name}" }

        return candidates.firstOrNull { isPythonSdkTypeName(it.name) }
            ?: candidates.firstOrNull {
                it.name.contains("Python", ignoreCase = true) ||
                    it.javaClass.simpleName.contains("Python", ignoreCase = true)
            }
    }

    companion object {
        fun getInstance(project: Project): PythonSdkService = project.getService(PythonSdkService::class.java)
    }
}
