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

    fun getPythonInterpreterDirectory(version: String): Path {
        return Path.of(PathManager.getSystemPath(), "blender_downloads", "py_interpreter", version)
    }

    fun findSystemPython(targetVersion: String): Path? {
        val isWindows = SystemInfo.isWindows
        val searchDirs = mutableListOf<Path>()

        if (isWindows) {
            // Common Windows paths
            val programFiles = System.getenv("ProgramFiles")?.let { Path.of(it) }
            val programFilesX86 = System.getenv("ProgramFiles(x86)")?.let { Path.of(it) }
            val localAppData = System.getenv("LOCALAPPDATA")?.let { Path.of(it) }

            // Python installations are often in C:\Python3x or C:\Program Files\Python3x
            val root = Path.of("C:\\")
            if (Files.exists(root)) {
                try {
                    Files.list(root).use { stream ->
                        stream.filter { it.name.startsWith("Python", ignoreCase = true) && Files.isDirectory(it) }
                            .forEach { searchDirs.add(it) }
                    }
                } catch (_: Exception) {}
            }

            programFiles?.let { pf ->
                val pythonDir = pf.resolve("Python")
                if (pythonDir.exists()) searchDirs.add(pythonDir)
                // Also scan for Python3x directly in Program Files if any
                try {
                    Files.list(pf).use { stream ->
                        stream.filter { it.name.startsWith("Python", ignoreCase = true) && Files.isDirectory(it) }
                            .forEach { searchDirs.add(it) }
                    }
                } catch (_: Exception) {}
            }

            localAppData?.let { la ->
                val programs = la.resolve("Programs").resolve("Python")
                if (programs.exists()) {
                    try {
                        Files.list(programs).use { stream ->
                            stream.filter { Files.isDirectory(it) }.forEach { searchDirs.add(it) }
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            // Linux/Mac common paths
            searchDirs.add(Path.of("/usr/bin"))
            searchDirs.add(Path.of("/usr/local/bin"))
            searchDirs.add(Path.of("/opt"))
            if (SystemInfo.isMac) {
                searchDirs.add(Path.of("/Library/Frameworks/Python.framework/Versions"))
            }
        }

        // Search in the directories
        for (dir in searchDirs) {
            val pythonExes = if (isWindows) {
                listOf(dir.resolve("python.exe"), dir.resolve("bin").resolve("python.exe"))
            } else {
                listOf(dir.resolve("python3"), dir.resolve("python$targetVersion"), dir.resolve("bin").resolve("python3"))
            }

            for (exe in pythonExes) {
                if (exe.exists()) {
                    val version = getPythonVersion(exe)
                    if (version == targetVersion || version?.startsWith("$targetVersion.") == true) {
                        return exe
                    }
                }
            }
            
            // If the dir itself might contain multiple versions (like /usr/bin or Programs/Python)
            if (Files.isDirectory(dir)) {
                try {
                    Files.list(dir).use { stream ->
                        val matching = stream.filter { it.name.contains("python", ignoreCase = true) }
                            .map { if (isWindows) it.resolve("python.exe") else it }
                            .filter { it.exists() && getPythonVersion(it)?.startsWith("$targetVersion.") == true }
                            .findFirst().orElse(null)
                        if (matching != null) return matching
                    }
                } catch (_: Exception) {}
            }
        }

        // Finally try PATH
        val pathExt = if (isWindows) ".exe" else ""
        val commonNames = listOf("python$targetVersion$pathExt", "python3$pathExt", "python$pathExt")
        for (name in commonNames) {
            val exeFromPath = findInPath(name)
            if (exeFromPath != null) {
                val version = getPythonVersion(exeFromPath)
                if (version == targetVersion || version?.startsWith("$targetVersion.") == true) {
                    return exeFromPath
                }
            }
        }

        return null
    }

    private fun findInPath(executable: String): Path? {
        val path = System.getenv("PATH") ?: return null
        val separator = if (SystemInfo.isWindows) ";" else ":"
        for (dir in path.split(separator)) {
            val file = Path.of(dir).resolve(executable)
            if (file.exists() && (SystemInfo.isWindows || Files.isExecutable(file))) {
                return file
            }
        }
        return null
    }

    fun findPythonExecutable(blenderExePath: Path): Path? {
        val blenderDir = getBlenderInternalDir(blenderExePath) ?: return null
        if (!blenderDir.exists()) return null

        // Blender's python is usually in a version-named folder, e.g., 4.2/python
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
                
                // Fallback for some linux distributions or older versions
                val pythonExeFallback = if (SystemInfo.isWindows) {
                    versionDir.resolve("python").resolve("python.exe")
                } else {
                    versionDir.resolve("python").resolve("bin").resolve("python")
                }
                if (pythonExeFallback.exists()) return pythonExeFallback
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
            // On Linux/Mac, it's usually .../python/lib/python3.x
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
        
        // Also look for Blender's own scripts/modules if they are relative to pythonExe
        // Structure: 
        // blender/
        //   5.0/
        //     python/
        //     scripts/
        //       modules/www
        val versionDir = pythonDir.parent
        if (versionDir != null) {
            val modulesDir = versionDir.resolve("scripts").resolve("modules")
            if (modulesDir.exists()) {
                libPaths.add(modulesDir)
            }
            val addonModulesDir = versionDir.resolve("scripts").resolve("addons").resolve("modules")
            if (addonModulesDir.exists()) {
                libPaths.add(addonModulesDir)
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
            
            // Output looks like "Python 3.11.7\n"
            val match = pythonVersionRegex.find(output.stdout) ?: pythonVersionRegex.find(output.stderr)
            return match?.groupValues?.get(1)
        } catch (e: Exception) {
            return null
        }
    }


    private fun getBlenderInternalDir(blenderExePath: Path): Path? {
        return if (SystemInfo.isMac) {
            // blenderExePath is .../Blender.app/Contents/MacOS/Blender
            blenderExePath.parent?.parent?.resolve("Resources")
        } else {
            blenderExePath.parent
        }
    }
}
