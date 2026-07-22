/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.util

import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.intellij.openapi.application.PathManager
import java.nio.file.Path

/**
 * Normalized host platform information used by Blender compatibility checks.
 *
 * @property osName normalized operating system identifier (`windows`, `macos`, `linux`, or `unknown`).
 * @property osVersion raw host OS version string from the JVM `os.version` property.
 * @property osArch normalized CPU architecture (`arm64`, `x64`, or `unknown`).
 * @property isWSL whether execution appears to be under Windows Subsystem for Linux.
 * @property bundleFileType expected Blender distribution file suffix for the current platform.
 * @property tempDir plugin-specific temporary directory under the IDE temp path.
 */
data class SysInfo(
    val osName: String,
    val osVersion: String,
    val osArch: String,
    val isWSL: Boolean? = null,
    val bundleFileType: String,
    val tempDir: Path
)


/**
 * Helpers for host OS/CPU detection and Blender version compatibility validation.
 */
object SystemHelper {
    private val sysArch = System.getProperty("os.arch").orEmpty().lowercase()
    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    
    /**
     * Checks whether the current host platform is supported for the provided Blender major/minor version.
     *
     * Compatibility is evaluated using [com.sakurasedaia.blenderdevelopment.lib.BlenderVersions.getCompatibleArch], matching the normalized
     * [getSysInfo.osName] and [getSysInfo.osArch] values.
     *
     * @param blMajorMinor Blender version key in `major.minor` form (for example, `4.5`).
     * @return `true` when the current platform is listed as compatible for that Blender version.
     */
    fun isOSCompatible(blMajorMinor: String): Boolean {
        val systemInfo = getSysInfo
        
        if (systemInfo.osName == "unknown") {
            return false
        }
        
        val compatWithOs: Map<String, List<String>>? = BlenderVersions.getCompatibleArch(blMajorMinor)
        
        return compatWithOs?.get(systemInfo.osName)?.contains(systemInfo.osArch) ?: false
    }
    
    /** Normalizes an operating-system name to Blender's download filename convention. */
    fun normalizeOSName(osName: String): String {
        val value = osName.lowercase()
        return when {
            "windows" in value || value == "win" -> "windows"
            "macos" in value || "mac os x" in value || "darwin" in value || value == "mac" -> "macos"
            "linux" in value -> "linux"
            else -> "unknown"
        }
    }

    /** Normalizes a CPU architecture to Blender's download filename convention. */
    fun normalizeOsArch(arch: String): String {
        val value = arch.lowercase()
        return when {
            "aarch64" in value || "arm64" in value -> "arm64"
            "x86_64" in value || "amd64" in value || value == "x64" -> "x64"
            else -> "unknown"
        }
    }

    /** Returns the preferred Blender distribution suffix for an operating system. */
    fun normalizeBundleFileType(osName: String) = when (osName) {
        "windows" -> "zip"
        "macos" -> "dmg"
        "linux" -> "tar.xz"
        else -> "unknown"
    }

    /** Returns whether Blender publishes the requested package type for the target operating system. */
    fun isBundleFileTypeSupported(osName: String, fileExtension: String): Boolean = when (osName) {
        "windows" -> fileExtension in setOf("zip", "msi", "msix")
        "macos" -> fileExtension == "dmg"
        "linux" -> fileExtension == "tar.xz"
        else -> false
    }

    /**
     * Snapshot of normalized host system information resolved at object initialization time.
     *
     * Values are derived from JVM system properties and environment variables.
     */
    val getSysInfo: SysInfo = SysInfo(
        osName = normalizeOSName(osName),
        osVersion = System.getProperty("os.version"),
        osArch = normalizeOsArch(sysArch),
        isWSL = System.getenv("WSL_DISTRO_NAME") != null,
        bundleFileType = normalizeBundleFileType(normalizeOSName(osName)),
        tempDir = PathManager.getTempDir().resolve("blender-development")
    )
    
}
