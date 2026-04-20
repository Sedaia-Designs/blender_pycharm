package com.sakurasedaia.blenderextensions.python

import com.sakurasedaia.blenderextensions.python.PythonFinder
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderScanner
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import com.intellij.openapi.application.PathManager
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import com.sakurasedaia.blenderextensions.blender.services.BlenderFinder

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
            com.sakurasedaia.blenderextensions.blender.services.BlenderFinder.tryGetVersion(blenderExePath).takeIf { it != LangManager.message("blender.version.unknown") } ?: "unknown"
        }
    }

    fun getLintDirectory(version: String, project: com.intellij.openapi.project.Project? = null): Path {
        val base = getDownloadsBaseDirectory(project)
        return base.resolve("linter").resolve(version)
    }

    fun findSystemPython(targetVersion: String, project: Project? = null): Path? {
        return PythonFinder.findSystemPython(targetVersion, project)
    }
    
    fun findPythonExecutable(blenderExePath: Path, project: Project? = null): Path? {
        BlenderLogger.debug(project, "findPythonExecutable: Searching for bundled Python in $blenderExePath")
        val blenderDir = getBlenderInternalDir(blenderExePath) ?: return null
        if (!blenderDir.exists()) {
            BlenderLogger.debug(project, "findPythonExecutable: Blender internal directory does not exist: $blenderDir")
            return null
        }

        try {
            val versionDirs = Files.list(blenderDir).use { stream ->
                stream.filter {
                    it.name.all { c -> c.isDigit() || c == '.' } && Files.isDirectory(it)
                }
                    .toList()
            }
            BlenderLogger.debug(project, "findPythonExecutable: Found versioned directories in Blender: $versionDirs")

            // First try versioned Blender paths: <root>/<version>/python/bin
            for (versionDir in versionDirs) {
                BlenderLogger.debug(project, "findPythonExecutable: Checking versioned dir: $versionDir")
                findPythonInDirectory(versionDir.resolve("python").resolve("bin"))?.let {
                    BlenderLogger.debug(project, "findPythonExecutable: Found bundled Python at $it")
                    return it
                }
                findPythonInDirectory(versionDir.resolve("python"))?.let {
                    BlenderLogger.debug(project, "findPythonExecutable: Found bundled Python at $it")
                    return it
                }
            }

            // Then try non-versioned layout fallbacks: <root>/python/bin
            BlenderLogger.debug(project, "findPythonExecutable: Checking non-versioned layout fallbacks in $blenderDir")
            findPythonInDirectory(blenderDir.resolve("python").resolve("bin"))?.let {
                BlenderLogger.debug(project, "findPythonExecutable: Found bundled Python at $it")
                return it
            }
            findPythonInDirectory(blenderDir.resolve("python"))?.let {
                BlenderLogger.debug(project, "findPythonExecutable: Found bundled Python at $it")
                return it
            }
        } catch (e: Exception) {
            BlenderLogger.debug(project, "findPythonExecutable: Error searching for bundled Python: ${e.message}")
            return null
        }
        BlenderLogger.debug(project, "findPythonExecutable: No bundled Python found in $blenderExePath")
        return null
    }

    private fun findPythonInDirectory(directory: Path): Path? {
        if (!directory.exists() || !Files.isDirectory(directory)) return null

        if (BlenderHelper.isWindows()) {
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

    fun getPythonVersion(pythonExe: Path, project: Project? = null): String? {
        return PythonFinder.getPythonVersion(pythonExe, project)
    }


    private fun getBlenderInternalDir(blenderExePath: Path): Path? {
        return blenderExePath.parent
    }
}
