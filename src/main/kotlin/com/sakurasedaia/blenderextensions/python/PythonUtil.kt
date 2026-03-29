package com.sakurasedaia.blenderextensions.python

import com.sakurasedaia.blenderextensions.system.PythonFinder
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderScanner
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

object PythonUtil {

    private fun getDownloadsBaseDirectory(project: com.intellij.openapi.project.Project? = null): Path {
        val configuredPath = project?.let { BlenderSettings.getInstance(it).state.downloadsPath }
            ?: Path.of(PathManager.getSystemPath(), "blender_downloads").toString()
        val configured = Path.of(configuredPath)

        // Backward compatibility: older versions stored interpreter downloads under py_interpreter.
        return if (configured.fileName?.toString() == "py_interpreter") {
            configured.parent ?: configured
        } else {
            configured
        }
    }

    fun getBlenderVersion(blenderExePath: String): String {
        val path = Path.of(blenderExePath)
        return if (blenderExePath.contains("blender_downloads")) {
            path.parent.name
        } else {
            com.sakurasedaia.blenderextensions.system.BlenderFinder.tryGetVersion(blenderExePath).takeIf { it != LangManager.message("blender.version.unknown") } ?: "unknown"
        }
    }

    fun getLintDirectory(version: String, project: com.intellij.openapi.project.Project? = null): Path {
        val base = getDownloadsBaseDirectory(project)
        return base.resolve("linter").resolve(version)
    }

    fun getPythonInterpreterDirectory(version: String, project: com.intellij.openapi.project.Project? = null): Path {
        val base = getDownloadsBaseDirectory(project)
        return base.resolve("python").resolve(version)
    }

    fun getLegacyPythonInterpreterDirectory(version: String, project: com.intellij.openapi.project.Project? = null): Path {
        val base = getDownloadsBaseDirectory(project)
        return base.resolve("py_interpreter").resolve(version)
    }

    fun findSystemPython(targetVersion: String): Path? {
        return PythonFinder.findSystemPython(targetVersion)
    }
    
    fun findPythonExecutable(blenderExePath: Path): Path? {
        println("[DEBUG_LOG] findPythonExecutable: Searching for bundled Python in $blenderExePath")
        val blenderDir = getBlenderInternalDir(blenderExePath) ?: return null
        if (!blenderDir.exists()) {
            println("[DEBUG_LOG] findPythonExecutable: Blender internal directory does not exist: $blenderDir")
            return null
        }

        try {
            val versionDirs = Files.list(blenderDir).use { stream ->
                stream.filter {
                    it.name.all { c -> c.isDigit() || c == '.' } && Files.isDirectory(it)
                }
                    .toList()
            }
            println("[DEBUG_LOG] findPythonExecutable: Found versioned directories in Blender: $versionDirs")

            // First try versioned Blender paths: <root>/<version>/python/bin
            for (versionDir in versionDirs) {
                println("[DEBUG_LOG] findPythonExecutable: Checking versioned dir: $versionDir")
                findPythonInDirectory(versionDir.resolve("python").resolve("bin"))?.let {
                    println("[DEBUG_LOG] findPythonExecutable: Found bundled Python at $it")
                    return it
                }
                findPythonInDirectory(versionDir.resolve("python"))?.let {
                    println("[DEBUG_LOG] findPythonExecutable: Found bundled Python at $it")
                    return it
                }
            }

            // Then try non-versioned layout fallbacks: <root>/python/bin
            println("[DEBUG_LOG] findPythonExecutable: Checking non-versioned layout fallbacks in $blenderDir")
            findPythonInDirectory(blenderDir.resolve("python").resolve("bin"))?.let {
                println("[DEBUG_LOG] findPythonExecutable: Found bundled Python at $it")
                return it
            }
            findPythonInDirectory(blenderDir.resolve("python"))?.let {
                println("[DEBUG_LOG] findPythonExecutable: Found bundled Python at $it")
                return it
            }
        } catch (e: Exception) {
            println("[DEBUG_LOG] findPythonExecutable: Error searching for bundled Python: ${e.message}")
            return null
        }
        println("[DEBUG_LOG] findPythonExecutable: No bundled Python found in $blenderExePath")
        return null
    }

    private fun findPythonInDirectory(directory: Path): Path? {
        if (!directory.exists() || !Files.isDirectory(directory)) return null

        if (SystemInfo.isWindows) {
            val candidate = directory.resolve("python.exe")
            return candidate.takeIf { it.exists() }
        }

        val preferredNames = listOf("python3", "python")
        for (name in preferredNames) {
            val candidate = directory.resolve(name)
            if (candidate.exists()) return candidate
        }

        return Files.list(directory).use { stream ->
            stream.filter {
                val fileName = it.fileName.toString()
                fileName.startsWith("python") && Files.isRegularFile(it)
            }.findFirst().orElse(null)
        }
    }

    fun getPythonVersion(pythonExe: Path): String? {
        return PythonFinder.getPythonVersion(pythonExe)
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
