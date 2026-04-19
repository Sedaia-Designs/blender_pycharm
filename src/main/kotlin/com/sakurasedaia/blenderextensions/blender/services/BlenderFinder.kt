package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.intellij.execution.util.ExecUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.util.PropertiesComponent
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import java.nio.file.Path
import kotlin.io.path.exists

object BlenderFinder {
    private const val VERSION_CACHE_PREFIX = "blender_version_"

    fun tryGetVersion(path: String): String {
        val cacheKey = VERSION_CACHE_PREFIX + path.hashCode()
        val cachedVersion = PropertiesComponent.getInstance().getValue(cacheKey)
        val unknown = LangManager.message("blender.version.unknown")
        if (cachedVersion != null && cachedVersion != unknown) {
            return cachedVersion
        }

        try {
            val commandLine = GeneralCommandLine(path, "--version")
            val output = ExecUtil.execAndGetOutput(commandLine)
            if (output.exitCode != 0) return unknown
            
            val stdout = output.stdout
            val blenderPrefix = "Blender "
            val index = stdout.indexOf(blenderPrefix, ignoreCase = true)
            if (index != -1) {
                val versionPart = stdout.substring(index + blenderPrefix.length).trim()
                val parts = versionPart.split(".")
                if (parts.size >= 2) {
                    val majorMinor = "${parts[0]}.${parts[1]}"
                    PropertiesComponent.getInstance().setValue(cacheKey, majorMinor)
                    return majorMinor
                }
            }
            
            return unknown
        } catch (e: Exception) {
            return unknown
        }
    }

    fun tryWhich(exec: String): String? {
        return try {
            val commandLine = GeneralCommandLine(if (BlenderHelper.isWindows()) "where" else "which", exec)
            val output = ExecUtil.execAndGetOutput(commandLine)
            val result = output.stdoutLines.firstOrNull()?.trim()
            if (output.exitCode == 0 && !result.isNullOrEmpty() && !result.contains("not found")) {
                result
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
