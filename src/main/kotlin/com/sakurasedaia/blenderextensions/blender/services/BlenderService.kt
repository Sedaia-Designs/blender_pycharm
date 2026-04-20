package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import com.sakurasedaia.blenderextensions.telemetry.BlenderTelemetryService
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.utils.BlenderScriptGenerator

/**
 * Central service for managing Blender instances and extension development.
 * 
 * The Blender management module follows this progression:
 * 1. [BlenderScanner] & [BlenderFinder] locate existing Blender installations on the system.
 * 2. [BlenderDownloader] handles downloading and extracting specific Blender versions if not found.
 * 3. [BlenderLinker] links the extension source code to the appropriate Blender extensions directory.
 * 4. [BlenderLauncher] starts the Blender process with the necessary arguments and startup scripts.
 * 5. [BlenderCommunicationService] establishes a connection with Blender for commands like reloading.
 */
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
        ApplicationManager.getApplication().executeOnPooledThread {
            telemetryService.collectAndLogTelemetry()
        }
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
        val sandboxDir = Path.of(projectPath, ".venv", "blender_sandbox")
        if (sandboxDir.exists()) {
            logger.log("Clearing Blender sandbox at: $sandboxDir")
            com.intellij.openapi.util.io.FileUtil.delete(sandboxDir.toFile())
            logger.log(LangManager.message("log.service.cleared.sandbox", sandboxDir.toString()))
        }
    }

    fun scanInstallations() {
        ApplicationManager.getApplication().executeOnPooledThread {
            BlenderScanner.scanSystemInstallations(project = project, force = true)
        }
    }

    /**
     * Entry point for starting a Blender process.
     * 
     * The startup process follows these steps:
     * 1. Check if Blender is already running.
     * 2. Determine if it's a custom command or a standard extension development start.
     * 3. (For extensions) Link source code to Blender's extensions repo using [BlenderLinker].
     * 4. (For extensions) Generate a startup script via [BlenderScriptGenerator] and start the communication server.
     * 5. Start the process via [BlenderLauncher].
     * 6. Attach process listeners for cleanup on termination.
     */
    fun startBlenderProcess(
        blenderPath: String,
        addonSourceDir: String? = null,
        addonSymlinkName: String? = null,
        additionalArgs: String? = null,
        isSandboxed: Boolean = false,
        blenderCommand: String? = null,
        importUserConfig: Boolean = false,
        blenderVersion: String? = null,
        runOptions: com.sakurasedaia.blenderextensions.run.BlenderRunConfigurationOptions? = null,
        indicator: com.intellij.openapi.progress.ProgressIndicator? = null
    ): OSProcessHandler? {
        if (isRunning.get()) return processHandler
        hasError.set(false)
        var startupScript: Path? = null

        val projectPath = project.basePath ?: return null

        val handler = try {
            if (!blenderCommand.isNullOrBlank()) {
                logger.log("Starting Blender with custom command: $blenderCommand (Path: $blenderPath, Version: ${blenderVersion ?: "Unknown"})")
                ApplicationManager.getApplication().executeOnPooledThread {
                    telemetryService.collectAndLogTelemetry(
                        context = "Blender Process Start (Custom Command)",
                        options = runOptions,
                        blenderPath = blenderPath,
                        blenderVersion = blenderVersion
                    )
                }
                launcher.startBlenderProcess(
                    blenderPath = blenderPath,
                    additionalArgs = additionalArgs,
                    isSandboxed = isSandboxed,
                    blenderCommand = blenderCommand,
                    importUserConfig = importUserConfig,
                    blenderVersion = blenderVersion,
                    indicator = indicator
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
                logger.log("Starting Blender for extension development: $symlinkName (Path: $blenderPath, Version: ${blenderVersion ?: "Unknown"})")
                logger.log("Linking extension source: ${sourcePath.absolutePathString()} -> $symlinkName (Sandboxed: $isSandboxed)")

                linker.linkExtensionSource(addonSourceDir, addonSymlinkName, isSandboxed)
                val repoDir = linker.getExtensionsRepoDir(isSandboxed)

                val port = communicationService.startServer()
                startupScript = scriptGenerator.createStartupScript(port, repoDir, currentExtensionName)

                ApplicationManager.getApplication().executeOnPooledThread {
                    telemetryService.collectAndLogTelemetry(
                        context = "Blender Process Start (Extension Development)",
                        options = runOptions,
                        blenderPath = blenderPath,
                        blenderVersion = blenderVersion
                    )
                }
                launcher.startBlenderProcess(
                    blenderPath = blenderPath,
                    scriptPath = startupScript,
                    additionalArgs = additionalArgs,
                    isSandboxed = isSandboxed,
                    importUserConfig = importUserConfig,
                    blenderVersion = blenderVersion,
                    indicator = indicator
                )
            }
        } catch (e: ExecutionException) {
            logger.error(LangManager.message("log.launcher.failed.start", e.message ?: ""))
            BlenderNotification(project).sendError(
                LangManager.message("notification.failed.start.blender.title"),
                e.message ?: LangManager.message("notification.failed.start.blender.message")
            )
            null
        }

        if (handler == null) {
            hasError.set(true)
            if (blenderCommand.isNullOrBlank()) {
                communicationService.stopServer()
            }
            scriptGenerator.cleanupStartupScript(startupScript)

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
                scriptGenerator.cleanupStartupScript(startupScript)
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

    fun setupLinter(blenderExePath: String) =
        PythonService.getInstance(project).setupLinter(blenderExePath)

    companion object {
        fun getInstance(project: Project): BlenderService = project.getService(BlenderService::class.java)
    }
}
