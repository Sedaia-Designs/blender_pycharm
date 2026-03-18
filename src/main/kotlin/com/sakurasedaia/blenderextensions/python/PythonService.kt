package com.sakurasedaia.blenderextensions.python

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderLogger
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class PythonService(private val project: Project) {

    fun getLinterSDKRoots(blenderExePath: String): List<Path> {
        val version = PythonUtil.getBlenderVersion(blenderExePath)
        if (version == "unknown") return emptyList()

        val lintDir = PythonUtil.getLintDirectory(version)
        if (!lintDir.exists()) {
            installFakeBpyModule(Path.of(blenderExePath), version)
        }
        
        return if (lintDir.exists()) listOf(lintDir) else emptyList()
    }

    fun installFakeBpyModule(blenderExePath: Path, version: String) {
        val logger = BlenderLogger.getInstance(project)
        val lintDir = PythonUtil.getLintDirectory(version)
        val downloader = BlenderDownloader.getInstance(project)
        
        val statusText = LangManager.message("log.blender.installing.linter.progress", version)
        downloader.updateProgress(BlenderDownloader.DownloadProgress(true, -1.0, statusText, version, BlenderDownloader.ProgressType.LINTER))

        try {
            val bundledPythonExe = PythonUtil.findPythonExecutable(blenderExePath) ?: return
            if (!lintDir.exists()) {
                java.nio.file.Files.createDirectories(lintDir)
            }

            val commandLine = GeneralCommandLine(
                bundledPythonExe.toString(),
                "-m", "pip", "install",
                "fake-bpy-module-$version",
                "--target", lintDir.toString()
            )
            val output = ExecUtil.execAndGetOutput(commandLine)
            if (output.exitCode == 0) {
                logger.log("Successfully installed fake-bpy-module-$version for linting.")
            } else {
                logger.log("Failed to install fake-bpy-module: ${output.stderr}")
            }
        } catch (e: Exception) {
            logger.log("Failed to install fake-bpy-module for linting: ${e.message}")
        } finally {
            downloader.updateProgress(BlenderDownloader.DownloadProgress.None)
        }
    }

    fun setupPythonInterpreter(blenderExePath: String) {
        // Placeholder for refactor commit
    }

    fun getBlenderPythonInfo(blenderPath: String): Pair<String, Boolean> {
        return try {
            val script = "import sys; import importlib.util; has_fake = importlib.util.find_spec('bpy') is not null; print(f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}|{has_fake}')"
            val commandLine = GeneralCommandLine(blenderPath, "--background", "--python-expr", script)
            val output = ExecUtil.execAndGetOutput(commandLine)
            if (output.exitCode == 0) {
                val lastLine = output.stdoutLines.lastOrNull { it.contains("|") }
                if (lastLine != null) {
                    val parts = lastLine.split("|")
                    return Pair(parts[0], parts[1].toBoolean())
                }
            }
            Pair("Unknown", false)
        } catch (e: Exception) {
            Pair("Error: ${e.message}", false)
        }
    }

    companion object {
        fun getInstance(project: Project): PythonService = project.getService(PythonService::class.java)
    }
}
