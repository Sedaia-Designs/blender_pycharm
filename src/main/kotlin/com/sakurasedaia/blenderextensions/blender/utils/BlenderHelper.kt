package com.sakurasedaia.blenderextensions.blender.utils

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import kotlin.io.path.extension

object BlenderHelper {
    /**
     * Blender is only compatible with the following:
     * Windows x64, Windows Arm64, Linux x64, and MacOS Arm64.
     */
    fun isOSCompatible(): Boolean {
        return when {
            isWindows() -> isX86_64() || isArm64()
            isLinux() -> isX86_64()
            isMac() -> isArm64()
            else -> false
        }
    }

    fun isWindows(): Boolean = SystemInfo.isWindows
    fun isLinux(): Boolean = SystemInfo.isLinux
    fun isMac(): Boolean = SystemInfo.isMac

    fun isArm64(): Boolean = System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm64") }
    fun isX86_64(): Boolean = System.getProperty("os.arch").lowercase().let { it.contains("x86_64") || it.contains("amd64") }

    fun isZip(path: Path): Boolean = path.extension.lowercase() == "zip"
    fun isTarXz(path: Path): Boolean = path.toString().lowercase().endsWith(".tar.xz")
    fun isDmg(path: Path): Boolean = path.extension.lowercase() == "dmg"

    fun getOsName(): String {
        return when {
            isWindows() -> "windows"
            isLinux() -> "linux"
            isMac() -> "macos"
            else -> "unknown"
        }
    }

    fun getArchName(): String {
        return when {
            isArm64() -> "arm64"
            isX86_64() -> "x64"
            else -> "unknown"
        }
    }

    fun getUserHome(): String = System.getProperty("user.home")
    fun getOsVersion(): String = System.getProperty("os.version")
    fun getRawOsName(): String = System.getProperty("os.name")
    fun getRawArchName(): String = System.getProperty("os.arch")

    fun getExtensionByOs(osName: String = getOsName()): String {
        return when (osName) {
            "windows" -> "zip"
            "linux" -> "tar.xz"
            "macos" -> "dmg"
            else -> throw (IllegalArgumentException("OS is not supported"))
        }
    }

    fun parseVersion(version: String): List<Int> {
        return try {
            version.split('.').mapNotNull { it.toIntOrNull() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun compareVersions(v1: List<Int>, v2: List<Int>): Int {
        val size = maxOf(v1.size, v2.size)
        for (i in 0 until size) {
            val p1 = if (i < v1.size) v1[i] else 0
            val p2 = if (i < v2.size) v2[i] else 0
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
