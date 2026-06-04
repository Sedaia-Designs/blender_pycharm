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

data class SysInfo(
    val osName: String,
    val osVersion: String,
    val osArch: String,
)

object SystemHelper {
    private val rawSysArch = System.getProperty("os.arch").orEmpty().lowercase()
    private fun isArch(input: String): Boolean = input in rawSysArch
    // returns a simple value in line with Blender's Filename scheme
    private val cpuArch = when {
        isArch("aarch64") || isArch("arm64") -> "arm64"
        isArch("x86_64") || isArch("amd64") -> "x64"
        else -> "unknown"
    }
    fun isFileExt(path: Path, extension: String): Boolean = path.name.endsWith(extension, ignoreCase=true)
    
    
    private val rawOsName = System.getProperty("os.name").orEmpty().lowercase()
    private fun isOS(input: String): Boolean = input in rawOsName
    private val parseOsName = when {
        isOS("windows") -> "win"
        isOS("macos") || isOS("mac os x") || isOS("darwin") -> "mac"
        isOS("linux") -> "linux"
        else -> "unknown"
    }
    
    val sysInfo: SysInfo = SysInfo(
        osName = parseOsName,
        osVersion = System.getProperty("os.version"),
        osArch = cpuArch
    )
    
    fun isOSCompatible(blMajorMinor: String): Boolean {
        val systemInfo = sysInfo
        
        if (systemInfo.osName == "unknown") {
            return false
        }
        
        val compatWithOs: Map<String, List<String>>? = BlenderVersions.getCompatibleArch(blMajorMinor)
        
        return compatWithOs?.get(systemInfo.osName)?.contains(systemInfo.osArch) ?: false
    }
}
