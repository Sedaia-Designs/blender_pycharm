package com.sakurasedaia.blenderdevelopment.model

import com.intellij.openapi.util.SystemInfo
import kotlinx.io.files.Path

object SystemHelper {
    private val sysArch = { arch: String -> System.getProperty("os.arch").contains(arch) }
    // returns a simple value in line with Blender's Filename scheme
    private val cpuArch = when {
        sysArch("aarch64") || sysArch("arm64") -> "arm64"
        sysArch("x86_64") || sysArch("amd64") -> "x64"
        else -> "unknown"
    }
    fun isFileExt(path: Path, extension: String): Boolean = path.name.endsWith(extension)
    fun getCpuArch(): String = cpuArch
}
