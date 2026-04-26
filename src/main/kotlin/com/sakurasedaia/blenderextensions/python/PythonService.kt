package com.sakurasedaia.blenderextensions.python
 
import com.sakurasedaia.blenderextensions.common.utils.ExternalProcessUtil
import com.sakurasedaia.blenderextensions.common.utils.FileUtil
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.GeneralCommandLine
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Service for managing Python-related functionality in Blender.
 * 
 * The Python support module follows this progression:
 * 1. [PythonService] acts as a high-level API for Python tasks.
 * 2. [PythonLinterService] handles the installation of `fake-bpy-module` for IDE intellisense.
 * 3. [PythonFinder] (if applicable) and [getBlenderPythonInfo] detect the Python environment within Blender.
 * 4. [ExternalProcessUtil] is used to execute Python scripts within the Blender process.
 */
@Service(Service.Level.PROJECT)
class PythonService(private val project: Project) {
    
    fun installFakeBpyModule(version: String, indicator: com.intellij.openapi.progress.ProgressIndicator? = null) =
        PythonLinterService.getInstance(project).installFakeBpyModule(version, indicator)
    
    fun setupLinter(versionOrPath: String) =
        PythonLinterService.getInstance(project).setupLinter(versionOrPath)

    fun getBlenderPythonInfo(blenderPath: String): Pair<String, Boolean> {
        return try {
            val path = Paths.get(blenderPath)
            FileUtil.makeExecutable(path)
            
            val script = "import sys; import importlib.util; has_fake = importlib.util.find_spec('bpy') is not None; print(f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}|{has_fake}')"
            val commandLine = GeneralCommandLine(blenderPath, "--background", "--python-expr", script)
            val output = try {
                ExternalProcessUtil.execAndGetOutput(commandLine)
            } catch (e: Exception) {
                val restriction = FileUtil.getExecutionRestrictionMessage(path)
                if (restriction != null) {
                    return Pair(restriction, false)
                }
                throw e
            }
            if (output.exitCode == 0) {
                val lastLine = output.stdoutLines.lastOrNull { it.contains("|") }
                if (lastLine != null) {
                    val parts = lastLine.split("|")
                    return Pair(parts[0], parts[1].toBoolean())
                }
            }
            Pair("Unknown", false)
        } catch (e: Exception) {
            Pair(LangManager.message("blender.status.error") + ": ${e.message}", false)
        }
    }

    companion object {
        fun getInstance(project: Project): PythonService = project.getService(PythonService::class.java)
    }

    fun ensureDebugpyInstalled(blenderPath: String, indicator: com.intellij.openapi.progress.ProgressIndicator? = null): Boolean {
        val script = "import importlib.util; print(importlib.util.find_spec('debugpy') is not None)"
        val checkCommand = GeneralCommandLine(blenderPath, "--background", "--python-expr", script)
        val checkOutput = ExternalProcessUtil.execAndGetOutput(checkCommand)
        
        if (checkOutput.exitCode == 0 && checkOutput.stdout.trim().endsWith("True")) {
            // Check version if possible, but for now just assume true if present
            return true
        }

        indicator?.text = "Installing debugpy into Blender..."
        // Request version 1.8.0 or newer for PyCharm 2025 compatibility
        val installScript = "import subprocess; import sys; subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'debugpy>=1.8.0'])"
        val installCommand = GeneralCommandLine(blenderPath, "--background", "--python-expr", installScript)
        val installExitCode = ExternalProcessUtil.executeCommand(installCommand)
        
        return installExitCode == 0
    }
}
