package com.sakurasedaia.blenderextensions.system

import com.intellij.execution.util.ExecUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.util.PropertiesComponent
import com.sakurasedaia.blenderextensions.LangManager
import java.nio.file.Path
import kotlin.io.path.exists

object BlenderFinder {
    private const val VERSION_CACHE_PREFIX = "blender_version_"
    private val versionRegex = Regex("Blender (\\d+\\.\\d+)")

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
            
            val match = versionRegex.find(output.stdout)
            val version = match?.groupValues?.get(1) ?: unknown

            if (version != unknown) {
                PropertiesComponent.getInstance().setValue(cacheKey, version)
            }

            return version
        } catch (e: Exception) {
            return unknown
        }
    }

    fun tryWhich(exec: String): String? {
        return try {
            val commandLine = GeneralCommandLine(if (System.getProperty("os.name").lowercase().contains("win")) "where" else "which", exec)
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
