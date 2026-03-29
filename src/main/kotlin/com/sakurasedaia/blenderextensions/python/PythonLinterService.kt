package com.sakurasedaia.blenderextensions.python

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderLogger
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.system.ExternalProcessUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class PythonLinterService(private val project: Project) {

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
                    override fun run(indicator: ProgressIndicator) {
                        var interpreterConfigured = false
                        ApplicationManager.getApplication().invokeAndWait {
                            interpreterConfigured = PythonInterpreterService.getInstance(project).setupPythonInterpreter(blenderExePath)
                        }
                        if (!interpreterConfigured) return

                        val linterInstalled = installFakeBpyModule(version)
                        if (!linterInstalled) {
                            BlenderNotification(project).sendError(
                                LangManager.message("toolwindow.managed.button.setup.linter"),
                                LangManager.message("toolwindow.setup.interpreter.error", "Failed to install fake-bpy-module")
                            )
                            return
                        }

                        ApplicationManager.getApplication().invokeLater {
                            PythonInterpreterService.getInstance(project).setupPythonInterpreter(blenderExePath)
                            BlenderNotification(project).sendInfo(
                                LangManager.message("toolwindow.managed.button.setup.linter"),
                                "Successfully installed fake-bpy-module."
                            )
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

    fun getLinterSDKRoots(blenderExePath: String): List<Path> {
        val roots = mutableListOf<Path>()
        val venvPath = Path.of(project.basePath ?: return emptyList(), ".venv")
        val venvSitePackages = if (SystemInfo.isWindows) {
            listOf(venvPath.resolve("Lib").resolve("site-packages"))
        } else {
            val libDir = venvPath.resolve("lib")
            if (!libDir.exists()) emptyList() else {
                try {
                    Files.list(libDir).use { stream ->
                        stream.filter { Files.isDirectory(it) && it.fileName.toString().startsWith("python") }
                            .map { it.resolve("site-packages") }
                            .filter { it.exists() }
                            .toList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        roots.addAll(venvSitePackages)

        val version = PythonUtil.getBlenderVersion(blenderExePath)
        if (version != "unknown") {
            val lintDir = PythonUtil.getLintDirectory(version, project)
            if (lintDir.exists()) {
                roots.add(lintDir)
            }
        }

        return roots.distinct()
    }

    fun installFakeBpyModule(@Suppress("UNUSED_PARAMETER") blenderExePath: Path, version: String) {
        installFakeBpyModule(version)
    }

    fun installFakeBpyModule(version: String): Boolean {
        val logger = BlenderLogger.getInstance(project)
        val downloader = BlenderDownloader.getInstance(project)
        val lintDir = PythonUtil.getLintDirectory(version, project)
        
        val statusText = LangManager.message("log.blender.installing.linter.progress", version)
        downloader.updateProgress(BlenderDownloader.DownloadProgress(true, -1.0, statusText, version, BlenderDownloader.ProgressType.LINTER))

        try {
            // Ensure linter directory exists
            if (!lintDir.exists()) {
                Files.createDirectories(lintDir)
            }
            
            // Get the Python interpreter from the venv
            val venvPath = Path.of(project.basePath ?: return false, ".venv")
            val venvPython = if (SystemInfo.isWindows) venvPath.resolve("Scripts").resolve("python.exe") else venvPath.resolve("bin").resolve("python")
            
            if (!venvPython.exists()) {
                logger.log("Cannot install linter: venv python not found at $venvPython")
                return false
            }

            println("[DEBUG_LOG] installFakeBpyModule: Installing fake-bpy-module-$version into $lintDir using $venvPython")
            val command = GeneralCommandLine(
                venvPython.toString(), "-m", "pip", "install", 
                "fake-bpy-module-$version", "--target", lintDir.toString()
            )
            
            val output = ExternalProcessUtil.execAndGetOutput(command)
            if (output.exitCode == 0) {
                logger.log("Successfully installed fake-bpy-module-$version into $lintDir.")
                return true
            } else {
                logger.log("Failed to install fake-bpy-module-$version into $lintDir: ${output.stderr}")
                return false
            }
        } catch (e: Exception) {
            logger.log("Failed to install fake-bpy-module for linting: ${e.message}")
            return false
        } finally {
            downloader.updateProgress(BlenderDownloader.DownloadProgress.None)
        }
    }

    companion object {
        fun getInstance(project: Project): PythonLinterService = project.getService(PythonLinterService::class.java)
    }
}
