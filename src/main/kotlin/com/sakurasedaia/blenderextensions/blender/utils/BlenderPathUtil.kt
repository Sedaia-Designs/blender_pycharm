package com.sakurasedaia.blenderextensions.blender.utils

import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.blender.services.BlenderFinder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

object BlenderPathUtil {
    fun getBaseDownloadDirectory(project: Project): Path {
        val path = BlenderSettings.getInstance(project).state.downloadsPath
        return Path.of(path)
    }

    fun getAppDirectory(project: Project): Path {
        return getBaseDownloadDirectory(project).resolve("app")
    }

    fun getVersionDirectory(project: Project, version: String?): Path {
        return getAppDirectory(project).resolve(version ?: "unknown")
    }

    fun getSystemBlenderConfigDir(version: String): Path? {
        val userHome = BlenderHelper.getUserHome()
        return when {
            BlenderHelper.isWindows() -> {
                val appData = System.getenv("APPDATA")
                if (appData != null) Paths.get(appData, "Blender Foundation", "Blender", version, "config") else null
            }
            BlenderHelper.isLinux() -> {
                Paths.get(userHome, ".config", "blender", version, "config")
            }
            BlenderHelper.isMac() -> {
                Paths.get(userHome, "Library", "Application Support", "Blender", version, "config")
            }
            else -> null
        }
    }

    fun getBlenderRootConfigDir(): Path? {
        val userHome = BlenderHelper.getUserHome()
        return when {
            BlenderHelper.isWindows() -> {
                val appData = System.getenv("APPDATA")
                if (appData != null) Paths.get(appData, "Blender Foundation", "Blender") else null
            }
            BlenderHelper.isLinux() -> {
                Paths.get(userHome, ".config", "blender")
            }
            BlenderHelper.isMac() -> {
                Paths.get(userHome, "Library", "Application Support", "Blender")
            }
            else -> null
        }
    }

    fun getBlenderExecutableName(): String {
        return when {
            BlenderHelper.isWindows() -> "blender.exe"
            else -> "blender"
        }
    }

    fun findBlenderExecutable(directory: Path): Path? {
        if (!directory.exists()) return null
        if (!directory.isDirectory()) return null

        val executableName = getBlenderExecutableName()
        val isWindows = BlenderHelper.isWindows()

        // Walk the directory to find the executable, limited depth for performance
        val found = Files.walk(directory, 5).use { stream ->
            stream.filter { path ->
                val matchesName = path.name == executableName
                val isFile = path.isRegularFile()
                matchesName && isFile
            }.findFirst().orElse(null)
        }
        return found
    }

    fun extractVersionFromPath(path: String): String? {
        // Search for any of our supported versions in the path
        return BlenderVersions.SUPPORTED_VERSIONS.find {
            path.contains(it.majorMinor)
        }?.majorMinor
    }

    fun detectVersion(project: Project?, path: String): String? {
        val version = BlenderFinder.tryGetVersion(path)
        if (version != "Unknown" && version.isNotBlank()) {
            return version
        }

        // Check managed versions first if we have a project
        if (project != null) {
            for (v in BlenderVersions.SUPPORTED_VERSIONS) {
                val managedDir = getVersionDirectory(project, v.majorMinor)
                if (path.startsWith(managedDir.toString())) {
                    return v.majorMinor
                }
            }
        }

        return extractVersionFromPath(path)
    }
}
