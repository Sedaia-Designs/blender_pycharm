package com.sakurasedaia.blenderextensions.common.utils

import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import java.nio.file.Path
import kotlin.io.path.*

object FileUtil {
    fun copyDirectory(source: Path, target: Path) {
        source.walk().forEach { sourcePath ->
            val targetPath = target.resolve(source.relativize(sourcePath))
            if (sourcePath.isDirectory()) {
                targetPath.createDirectories()
            } else {
                sourcePath.copyTo(targetPath, overwrite = true)
            }
        }
    }

    /**
     * Ensures the given path has execution permissions on Unix-like systems.
     */
    fun makeExecutable(path: Path) {
        if (com.intellij.openapi.util.SystemInfo.isWindows) return
        if (!path.exists() || path.isDirectory()) return

        try {
            path.toFile().setExecutable(true)
        } catch (_: Exception) {
            // Ignore failures to set executable bit
        }
    }

    private const val WIKI_EXEC_URL = "https://wiki.sakura-sedaia.com/blender-development-pycharm/usage/linux-execution-guide.html"

    /**
     * Validates if a binary can be executed at the given path, checking for filesystem-level restrictions.
     * Returns a descriptive error message if execution is likely to fail, or null if it seems okay.
     */
    fun getExecutionRestrictionMessage(path: Path): String? {
        if (!com.intellij.openapi.util.SystemInfo.isLinux) return null
        
        try {
            val absolutePath = path.toAbsolutePath().toString()
            
            // 1. Check current mount state via /proc/self/mountinfo
            val mountinfo = java.io.File("/proc/self/mountinfo")
            if (mountinfo.exists()) {
                val lines = mountinfo.readLines()
                var bestMountPoint = ""
                var optionsStr = ""
                
                for (line in lines) {
                    val parts = line.split(" ")
                    if (parts.size < 6) continue
                    val mountPoint = parts[4]
                    if (absolutePath.startsWith(mountPoint) && mountPoint.length > bestMountPoint.length) {
                        bestMountPoint = mountPoint
                        optionsStr = parts[5]
                    }
                }
                
                if (optionsStr.split(",").contains("noexec")) {
                    return LangManager.message("log.error.noexec.mount", bestMountPoint, WIKI_EXEC_URL)
                }
            }

            // 2. Proactively check /etc/fstab for the 'users' flag which implies 'noexec'
            val fstab = java.io.File("/etc/fstab")
            if (fstab.exists()) {
                val lines = fstab.readLines()
                for (line in lines) {
                    if (line.trim().startsWith("#") || line.isBlank()) continue
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 4) continue
                    
                    val mountPoint = parts[1]
                    if (absolutePath.startsWith(mountPoint) && mountPoint != "/") {
                        val options = parts[3].split(",")
                        val hasUser = options.contains("user") || options.contains("users")
                        val hasExec = options.contains("exec")
                        val hasNoExec = options.contains("noexec")
                        
                        if (hasNoExec || (hasUser && !hasExec)) {
                            return LangManager.message("log.error.noexec.fstab", mountPoint, WIKI_EXEC_URL)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore errors in detection and assume it might work
        }
        
        return null
    }
}
