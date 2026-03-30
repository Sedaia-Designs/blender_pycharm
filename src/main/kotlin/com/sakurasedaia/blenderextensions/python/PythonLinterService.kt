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
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderLogger
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.system.ExternalProcessUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class PythonLinterService(private val project: Project) {

    fun setupLinter(blenderExePath: String) {
        try {
            val path = Path.of(blenderExePath)
            if (!path.exists()) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.managed.button.setup.linter"),
                    LangManager.message("toolwindow.managed.button.setup.linter.error", "Blender executable not found: $blenderExePath")
                )
                return
            }

            val version = PythonUtil.getBlenderVersion(blenderExePath)
            if (version == "unknown") {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.managed.button.setup.linter"),
                    LangManager.message("toolwindow.managed.button.setup.linter.error.version", blenderExePath)
                )
                return
            }

            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Installing linter for Blender $version", true) {
                    override fun run(indicator: ProgressIndicator) {
                        val linterInstalled = installFakeBpyModule(version)
                        if (!linterInstalled) {
                            BlenderNotification(project).sendError(
                                LangManager.message("toolwindow.managed.button.setup.linter"),
                                LangManager.message("toolwindow.managed.button.setup.linter.error.pip")
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
        val sdk = ProjectRootManager.getInstance(project).projectSdk ?: return
        val lintDir = PythonUtil.getLintDirectory(blenderVersion, project)
        if (!lintDir.exists()) return

        ApplicationManager.getApplication().runWriteAction {
            val sdkModificator = sdk.sdkModificator
            val vFile = VirtualFileManager.getInstance().findFileByNioPath(lintDir)
            if (vFile != null) {
                // Check if already present
                val currentRoots = sdkModificator.getRoots(OrderRootType.CLASSES)
                if (currentRoots.none { it.path == vFile.path }) {
                    sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                    sdkModificator.commitChanges()
                }
            }
        }
    }

    fun getLinterSDKRoots(blenderExePath: String): List<Path> {
        val roots = mutableListOf<Path>()
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
            
            // Find a system Python to run pip
            val pythonToUse = PythonUtil.findSystemPython("3") // Try to find any Python 3
                ?: PythonUtil.findSystemPython("") // Fallback to any python
            
            if (pythonToUse == null) {
                logger.log("Cannot install linter: No system Python found to run pip.")
                return false
            }

            println("[DEBUG_LOG] installFakeBpyModule: Installing fake-bpy-module-$version into $lintDir using $pythonToUse")
            val command = GeneralCommandLine(
                pythonToUse.toString(), "-m", "pip", "install", 
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
