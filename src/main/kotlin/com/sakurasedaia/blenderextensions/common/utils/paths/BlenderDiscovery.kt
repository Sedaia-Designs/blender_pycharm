package com.sakurasedaia.blenderextensions.common.utils.paths

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

private val LOG = Logger.getInstance("com.sakurasedaia.blenderextensions.common.utils.paths.BlenderDiscovery")

/**
 * Functions for discovering Blender and Python executables.
 */

/**
 * Gets the platform-specific Blender executable name.
 */
fun getBlenderExecutableName(): String {
    return when {
        SystemInfo.isWindows -> "blender.exe"
        SystemInfo.isMac -> "blender"
        else -> "blender"
    }
}

/**
 * Finds the Blender executable within a given directory.
 */
fun findBlenderExecutable(directory: Path): Path? {
    if (!directory.exists()) return null

    // Direct check
    val directPath = directory.resolve(getBlenderExecutableName())
    if (directPath.exists()) return directPath

    // Mac specific structure check
    if (SystemInfo.isMac) {
        val macExe = findMacExecutable(directory)
        if (macExe != null) return macExe
    }

    // Recursive search (shallow)
    return try {
        directory.listDirectoryEntries().firstOrNull {
            it.name == getBlenderExecutableName() || (it.isDirectory() && it.resolve(getBlenderExecutableName()).exists())
        }?.let { if (it.isDirectory()) it.resolve(getBlenderExecutableName()) else it }
    } catch (e: Exception) {
        null
    }
}

/**
 * Finds the Blender executable within a macOS .app bundle.
 */
fun findMacExecutable(directory: Path): Path? {
    // Check if the directory itself is the executable (rare) or contains the app bundle
    val appBundle = if (directory.name.endsWith(".app")) directory else {
        directory.listDirectoryEntries("*.app").firstOrNull()
    }

    return appBundle?.resolve("Contents")?.resolve("MacOS")?.resolve("Blender")?.takeIf { it.exists() }
}

/**
 * Finds the system Python executable that matches the target version.
 */
fun findSystemPythonExecutable(targetVersion: String, project: Project? = null): Path? {
    val pythonNames = if (SystemInfo.isWindows) listOf("python.exe", "python3.exe") else listOf("python3", "python")

    // 1. Check common system paths
    val commonPaths = if (SystemInfo.isWindows) {
        listOf("C:\\Python311", "C:\\Python310", "C:\\srv\\python")
    } else {
        listOf("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin")
    }

    for (path in commonPaths) {
        val dir = Path(path)
        if (!dir.exists()) continue
        for (name in pythonNames) {
            val exe = dir.resolve(name)
            if (exe.exists()) {
                val version = getPythonVersion(exe, project)
                if (version != null && version.startsWith(targetVersion)) return exe
            }
        }
    }

    // 2. Try 'which' or 'where' command
    try {
        val process = ProcessBuilder(if (SystemInfo.isWindows) listOf("where", "python") else listOf("which", "-a", "python3")).start()
        val output = process.inputStream.bufferedReader().readText()
        output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val exe = Path(trimmed)
                if (exe.exists()) {
                    val version = getPythonVersion(exe, project)
                    if (version != null && version.startsWith(targetVersion)) return exe
                }
            }
        }
    } catch (e: Exception) {
        // Ignore command failures
    }

    // 3. Fallback
    return null
}

/**
 * Finds the bundled Python executable within a Blender installation.
 */
fun findBundledPython(blenderExePath: Path, project: Project? = null): Path? {
    val blenderDir = if (SystemInfo.isMac) {
        // On Mac, the executable is in Contents/MacOS/Blender, but Python is in Contents/Resources
        blenderExePath.parent.parent.resolve("Resources")
    } else {
        blenderExePath.parent
    }

    // Look for a versioned directory (e.g., 3.6, 4.0)
    val versionDirs = try {
        blenderDir.listDirectoryEntries().filter { it.isDirectory() && it.name.firstOrNull()?.isDigit() == true }
    } catch (e: Exception) {
        emptyList()
    }

    for (vDir in versionDirs) {
        val pythonDir = vDir.resolve("python")
        if (pythonDir.exists()) {
            val pythonExe = findPythonInDirectory(pythonDir)
            if (pythonExe != null) return pythonExe
        }
    }

    return null
}

/**
 * Finds the Python executable within a given directory.
 */
fun findPythonInDirectory(directory: Path): Path? {
    val names = if (SystemInfo.isWindows) {
        listOf("python.exe", "bin/python.exe", "Scripts/python.exe")
    } else {
        listOf("bin/python3", "bin/python", "python")
    }

    for (name in names) {
        val exe = directory.resolve(name)
        if (exe.exists()) return exe
    }

    // Last resort: search recursively but shallow
    return try {
        Files.walk(directory, 2).filter { it.nameWithoutExtension.lowercase() == "python" || it.nameWithoutExtension.lowercase() == "python3" }.findFirst().orElse(null)
    } catch (e: Exception) {
        null
    }
}
