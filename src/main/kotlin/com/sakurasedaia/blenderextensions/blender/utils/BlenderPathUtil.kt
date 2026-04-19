package com.sakurasedaia.blenderextensions.blender.utils

import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
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
        if (!directory.exists() || !directory.isDirectory()) return null

        val executableName = getBlenderExecutableName()
        val isWindows = BlenderHelper.isWindows()

        // Walk the directory to find the executable, limited depth for performance
        Files.walk(directory, 3).use { stream ->
            return stream.filter { path ->
                path.name == executableName && path.isRegularFile() && (isWindows || Files.isExecutable(path))
            }.findFirst().orElse(null)
        }
    }
}
