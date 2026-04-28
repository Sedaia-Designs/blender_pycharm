package com.sakurasedaia.blenderextensions.common.utils.paths

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Functions for system-wide or managed directory paths (downloads, config, etc.).
 */

/**
 * Gets the base directory for Blender downloads.
 * On Windows, this is typically %AppData%/BlenderExtensions/Blender.
 * On Unix, it follows XDG standards (~/.local/share/BlenderExtensions/Blender).
 */
fun getBaseDownloadDirectory(project: Project? = null): Path {
    val base = Paths.get(PathManager.getSystemPath()).resolve("BlenderExtensions")
    return base.resolve("Downloads")
}

/**
 * Gets the directory where Blender applications are installed.
 */
fun getAppDirectory(project: Project? = null): Path {
    return getBaseDownloadDirectory(project).resolve("App")
}

/**
 * Gets the directory for a specific Blender version.
 */
fun getVersionDirectory(project: Project, version: String?): Path {
    val base = getAppDirectory(project)
    return if (version != null) base.resolve(version) else base
}

/**
 * Gets the directory for linter files for a specific Blender version.
 */
fun getLintDirectory(version: String, project: Project? = null): Path {
    return getBaseDownloadDirectory(project).resolve("Linter").resolve(version)
}

/**
 * Gets the system's Blender configuration directory for a specific version.
 */
fun getSystemBlenderConfigDir(version: String): Path? {
    val root = getBlenderRootConfigDir() ?: return null
    val path = root.resolve(version).resolve("config")
    return if (path.exists()) path else null
}

/**
 * Gets the root directory where Blender stores its configuration on the system.
 */
fun getBlenderRootConfigDir(): Path? {
    return when {
        SystemInfo.isWindows -> {
            val appData = System.getenv("APPDATA") ?: return null
            Paths.get(appData).resolve("Blender Foundation").resolve("Blender")
        }
        SystemInfo.isMac -> {
            Paths.get(System.getProperty("user.home")).resolve("Library").resolve("Application Support").resolve("Blender")
        }
        SystemInfo.isLinux -> {
            val xdgConfig = System.getenv("XDG_CONFIG_HOME")
            if (xdgConfig != null) {
                Paths.get(xdgConfig).resolve("blender")
            } else {
                Paths.get(System.getProperty("user.home")).resolve(".config").resolve("blender")
            }
        }
        else -> null
    }
}
