package com.sakurasedaia.blenderextensions.python

import com.intellij.execution.util.ExecUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import java.nio.file.Path
import kotlin.io.path.exists

object PythonFinder {

    fun getPythonVersion(pythonExe: Path, project: Project? = null): String? {
        if (!pythonExe.exists()) {
            BlenderLogger.log(project, "[DEBUG_LOG] getPythonVersion: Executable does not exist: $pythonExe")
            return null
        }
        try {
            val commandLine = GeneralCommandLine(pythonExe.toString(), "--version")
            val output = ExecUtil.execAndGetOutput(commandLine)
            if (output.exitCode != 0) {
                BlenderLogger.log(project, "[DEBUG_LOG] getPythonVersion: --version failed for $pythonExe (exit code: ${output.exitCode})")
                return null
            }
            
            val combinedOutput = (output.stdout + output.stderr).trim()
            if (combinedOutput.isEmpty()) return null

            val version = if (combinedOutput.startsWith("Python ", ignoreCase = true)) {
                combinedOutput.substring(7).trim().split(" ").firstOrNull()
            } else {
                combinedOutput.split(" ").firstOrNull()
            }
            
            if (version != null && version.all { it.isDigit() || it == '.' }) {
                BlenderLogger.log(project, "[DEBUG_LOG] getPythonVersion: $pythonExe version is $version")
                return version
            }
            
            BlenderLogger.log(project, "[DEBUG_LOG] getPythonVersion: $pythonExe version (extracted) is ${version ?: "unknown"}")
            return version
        } catch (e: Exception) {
            BlenderLogger.log(project, "[DEBUG_LOG] getPythonVersion: Error getting version for $pythonExe: ${e.message}")
            return null
        }
    }

    fun findSystemPython(targetVersion: String, project: Project? = null): Path? {
        BlenderLogger.log(project, "[DEBUG_LOG] findSystemPython: Searching for system Python $targetVersion")
        val executableNames = if (BlenderHelper.isWindows()) {
            listOf("python.exe")
        } else {
            listOf("python$targetVersion", "python3", "python")
        }

        val pathEnv = System.getenv("PATH")
        if (pathEnv == null) {
            BlenderLogger.log(project, "[DEBUG_LOG] findSystemPython: PATH environment variable is null")
            return null
        }
        val separator = if (BlenderHelper.isWindows()) ";" else ":"
        val pathDirs = pathEnv.split(separator).toMutableList()
        
        if (BlenderHelper.isWindows()) {
            val pyExe = pathDirs.asSequence()
                .mapNotNull { runCatching { Path.of(it) }.getOrNull() }
                .map { it.resolve("py.exe") }
                .firstOrNull { it.exists() }
            
            if (pyExe != null) {
                try {
                    val output = ExecUtil.execAndGetOutput(
                        GeneralCommandLine(pyExe.toString(), "-$targetVersion", "-c", "import sys; print(sys.executable)")
                    )
                    if (output.exitCode == 0) {
                        val pyPath = Path.of(output.stdout.trim())
                        if (pyPath.exists()) {
                            return pyPath
                        }
                    }
                } catch (e: Exception) {
                    BlenderLogger.log(project, "[DEBUG_LOG] findSystemPython: Error calling py.exe: ${e.message}")
                }
            }
        }

        for (dir in pathDirs) {
            val baseDir = runCatching { Path.of(dir) }.getOrNull() ?: continue
            for (name in executableNames) {
                val candidate = baseDir.resolve(name)
                if (!candidate.exists()) continue
                val version = getPythonVersion(candidate, project) ?: continue
                if (version == targetVersion || version.startsWith("$targetVersion.")) {
                    return candidate
                }
            }
        }

        // Search in common locations as fallback
        val commonLocations = if (BlenderHelper.isWindows()) {
            val locations = mutableListOf(
                Path.of(System.getenv("LocalAppData") ?: "", "Programs", "Python"),
                Path.of(System.getenv("ProgramFiles") ?: "", "Python")
            )
            System.getenv("SystemDrive")?.let { drive ->
                locations.add(Path.of(drive, "Python$targetVersion"))
                locations.add(Path.of(drive, "Python${targetVersion.replace(".", "")}"))
            }
            locations
        } else {
            listOf(Path.of("/usr/bin"), Path.of("/usr/local/bin"))
        }

        for (baseDir in commonLocations) {
            if (!baseDir.exists()) continue
            
            if (BlenderHelper.isWindows()) {
                try {
                    val directMatch = baseDir.resolve("python.exe")
                    if (directMatch.exists()) {
                        val version = getPythonVersion(directMatch, project)
                        if (version == targetVersion || version?.startsWith("$targetVersion.") == true) return directMatch
                    }

                    val found = java.nio.file.Files.list(baseDir).use { stream ->
                        stream.filter { (it.toString().contains("Python", ignoreCase = true) || it.toString().contains(targetVersion)) && java.nio.file.Files.isDirectory(it) }
                            .map { versionDir -> versionDir.resolve("python.exe") }
                            .filter { it.exists() }
                            .filter { candidate ->
                                val version = getPythonVersion(candidate, project)
                                version == targetVersion || version?.startsWith("$targetVersion.") == true
                            }
                            .findFirst().orElse(null)
                    }
                    if (found != null) return found
                } catch (e: Exception) {}
            } else {
                for (name in listOf("python$targetVersion", "python3", "python")) {
                    val candidate = baseDir.resolve(name)
                    if (candidate.exists()) {
                        val version = getPythonVersion(candidate, project)
                        if (version == targetVersion || version?.startsWith("$targetVersion.") == true) return candidate
                    }
                }
            }
        }

        return null
    }
}
