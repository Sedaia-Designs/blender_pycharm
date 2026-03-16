package com.sakurasedaia.blenderextensions.python

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

object PythonUtil {
    private val pythonVersionRegex = Regex("Python (\\d+\\.\\d+)")

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
                val pythonExe = if (SystemInfo.isWindows) pythonBinDir.resolve("python.exe") else pythonBinDir.resolve("python3")
                
                if (pythonExe.exists()) return pythonExe
                
                // Fallback for some linux distributions or older versions
                val pythonExeFallback = if (SystemInfo.isWindows) versionDir.resolve("python").resolve("python.exe") else versionDir.resolve("python").resolve("bin").resolve("python")
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
        //       modules/
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
