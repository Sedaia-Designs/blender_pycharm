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

import kotlinx.io.files.Path

/** Normalized host platform information used by Blender compatibility checks. */
data class SysInfo(
    val osName: String,
    val osVersion: String,
    val osArch: String,
)


/** Helpers for host OS/CPU detection and Blender compatibility validation. */
object SystemHelper {
    private val rawSysArch = System.getProperty("os.arch").orEmpty().lowercase()
    
    
    /**
     * Returns whether the normalized CPU architecture string contains [input].
     *
     * @param input architecture token to match.
     * @return `true` when the token is present in the architecture string.
     */
    
    private fun isArch(input: String): Boolean = input in rawSysArch
    
    // returns a simple value in line with Blender's Filename scheme
    private val cpuArch = when {
        isArch("aarch64") || isArch("arm64") -> "arm64"
        isArch("x86_64") || isArch("amd64") -> "x64"
        else -> "unknown"
    }
    
    
    /**
     * Returns whether the file path ends with the provided extension.
     *
     * @param path file path to inspect.
     * @param extension expected extension.
     * @return `true` when the file name ends with [extension], case-insensitive.
     */
    fun isFileExt(path: Path, extension: String): Boolean = path.name.endsWith(extension, ignoreCase=true)
    
    private val rawOsName = System.getProperty("os.name").orEmpty().lowercase()
    
    
    /**
     * Returns whether the normalized OS name string contains [input].
     *
     * @param input OS token to match.
     * @return `true` when the token is present in the OS name string.
     */
    private fun isOS(input: String): Boolean = input in rawOsName
    private val parseOsName = when {
        isOS("windows") -> "windows"
        isOS("macos") || isOS("mac os x") || isOS("darwin") -> "macos"
        isOS("linux") -> "linux"
        else -> "unknown"
    }
    
    val getSystemBundleExtension: (String) -> String = { os: String -> when (os) {
            "windows" -> "zip"
            "macos" -> "dmg"
            "linux" -> "tar.gz"
            else -> "unknown"
        }
    }
    
    val sysInfo: SysInfo = SysInfo(
        osName = parseOsName,
        osVersion = System.getProperty("os.version"),
        osArch = cpuArch
    )
    
    
    /**
     * Returns whether the current host can run the selected Blender major/minor version.
     *
     * @param blMajorMinor target Blender major/minor version.
     * @return `true` if current OS/arch is listed as compatible.
     */
    fun isOSCompatible(blMajorMinor: String): Boolean {
        val systemInfo = sysInfo
        
        if (systemInfo.osName == "unknown") {
            return false
        }
        
        val compatWithOs: Map<String, List<String>>? = BlenderVersions.getCompatibleArch(blMajorMinor)
        
        return compatWithOs?.get(systemInfo.osName)?.contains(systemInfo.osArch) ?: false
    }
}
