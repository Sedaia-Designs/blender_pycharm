package com.sakurasedaia.blenderextensions.blender

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name

object BlenderPathUtil {
    private val userHome: String = System.getProperty("user.home")

    fun getSystemBlenderConfigDir(version: String): Path? {
        return when {
            SystemInfo.isWindows -> {
                val appData = System.getenv("APPDATA")
                if (appData != null) Paths.get(appData, "Blender Foundation", "Blender", version, "config") else null
            }
            SystemInfo.isLinux -> {
                Paths.get(userHome, ".config", "blender", version, "config")
            }
            else -> null
        }
    }

    fun getBlenderRootConfigDir(): Path? {
        return when {
            SystemInfo.isWindows -> {
                val appData = System.getenv("APPDATA")
                if (appData != null) Paths.get(appData, "Blender Foundation", "Blender") else null
            }
            SystemInfo.isLinux -> {
                Paths.get(userHome, ".config", "blender")
            }
            else -> null
        }
    }

    fun getBlenderExecutableName(): String {
        return when {
            SystemInfo.isWindows -> "blender.exe"
            else -> "blender"
        }
    }
}
