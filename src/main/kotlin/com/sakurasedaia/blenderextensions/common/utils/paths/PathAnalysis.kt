package com.sakurasedaia.blenderextensions.common.utils.paths

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

private val LOG = Logger.getInstance("com.sakurasedaia.blenderextensions.common.utils.paths.PathAnalysis")

/**
 * Functions for analyzing paths, such as version detection.
 */

/**
 * Extracts the version number from a path string.
 */
fun extractVersionFromPath(path: String): String? {
    // Look for patterns like "4.2", "4.3", etc.
    val parts = path.split('/', '\\', ' ', '_', '-')
    return parts.find { part ->
        part.length >= 3 && part[0].isDigit() && part[1] == '.' && part[2].isDigit()
    }?.take(3)
}

/**
 * Detects the Blender version from an executable path or directory.
 */
fun detectVersion(project: Project?, path: String): String? {
    // 1. Try to extract from path string
    val fromPath = extractVersionFromPath(path)
    if (fromPath != null && BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == fromPath }) return fromPath

    // 2. Try to get from executable if it exists
    if (java.nio.file.Paths.get(path).exists()) {
        try {
            return getBlenderVersion(path, project)
        } catch (e: Exception) {
            // Ignore failure
        }
    }

    return null
}

/**
 * Gets the version of a Blender executable by running it with --version.
 */
fun getBlenderVersion(blenderExePath: String, project: Project? = null): String {
    return try {
        val process = ProcessBuilder(blenderExePath, "--version").start()
        val output = process.inputStream.bufferedReader().readText()
        // Blender version output is usually "Blender 4.0.2"
        val version = output.split(" ").getOrNull(1)?.split(".")?.take(2)?.joinToString(".")
        version ?: throw IllegalStateException("Could not parse version from: $output")
    } catch (e: Exception) {
        val msg = "Failed to get Blender version from $blenderExePath: ${e.message}"
        if (project != null) BlenderLogger.warn(project, msg) else LOG.warn(msg)
        "Unknown"
    }
}

/**
 * Gets the version of a Python executable by running it with --version.
 */
fun getPythonVersion(pythonExe: Path, project: Project? = null): String? {
    if (!pythonExe.exists()) return null
    return try {
        val process = ProcessBuilder(pythonExe.toString(), "--version").start()
        val output = process.inputStream.bufferedReader().readText().trim()
        // Python version output is usually "Python 3.10.12"
        val version = output.split(" ").lastOrNull()?.split(".")?.take(2)?.joinToString(".")
        version
    } catch (e: Exception) {
        val msg = "Failed to get Python version from $pythonExe: ${e.message}"
        if (project != null) BlenderLogger.warn(project, msg) else LOG.warn(msg)
        null
    }
}
