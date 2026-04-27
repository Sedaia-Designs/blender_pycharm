package com.sakurasedaia.blenderextensions.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.blender.model.DownloadProgress
import com.sakurasedaia.blenderextensions.blender.model.ProgressType
import com.sakurasedaia.blenderextensions.blender.services.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.services.BlenderFinder
import com.sakurasedaia.blenderextensions.blender.utils.toBlenderHandler
import com.sakurasedaia.blenderextensions.common.utils.BlenderTaskManager
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class PythonLinterService(private val project: Project) {

    fun setupLinter(versionOrPath: String) {
        val version = if (versionOrPath.contains("/") || versionOrPath.contains("\\")) {
            BlenderFinder.tryGetVersion(versionOrPath)
        } else {
            versionOrPath
        }

        if (version == LangManager.message("blender.version.unknown")) {
            BlenderNotification(project).sendError(
                LangManager.message("toolwindow.managed.button.setup.linter"),
                LangManager.message("blender.version.unknown")
            )
            return
        }

        if (!promptForLinterPermission()) return

        BlenderTaskManager.getInstance().run(project, LangManager.message("action.setup.linter.task", version)) { indicator ->
            try {
                val pythonVersion = BlenderVersions.getSupportedVersions().find { it.majorMinor == version }?.pythonVersion
                if (PythonSdkService.getInstance(project).ensureVirtualEnvironment(pythonVersion) == null) {
                    BlenderNotification(project).sendError(
                        LangManager.message("toolwindow.managed.button.setup.linter"),
                        LangManager.message("log.python.venv.failed")
                    )
                    return@run
                }

                val result = installFakeBpyModule(version, indicator)
                if (!result.success) {
                    BlenderNotification(project).sendError(
                        LangManager.message("toolwindow.managed.button.setup.linter"),
                        result.errorMessage ?: LangManager.message("toolwindow.managed.button.setup.linter.error.pip")
                    )
                    return@run
                }

                ApplicationManager.getApplication().invokeLater {
                    PythonUtil.addLinterToCurrentSdk(project, version)
                    BlenderNotification(project).sendInfo(
                        LangManager.message("toolwindow.managed.button.setup.linter"),
                        LangManager.message("toolwindow.managed.button.setup.linter.success", version)
                    )
                }
            } catch (e: Exception) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.managed.button.setup.linter"),
                    LangManager.message("toolwindow.managed.button.setup.linter.error", e.message ?: "Unknown error")
                )
            }
        }
    }

    private fun promptForLinterPermission(): Boolean {
        var result = Messages.NO
        val message = LangManager.message("dialog.permission.python.sdk.message")
        val title = LangManager.message("dialog.permission.python.sdk.title")
        
        if (ApplicationManager.getApplication().isDispatchThread) {
            result = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
        } else {
            ApplicationManager.getApplication().invokeAndWait {
                result = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
            }
        }
        return result == Messages.YES
    }

    data class InstallResult(val success: Boolean, val errorMessage: String? = null)

    fun installFakeBpyModule(version: String, indicator: ProgressIndicator? = null): InstallResult {
        val logger = BlenderLogger.getInstance(project)
        val downloader = BlenderDownloader.getInstance(project)
        val lintDir = PythonUtil.getLintDirectory(version, project)
        val statusText = LangManager.message("log.blender.installing.linter.progress", version)
        indicator?.toBlenderHandler(downloader, version, statusText, ProgressType.LINTER)
        
        val bpyVersion = if (version == BlenderVersions.getSupportedVersions().last().majorMinor) "latest" else version
        
        try {
            if (!lintDir.exists()) {
                Files.createDirectories(lintDir)
            }
            
            val bpyPythonVersion = BlenderVersions.getSupportedVersions().find { it.majorMinor == version }?.pythonVersion
            val venvSdk = PythonSdkService.getInstance(project).ensureVirtualEnvironment(bpyPythonVersion)
            if (venvSdk?.homePath == null) {
                val error = LangManager.message("log.python.venv.failed")
                logger.log(error)
                return InstallResult(false, error)
            }

            val venvDir = project.basePath?.let { Path.of(it).resolve(".venv") }
            val uvExe = UvUtil.findUvExecutable(project)
            if (uvExe == null) {
                val error = LangManager.message("log.python.uv.not.found")
                logger.log(error)
                return InstallResult(false, error)
            }

            val installSuccess = if (venvDir != null && venvDir.exists()) {
                UvUtil.installPackages(uvExe, venvDir, listOf("fake-bpy-module-$bpyVersion"), lintDir, project)
            } else {
                false
            }

            if (installSuccess) {
                logger.log(LangManager.message("log.python.linter.install.success", version, lintDir.toString()))
                return InstallResult(true)
            } else {
                val error = LangManager.message("log.python.linter.install.failed", version, lintDir.toString(), "Check logs for details")
                logger.log(error)
                return InstallResult(false, error)
            }
        } catch (e: Exception) {
            val error = LangManager.message("log.python.linter.install.error", e.message ?: "Unknown error")
            logger.log(error)
            return InstallResult(false, error)
        } finally {
            downloader.updateProgress(DownloadProgress.None)
        }
    }

    companion object {
        fun getInstance(project: Project): PythonLinterService = project.getService(PythonLinterService::class.java)
    }
}
