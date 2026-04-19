package com.sakurasedaia.blenderextensions.python

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import com.sakurasedaia.blenderextensions.blender.BlenderVersions
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.system.ExternalProcessUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class PythonLinterService(private val project: Project) {

    fun setupLinter(version: String) {
        try {
            // Guardrail: Ensure project has a Virtual Environment SDK
            if (ensureVirtualEnvironment() == null) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.managed.button.setup.linter"),
                    LangManager.message("log.python.venv.failed")
                )
                return
            }

            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Installing linter for Blender $version", true) {
                    override fun run(indicator: ProgressIndicator) {
                        val result = installFakeBpyModule(version)
                        if (!result.success) {
                            BlenderNotification(project).sendError(
                                LangManager.message("toolwindow.managed.button.setup.linter"),
                                result.errorMessage ?: LangManager.message("toolwindow.managed.button.setup.linter.error.pip")
                            )
                            return
                        }

                        ApplicationManager.getApplication().invokeLater {
                            addLinterToCurrentSdk(version)
                            BlenderNotification(project).sendInfo(
                                LangManager.message("toolwindow.managed.button.setup.linter"),
                                LangManager.message("toolwindow.managed.button.setup.linter.success", version)
                            )
                        }
                    }
                }
            )
        } catch (e: Exception) {
            BlenderNotification(project).sendError(
                LangManager.message("toolwindow.managed.button.setup.linter"),
                LangManager.message("toolwindow.managed.button.setup.linter.error", e.message ?: "Unknown error")
            )
        }
    }

    private fun addLinterToCurrentSdk(blenderVersion: String) {
        println("[DEBUG_LOG] Adding linter to current SDK")
        println("[DEBUG_LOG] Blender version: $blenderVersion")
        val sdk = ProjectRootManager.getInstance(project).projectSdk ?: return
        val lintDir = PythonUtil.getLintDirectory(blenderVersion, project)
        if (!lintDir.exists()) return

        ApplicationManager.getApplication().runWriteAction {
            val sdkModificator = sdk.sdkModificator
            val vFile = VirtualFileManager.getInstance().findFileByNioPath(lintDir)
            if (vFile != null) {
                println("[DEBUG_LOG] Linter directory found: ${vFile.path}")
                // Check if already present
                val currentRoots = sdkModificator.getRoots(OrderRootType.CLASSES)
                if (currentRoots.none { it.path == vFile.path }) {
                    sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                    sdkModificator.commitChanges()
                }
            }
        }
    }

    data class InstallResult(val success: Boolean, val errorMessage: String? = null)

    fun installFakeBpyModule(version: String): InstallResult {
        val logger = BlenderLogger.getInstance(project)
        val downloader = BlenderDownloader.getInstance(project)
        val lintDir = PythonUtil.getLintDirectory(version, project)
        val statusText = LangManager.message("log.blender.installing.linter.progress", version)
        downloader.updateProgress(BlenderDownloader.DownloadProgress(true, -1.0, statusText, version, BlenderDownloader.ProgressType.LINTER))
        
        var bpyVersion: String
        val latest: String = BlenderVersions.getSupportedVersions().last().majorMinor
        println("[DEBUG_LOG] Latest Blender version: $latest")
        println("[DEBUG_LOG] Requested Blender version: $version")
        
        if (version == latest) {
            bpyVersion = "latest"
        } else {
            bpyVersion = version
        }
        println("[DEBUG_LOG] Using Blender version: $bpyVersion")
        
        try {
            // Ensure linter directory exists
            if (!lintDir.exists()) {
                Files.createDirectories(lintDir)
            }
            
            // Guardrail: Ensure project has a Virtual Environment SDK
            val venvSdk = ensureVirtualEnvironment()
            if (venvSdk == null) {
                val error = LangManager.message("log.python.venv.failed")
                logger.log(error)
                return InstallResult(false, error)
            }

            val pythonToUse = venvSdk.homePath?.let { Path.of(it) }
            if (pythonToUse == null) {
                val error = LangManager.message("log.python.sdk.no.home")
                logger.log(error)
                return InstallResult(false, error)
            }

            // Ensure pip is available in the venv
            val ensurePipCommand = GeneralCommandLine(pythonToUse.toString(), "-m", "ensurepip", "--upgrade")
            ExternalProcessUtil.execAndGetOutput(ensurePipCommand)
            
            val command = GeneralCommandLine(
                pythonToUse.toString(), "-m",
                "pip", "install", "fake-bpy-module-$bpyVersion",
                "--target", lintDir.toString()
            )
            println("[DEBUG_LOG] Installing linter for Blender $bpyVersion: ${command.commandLineString}")
            
            
            val output = ExternalProcessUtil.execAndGetOutput(command)
            if (output.exitCode == 0) {
                logger.log(LangManager.message("log.python.linter.install.success", version, lintDir.toString()))
                return InstallResult(true)
            } else {
                val error = LangManager.message("log.python.linter.install.failed", version, lintDir.toString(), output.stderr)
                logger.log(error)
                println(error)
                return InstallResult(false, error)
            }
        } catch (e: Exception) {
            val error = LangManager.message("log.python.linter.install.error", e.message ?: "Unknown error")
            logger.log(error)
            println(error)
            return InstallResult(false, error)
        } finally {
            downloader.updateProgress(BlenderDownloader.DownloadProgress.None)
        }
    }

    fun ensureVirtualEnvironment(): Sdk? {
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk
        if (projectSdk != null && isPythonSdk(projectSdk) && isVenv(projectSdk)) {
            return projectSdk
        }

        val projectRoot = project.basePath?.let { Path.of(it) } ?: return null
        val venvDir = projectRoot.resolve(".venv")
        
        if (!venvDir.exists()) {
            val latestPython = PythonUtil.findSystemPython("3") ?: PythonUtil.findSystemPython("") ?: return null
            val createVenvCommand = GeneralCommandLine(latestPython.toString(), "-m", "venv", venvDir.toString())
            val output = ExternalProcessUtil.execAndGetOutput(createVenvCommand)
            if (output.exitCode != 0) {
                BlenderLogger.getInstance(project).log(LangManager.message("log.python.venv.create.failed", output.stderr))
                return null
            }
        }

        val pythonExe = if (SystemInfo.isWindows) venvDir.resolve("Scripts").resolve("python.exe") else venvDir.resolve("bin").resolve("python")
        if (!pythonExe.exists()) {
            BlenderLogger.getInstance(project).log(LangManager.message("log.python.venv.not.found", pythonExe.toString()))
            return null
        }

        // Create or find existing SDK in the IDE
        val sdkType = SdkType.getAllTypes().find { isPythonSdkTypeName(it.name) } ?: return null
        val sdkName = "Python (BlenderExtensions .venv)"
        
        var sdk = ProjectJdkTable.getInstance().allJdks.find { it.homePath == pythonExe.toString() }
        if (sdk == null) {
            sdk = ProjectJdkTable.getInstance().createSdk(sdkName, sdkType)
            val modificator = sdk.sdkModificator
            modificator.homePath = pythonExe.toString()
            val version = PythonUtil.getPythonVersion(pythonExe)
            modificator.versionString = version?.let { if (it.startsWith("Python")) it else "Python $it" }
            ApplicationManager.getApplication().runWriteAction {
                modificator.commitChanges()
                ProjectJdkTable.getInstance().addJdk(sdk)
            }
        }

        // Set as project SDK
        ApplicationManager.getApplication().runWriteAction {
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
        
        return sdk
    }

    private fun isVenv(sdk: Sdk): Boolean {
        val homePath = sdk.homePath ?: return false
        val path = Path.of(homePath)
        return path.parent?.fileName?.toString() == "Scripts" || path.parent?.fileName?.toString() == "bin" || homePath.contains(".venv") || homePath.contains("venv") || homePath.contains("site-packages")
    }

    private fun isPythonSdk(sdk: Sdk): Boolean {
        return isPythonSdkTypeName(sdk.sdkType.name) || sdk.sdkType.javaClass.simpleName.contains("Python", ignoreCase = true)
    }

    private fun isPythonSdkTypeName(name: String): Boolean {
        return name == "Python SDK" || name == "Python"
    }

    companion object {
        fun getInstance(project: Project): PythonLinterService = project.getService(PythonLinterService::class.java)
    }
}
