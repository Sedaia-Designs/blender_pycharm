package com.sakurasedaia.blenderextensions.blender

import com.sakurasedaia.blenderextensions.LangManager
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class BlenderService(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)
    private val downloader = BlenderDownloader.getInstance(project)
    private val linker = BlenderLinker.getInstance(project)
    private val launcher = BlenderLauncher.getInstance(project)
    private val scriptGenerator = BlenderScriptGenerator.getInstance()
    private val communicationService = BlenderCommunicationService.getInstance(project)
    private val telemetryService = BlenderTelemetryService.getInstance(project)

    private var processHandler: OSProcessHandler? = null
    private val isRunning = AtomicBoolean(false)
    private val hasError = AtomicBoolean(false)

    init {
        telemetryService.collectAndLogTelemetry()
    }

    fun isRunning(): Boolean = isRunning.get()
    fun hasError(): Boolean = hasError.get()
    private var currentExtensionName: String? = null

    /**
     * Main log entry point for other components (like UI).
     */
    fun log(message: String) = logger.log(message)

    fun getOrDownloadBlenderPath(version: String): String? = downloader.getOrDownloadBlenderPath(version)

    fun clearSandbox() {
        val projectPath = project.basePath ?: return
        val sandboxDir = Path.of(projectPath, ".blender-sandbox")
        if (sandboxDir.exists()) {
            com.intellij.openapi.util.io.FileUtil.delete(sandboxDir.toFile())
            logger.log(LangManager.message("log.service.cleared.sandbox", sandboxDir.toString()))
        }
    }

    fun startBlenderProcess(
        blenderPath: String,
        addonSourceDir: String? = null,
        addonSymlinkName: String? = null,
        additionalArgs: String? = null,
        isSandboxed: Boolean = false,
        blenderCommand: String? = null,
        importUserConfig: Boolean = false,
        blenderVersion: String? = null,
        runOptions: com.sakurasedaia.blenderextensions.run.BlenderRunConfigurationOptions? = null
    ): OSProcessHandler? {
        if (isRunning.get()) return processHandler
        hasError.set(false)

        val projectPath = project.basePath ?: return null

        val handler = if (!blenderCommand.isNullOrBlank()) {
            telemetryService.collectAndLogTelemetry(
                context = "Blender Process Start (Custom Command)",
                options = runOptions,
                blenderPath = blenderPath,
                blenderVersion = blenderVersion
            )
            launcher.startBlenderProcess(
                blenderPath = blenderPath,
                additionalArgs = additionalArgs,
                isSandboxed = isSandboxed,
                blenderCommand = blenderCommand,
                importUserConfig = importUserConfig,
                blenderVersion = blenderVersion
            )
        } else {
            val sourcePath = if (!addonSourceDir.isNullOrEmpty()) {
                Path.of(addonSourceDir)
            } else {
                val markedSource = BlenderSettings.getInstance(project).getSourceFolders().firstOrNull()
                if (markedSource != null) Path.of(markedSource) else Path.of(projectPath)
            }

            if (!sourcePath.exists()) {
                logger.log(LangManager.message("log.linker.source.not.found", sourcePath.toString()))
                return null
            }

            val symlinkName = if (!addonSymlinkName.isNullOrEmpty()) addonSymlinkName else sourcePath.name
            currentExtensionName = symlinkName

            linker.linkExtensionSource(addonSourceDir, addonSymlinkName, isSandboxed)
            val repoDir = linker.getExtensionsRepoDir(isSandboxed)

            val port = communicationService.startServer()
            val script = scriptGenerator.createStartupScript(port, repoDir, currentExtensionName)

            telemetryService.collectAndLogTelemetry(
                context = "Blender Process Start (Extension Development)",
                options = runOptions,
                blenderPath = blenderPath,
                blenderVersion = blenderVersion
            )
            launcher.startBlenderProcess(
                blenderPath = blenderPath,
                scriptPath = script,
                additionalArgs = additionalArgs,
                isSandboxed = isSandboxed,
                importUserConfig = importUserConfig,
                blenderVersion = blenderVersion
            )
        }

        if (handler == null) {
            hasError.set(true)
            if (blenderCommand.isNullOrBlank()) {
                communicationService.stopServer()
            }

            BlenderNotification(project).sendError(
                LangManager.message("notification.failed.start.blender.title"),
                LangManager.message("notification.failed.start.blender.message")
                )
                
            return null
        }

        processHandler = handler
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                isRunning.set(false)
                communicationService.stopServer()
            }
        })

        handler.startNotify()
        isRunning.set(true)
        return handler
    }

    fun reloadExtension() {
        val extensionName = currentExtensionName ?: "unknown"
        try {
            communicationService.sendReloadCommand(extensionName)
        } catch (e: Exception) {
            BlenderNotification(project).sendWarning(
                LangManager.message("notification.reload.failed.title"),
                LangManager.message("notification.reload.failed.message", e.message ?: "")
            )
            logger.log(LangManager.message("log.blender.failed.reload", e.message ?: ""))
        }
    }

    fun setupPythonInterpreter(blenderExePath: String, skipLinter: Boolean = false) {
        try {
            val path = Path.of(blenderExePath)
            val pythonExe = BlenderPathUtil.findPythonExecutable(path)
            if (pythonExe == null || !pythonExe.exists()) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "Python executable not found in $blenderExePath")
                )
                return
            }

            val pySdkType = try {
                val sdkTypeClass = Class.forName("com.jetbrains.python.sdk.PythonSdkType")
                com.intellij.openapi.projectRoots.SdkType.findInstance(sdkTypeClass as Class<out com.intellij.openapi.projectRoots.SdkType>)
            } catch (e: Exception) {
                com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().allJdks.find { it.sdkType.name == "Python SDK" }?.sdkType
                    ?: com.intellij.openapi.projectRoots.SdkType.getAllTypes().find { it.name == "Python SDK" }
            }

            if (pySdkType == null) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "Python plugin not found or Python SDK type unavailable")
                )
                return
            }

            val sdkName = "Blender Python (${path.parent.name})"
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
                val sdkTable = ProjectJdkTable.getInstance()
                val existingSdk = sdkTable.allJdks.find { it.name == sdkName && it.sdkType == pySdkType }
                
                val sdk = existingSdk ?: sdkTable.createSdk(sdkName, pySdkType)
                val sdkModificator = sdk.sdkModificator
                sdkModificator.homePath = pythonExe.toString()
                
                // Clear existing roots to avoid duplicates when updating
                sdkModificator.removeAllRoots()

                // Add standard library paths and Blender modules
                BlenderPathUtil.getPythonLibraryPaths(pythonExe).forEach { libPath ->
                    VirtualFileManager.getInstance().findFileByNioPath(libPath)?.let { vFile ->
                        sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                    }
                }

                // Add linting paths if available
                val version = if (blenderExePath.contains("blender_downloads")) {
                    path.parent.name
                } else {
                    BlenderScanner.tryGetVersion(blenderExePath).takeIf { it != LangManager.message("blender.version.unknown") } ?: "unknown"
                }
                
                if (version != "unknown") {
                    val downloader = BlenderDownloader.getInstance(project)
                    val lintDir = downloader.getLintDirectory(version)
                    if (lintDir.exists()) {
                        VirtualFileManager.getInstance().findFileByNioPath(lintDir)?.let { vFile ->
                            sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                        }
                    }
                }

                sdkModificator.commitChanges()

                if (existingSdk == null) {
                    sdkTable.addJdk(sdk)
                }
                ProjectRootManager.getInstance(project).projectSdk = sdk
            }

            BlenderNotification(project).sendInfo(
                LangManager.message("toolwindow.setup.interpreter"),
                LangManager.message("toolwindow.setup.interpreter.success", pythonExe.toString())
            )
            
            // Also trigger linter setup (it will install if missing and update the SDK roots)
            if (!skipLinter) {
                setupLinter(blenderExePath)
            }
        } catch (e: Exception) {
            BlenderNotification(project).sendError(
                LangManager.message("toolwindow.setup.interpreter"),
                LangManager.message("toolwindow.setup.interpreter.error", e.message ?: "Unknown error")
            )
        }
    }

    fun setupLinter(blenderExePath: String) {
        try {
            val path = Path.of(blenderExePath)
            if (!path.exists()) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "Blender executable not found: $blenderExePath")
                )
                return
            }

            val version = try {
                // Try to get a clean version string from the path if it's managed, 
                // otherwise use the scanner to get it from the executable
                if (blenderExePath.contains("blender_downloads")) {
                    path.parent.name
                } else {
                    BlenderScanner.tryGetVersion(blenderExePath).takeIf { it != LangManager.message("blender.version.unknown") } ?: "unknown"
                }
            } catch (e: Exception) {
                "unknown"
            }

            if (version == "unknown") {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "Could not determine Blender version for $blenderExePath")
                )
                return
            }

            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Installing linter for Blender $version", true) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        downloader.installFakeBpyModule(path, version)
                        
                        // After installation, re-run setupPythonInterpreter to add the new roots to the SDK
                        // This will update the project SDK with the new linting paths
                        // We skip linter setup to avoid recursion
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            setupPythonInterpreter(blenderExePath, skipLinter = true)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            BlenderNotification(project).sendError(
                LangManager.message("toolwindow.setup.interpreter"),
                LangManager.message("toolwindow.setup.interpreter.error", e.message ?: "Unknown error")
            )
        }
    }

    companion object {
        fun getInstance(project: Project): BlenderService = project.getService(BlenderService::class.java)
    }
}
