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
    private val sysArch = { arch: String -> System.getProperty("os.arch").contains(arch) }
    // returns a simple value in line with Blender's Filename scheme
    private val cpuArch = when {
        sysArch("aarch64") || sysArch("arm64") -> "arm64"
        sysArch("x86_64") || sysArch("amd64") -> "x64"
        else -> "unknown"
    }
    fun isFileExt(path: Path, extension: String): Boolean = path.name.endsWith(extension)
    
    private val parseOsName = when {
        System.getProperty("os.name").lowercase().contains("windows") -> "win"
        System.getProperty("os.name").lowercase().contains("mac os x") -> "mac"
        System.getProperty("os.name").lowercase().contains("linux") -> "linux"
        else -> "unknown"
    }
    
    val Info: SysInfo = SysInfo(
        osName = parseOsName,
        osVersion = System.getProperty("os.version"),
        osArch = cpuArch
    )
    
    fun isOSCompatible(blMajorMinor: String): Boolean {
        val systemInfo = Info
        
        if (systemInfo.osName == "unknown") {
            return false
        }
        
        val compatWithOs: Map<String, List<String>>? = BlenderVersions.getCompatibleArch(blMajorMinor)
        
        return compatWithOs?.get(systemInfo.osName)?.contains(systemInfo.osArch) ?: false
    }
}
