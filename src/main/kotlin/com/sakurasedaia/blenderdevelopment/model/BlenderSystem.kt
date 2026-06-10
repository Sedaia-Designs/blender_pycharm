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

package com.sakurasedaia.blenderdevelopment.model

/** Normalized host platform information used by Blender compatibility checks. */
data class SysInfo(
    val osName: String,
    val osVersion: String,
    val osArch: String,
    val isWSL: Boolean? = null,
    val bundleFileType: String,
)


/** Helpers for host OS/CPU detection and Blender compatibility validation. */
object BlenderSystem {
    private val sysArch = System.getProperty("os.arch").orEmpty().lowercase()
    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    
    private fun isArch(input: String): Boolean = input in sysArch
    private fun isOS(input: String): Boolean = input in osName
    
    fun isOSCompatible(blMajorMinor: String): Boolean {
        val systemInfo = getSysInfo
        
        if (systemInfo.osName == "unknown") {
            return false
        }
        
        val compatWithOs: Map<String, List<String>>? = BlenderVersions.getCompatibleArch(blMajorMinor)
        
        return compatWithOs?.get(systemInfo.osName)?.contains(systemInfo.osArch) ?: false
    }
    
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
        }
    )
}
