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

package com.sakurasedaia.blenderdevelopment.lib

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
    
    private fun isArch(input: String): Boolean = input in sysArch
    private fun isOS(input: String): Boolean = input in osName
    
    /**
     * Checks whether the current host platform is supported for the provided Blender major/minor version.
     *
     * Compatibility is evaluated using [BlenderVersions.getCompatibleArch], matching the normalized
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
    
    /**
     * Snapshot of normalized host system information resolved at object initialization time.
     *
     * Values are derived from JVM system properties and environment variables.
     */
    val getSysInfo: SysInfo = SysInfo(
        osName = when {
            isOS("windows") -> "windows"
            isOS("macos") || isOS("mac os x") || isOS("darwin") -> "macos"
            isOS("linux") -> "linux"
            else -> "unknown"
        },
        osVersion = System.getProperty("os.version"),
        osArch = when {
            isArch("aarch64") || isArch("arm64") -> "arm64"
            isArch("x86_64") || isArch("amd64") -> "x64"
            else -> "unknown"
        },
        isWSL = System.getenv("WSL_DISTRO_NAME") != null,
        bundleFileType = when (osName) {
            "windows" -> "exe"
            "macos" -> "dmg"
            "linux" -> "tar.xz"
            else -> "unknown"
        },
        tempDir = PathManager.getTempDir().resolve("blender-development")
    )
    
}
