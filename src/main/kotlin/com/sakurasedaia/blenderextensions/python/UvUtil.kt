package com.sakurasedaia.blenderextensions.python

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.common.utils.ExternalProcessUtil
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Path
import kotlin.io.path.exists

object UvUtil {

    /**
     * Finds the 'uv' executable in the system PATH.
     */
    fun findUvExecutable(project: Project? = null): Path? {
        val uvName = if (BlenderHelper.isWindows()) "uv.exe" else "uv"
        val pathEnv = System.getenv("PATH")
        if (pathEnv == null) {
            BlenderLogger.debug(project, "UvUtil: PATH environment variable is null")
            return null
        }
        
        val separator = if (BlenderHelper.isWindows()) ";" else ":"
        val paths = pathEnv.split(separator).toMutableList()
        
        // Also check common local installation paths if not in PATH yet
        val home = System.getProperty("user.home")
        if (home != null) {
            val localBin = Path.of(home).resolve(".local").resolve("bin")
            if (localBin.exists() && !paths.contains(localBin.toString())) {
                paths.add(localBin.toString())
            }
            val appDataBin = Path.of(home).resolve("AppData").resolve("Roaming").resolve("uv").resolve("bin")
            if (appDataBin.exists() && !paths.contains(appDataBin.toString())) {
                paths.add(appDataBin.toString())
            }
        }

        return paths.asSequence()
            .mapNotNull { runCatching { Path.of(it) }.getOrNull() }
            .map { it.resolve(uvName) }
            .firstOrNull { it.exists() }
    }

    /**
     * Installs 'uv' using the official standalone installer scripts.
     * @return true if installation command was executed successfully.
     */
    fun installUv(project: Project? = null): Boolean {
        return try {
            val command = if (BlenderHelper.isWindows()) {
                // Windows: powershell -c "irm https://astral.sh/uv/install.ps1 | iex"
                GeneralCommandLine("powershell", "-ExecutionPolicy", "ByPass", "-c", "irm https://astral.sh/uv/install.ps1 | iex")
            } else {
                // Linux/macOS: curl -LsSf https://astral.sh/uv/install.sh | sh
                // We use a shell to handle the pipe
                GeneralCommandLine("sh", "-c", "curl -LsSf https://astral.sh/uv/install.sh | sh")
            }
            
            BlenderLogger.log(project, LangManager.message("log.python.uv.installing"))
            val output = ExternalProcessUtil.execAndGetOutput(command)
            
            if (output.exitCode == 0) {
                BlenderLogger.log(project, LangManager.message("log.python.uv.installed"))
                true
            } else {
                BlenderLogger.log(project, LangManager.message("log.python.uv.install.failed", output.stderr))
                false
            }
        } catch (e: Exception) {
            BlenderLogger.log(project, LangManager.message("log.python.uv.install.failed", e.message ?: "Unknown error"))
            false
        }
    }

    /**
     * Creates a virtual environment using 'uv'.
     * @param pythonVersion Optional python version (e.g., "3.11")
     * @return true if successful, false otherwise.
     */
    fun createVenv(uvExe: Path, venvDir: Path, pythonVersion: String? = null, project: Project? = null): Boolean {
        try {
            // Command: uv venv <venvDir> [--python <version>]
            val args = mutableListOf("venv", venvDir.toString())
            if (pythonVersion != null) {
                args.add("--python")
                args.add(pythonVersion)
            }
            
            val command = GeneralCommandLine(uvExe.toString()).withParameters(args)
            val output = ExternalProcessUtil.execAndGetOutput(command)
            
            if (output.exitCode == 0) {
                BlenderLogger.debug(project, "UvUtil: Successfully created venv at $venvDir using uv" + (if (pythonVersion != null) " (Python $pythonVersion)" else ""))
                return true
            } else {
                BlenderLogger.log(project, "UvUtil: Failed to create venv using uv: ${output.stderr}")
            }
        } catch (e: Exception) {
            BlenderLogger.log(project, "UvUtil: Error creating venv using uv: ${e.message}")
        }
        return false
    }

    /**
     * Installs packages using 'uv' pip.
     * @return true if successful, false otherwise.
     */
    fun installPackages(uvExe: Path, venvDir: Path, packages: List<String>, targetDir: Path? = null, project: Project? = null): Boolean {
        try {
            // Command: uv pip install <packages> --python <venvPython>
            val pythonExe = if (BlenderHelper.isWindows()) {
                venvDir.resolve("Scripts").resolve("python.exe")
            } else {
                venvDir.resolve("bin").resolve("python")
            }

            val args = mutableListOf("pip", "install")
            args.addAll(packages)
            args.add("--python")
            args.add(pythonExe.toString())
            
            if (targetDir != null) {
                args.add("--target")
                args.add(targetDir.toString())
            }

            val command = GeneralCommandLine(uvExe.toString()).withParameters(args)
            val output = ExternalProcessUtil.execAndGetOutput(command)

            if (output.exitCode == 0) {
                BlenderLogger.debug(project, "UvUtil: Successfully installed packages using uv pip")
                return true
            } else {
                BlenderLogger.log(project, "UvUtil: Failed to install packages using uv pip: ${output.stderr}")
            }
        } catch (e: Exception) {
            BlenderLogger.log(project, "UvUtil: Error installing packages using uv pip: ${e.message}")
        }
        return false
    }
}
