package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.common.utils.BlenderTaskManager
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.utils.BlenderScriptGenerator

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
    private val startLock = ReentrantLock()

    init {
        BlenderTaskManager.getInstance().execute {
            telemetryService.collectAndLogTelemetry()
        }
    }

    fun isRunning(): Boolean = isRunning.get()
    fun hasError(): Boolean = hasError.get()
    private var currentExtensionName: String? = null

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
        BlenderTaskManager.getInstance().execute {
            val customPaths = BlenderSettings.getInstance(project).getCustomBlenderPaths()
            BlenderScanner.scanSystemInstallations(project = project, force = true, customPaths = customPaths)
        }
    }

    fun startBlenderProcess(
        blenderPath: String,
        addonSourceDir: String? = null,
        addonSymlinkName: String? = null,
        additionalArgs: String? = null,
        isSandboxed: Boolean = false,
        importUserConfig: Boolean = false,
        blenderVersion: String? = null,
        runOptions: com.sakurasedaia.blenderextensions.run.BlenderRunConfigurationOptions? = null,
        indicator: com.intellij.openapi.progress.ProgressIndicator? = null,
        isDebugMode: Boolean = false
    ): OSProcessHandler? {
        startLock.lock()
        try {
            if (isRunning.get()) return processHandler
            hasError.set(false)
            var startupScript: Path? = null

            val projectPath = project.basePath ?: return null
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
            logger.log("Starting Blender for extension development: $symlinkName (Path: $blenderPath, Version: ${blenderVersion ?: "Unknown"}, Debug: $isDebugMode)")
            logger.log("Linking extension source: ${sourcePath.absolutePathString()} -> $symlinkName (Sandboxed: $isSandboxed)")

            linker.linkExtensionSource(addonSourceDir, addonSymlinkName, isSandboxed)
            val repoDir = linker.getExtensionsRepoDir(isSandboxed)

            val port = communicationService.startServer()
            startupScript = scriptGenerator.createStartupScript(port, repoDir, currentExtensionName, isDebugMode)

            ApplicationManager.getApplication().executeOnPooledThread {
                telemetryService.collectAndLogTelemetry(
                    context = "Blender Process Start",
                    options = runOptions,
                    blenderPath = blenderPath,
                    blenderVersion = blenderVersion
                )
            }
            val handler = launcher.startBlenderProcess(
                blenderPath = blenderPath,
                scriptPath = startupScript,
                additionalArgs = additionalArgs,
                isSandboxed = isSandboxed,
                importUserConfig = importUserConfig,
                blenderVersion = blenderVersion,
                indicator = indicator,
                isDebugMode = isDebugMode
            )

            if (handler == null) {
                hasError.set(true)
                communicationService.stopServer()
                scriptGenerator.cleanupStartupScript(startupScript)

                BlenderNotification(project).sendError(
                    LangManager.message("notification.failed.start.blender.title"),
                    LangManager.message("notification.failed.start.blender.message")
                )

                return null
            }

            isRunning.set(true)
            processHandler = handler
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    isRunning.set(false)
                    communicationService.stopServer()
                    scriptGenerator.cleanupStartupScript(startupScript)
                }
            })

            handler.startNotify()
            return handler
        } finally {
            startLock.unlock()
        }
    }

    fun reloadExtension() {
        val extensionName = currentExtensionName ?: "unknown"
        BlenderTaskManager.getInstance().execute {
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
    }

    companion object {
        fun getInstance(project: Project): BlenderService = project.getService(BlenderService::class.java)
    }
}
