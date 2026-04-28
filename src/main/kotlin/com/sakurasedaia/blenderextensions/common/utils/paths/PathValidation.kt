package com.sakurasedaia.blenderextensions.common.utils.paths

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * Functions for validating paths and checking for safety.
 */

/**
 * Checks if a path is safe to delete.
 */
fun isSafeToDelete(path: Path, project: Project?): Boolean {
    val normalizedPath = path.toAbsolutePath().normalize()

    // 1. NEVER delete system root or user home
    val userHome = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize()
    if (normalizedPath == userHome || normalizedPath.parent == null) return false

    // 2. NEVER delete standard OS directories
    if (isSystemPath(normalizedPath)) return false

    // 3. ALLOW deletion within the managed download directory
    val managedDir = getBaseDownloadDirectory(project).toAbsolutePath().normalize()
    if (normalizedPath.startsWith(managedDir) && normalizedPath != managedDir) return true

    // 4. Boundary checks for project-local deletions
    if (project != null) {
        val projectRoot = Paths.get(project.basePath ?: "").toAbsolutePath().normalize()

        // NEVER delete the project root itself or anything outside it (unless it's the managed dir above)
        if (normalizedPath == projectRoot || !normalizedPath.startsWith(projectRoot)) return false

        // ALWAYS allow deleting the sandbox directory
        val sandboxDir = getSandboxDir(project).toAbsolutePath().normalize()
        if (normalizedPath == sandboxDir || normalizedPath.startsWith(sandboxDir)) return true

        // ALLOW deletion of any directory within project that contains pyvenv.cfg (extra safety for venvs)
        if (normalizedPath.resolve(PYVENV_CFG_NAME).exists()) return true
    }

    return false
}

/**
 * Checks if the path belongs to a critical system directory.
 */
fun isSystemPath(path: Path): Boolean {
    val pathString = path.toString().lowercase()
    val systemPaths = listOf(
        "/bin", "/boot", "/dev", "/etc", "/lib", "/proc", "/root", "/run", "/sbin", "/sys", "/usr", "/var",
        "c:\\windows", "c:\\program files", "c:\\program files (x86)", "c:\\users"
    )
    return systemPaths.any { pathString == it || pathString.startsWith("$it/") || pathString.startsWith("$it\\") }
}

/**
 * Returns a human-readable message if execution is restricted for the given path.
 */
fun getExecutionRestrictionMessage(path: Path): String? {
    if (SystemInfo.isWindows) return null

    // Check if the filesystem is mounted with 'noexec'
    try {
        val lines = Paths.get("/proc/mounts").readLines()
        val mountPoint = findMountPoint(path, lines)
        if (mountPoint != null && (mountPoint.contains("noexec") || mountPoint.contains("user"))) {
            return "The directory ${path.parent} is mounted with 'noexec'. Blender cannot run from here.\n" +
                    "Please move the project to a different partition or refer to the guide:\n$WIKI_EXEC_URL"
        }
    } catch (e: Exception) {
        // Fallback or ignore if /proc/mounts is not available
    }
    return null
}

private fun findMountPoint(path: Path, mounts: List<String>): String? {
    var currentPath = path.toAbsolutePath()
    while (true) {
        val match = mounts.find { it.split(" ")[1] == currentPath.toString() }
        if (match != null) return match
        currentPath = currentPath.parent ?: break
    }
    return null
}
