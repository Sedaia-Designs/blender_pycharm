package com.sakurasedaia.blenderextensions.python

import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderScanner
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

object PythonUtil {
    private val pythonVersionRegex = Regex("Python (\\d+\\.\\d+)")

    fun getBlenderVersion(blenderExePath: String): String {
        val path = Path.of(blenderExePath)
        return if (blenderExePath.contains("blender_downloads")) {
            path.parent.name
        } else {
            BlenderScanner.tryGetVersion(blenderExePath).takeIf { it != LangManager.message("blender.version.unknown") } ?: "unknown"
        }
    }

    fun getLintDirectory(version: String): Path {
        return Path.of(PathManager.getSystemPath(), "blender_downloads", "lint", version)
    }

    fun findPythonExecutable(blenderExePath: Path): Path? {
        val blenderDir = getBlenderInternalDir(blenderExePath) ?: return null
        if (!blenderDir.exists()) return null

        try {
            Files.list(blenderDir).use { stream ->
                val versionDir = stream.filter { 
                    it.name.all { c -> c.isDigit() || c == '.' } && Files.isDirectory(it)
                }.findFirst().orElse(null) ?: return null

                val pythonBinDir = versionDir.resolve("python").resolve("bin")
                val pythonExe = if (SystemInfo.isWindows) {
                    pythonBinDir.resolve("python.exe")
                } else {
                    pythonBinDir.resolve("python3")
                }
                
                if (pythonExe.exists()) return pythonExe
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    fun getPythonLibraryPaths(pythonExePath: Path): List<Path> {
        val pythonBinDir = pythonExePath.parent
        val pythonDir = pythonBinDir.parent // .../python/
        val libPaths = mutableListOf<Path>()
        
        if (SystemInfo.isWindows) {
            libPaths.add(pythonDir.resolve("Lib"))
            libPaths.add(pythonDir.resolve("Lib").resolve("site-packages"))
        } else {
            val libDir = pythonDir.resolve("lib")
            if (libDir.exists()) {
                try {
                    Files.list(libDir).use { stream ->
                        stream.filter { it.name.startsWith("python") && Files.isDirectory(it) }
                            .forEach { pyLib ->
                                libPaths.add(pyLib)
                                libPaths.add(pyLib.resolve("site-packages"))
                            }
                    }
                } catch (_: Exception) {}
            }
        }
        return libPaths.filter { it.exists() }
    }

    fun getPythonVersion(pythonExe: Path): String? {
        if (!pythonExe.exists()) return null
        try {
            val commandLine = com.intellij.execution.configurations.GeneralCommandLine(pythonExe.toString(), "--version")
            val output = com.intellij.execution.util.ExecUtil.execAndGetOutput(commandLine)
            if (output.exitCode != 0) return null
            val match = pythonVersionRegex.find(output.stdout) ?: pythonVersionRegex.find(output.stderr)
            return match?.groupValues?.get(1)
        } catch (e: Exception) {
            return null
        }
    }

    private fun getBlenderInternalDir(blenderExePath: Path): Path? {
        return if (SystemInfo.isMac) {
            blenderExePath.parent?.parent?.resolve("Resources")
        } else {
            blenderExePath.parent
        }
    }
}
