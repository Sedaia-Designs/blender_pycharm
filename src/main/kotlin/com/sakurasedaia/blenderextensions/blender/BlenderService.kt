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
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.python.PythonUtil
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
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

            val version = PythonUtil.getBlenderVersion(blenderExePath)

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
                        // First ensure the interpreter is set up (and venv is created)
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                            PythonService.getInstance(project).setupPythonInterpreter(blenderExePath)
                        }
                        
                        // Then install the linter module
                        PythonService.getInstance(project).installFakeBpyModule(path, version)
                        
                        // Finally update the interpreter roots again to include any new paths
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            PythonService.getInstance(project).setupPythonInterpreter(blenderExePath)
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
