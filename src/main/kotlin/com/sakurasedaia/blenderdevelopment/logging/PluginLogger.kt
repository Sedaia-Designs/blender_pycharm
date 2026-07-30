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

package com.sakurasedaia.blenderdevelopment.logging

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.createDirectories
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.util.currentProject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists

/**
 * Project-level logger that mirrors messages to:
 *  - the IntelliJ platform logger (`idea.log`), respecting its level configuration, and
 *  - a plugin-specific daily file under the configured plugin log path
 *    (`PluginConfig.logPath`) using `blender_plugin_<yyyy-MM-dd>.log`.
 *
 * File I/O is dispatched to a pooled thread and serialized to avoid interleaving and to keep the EDT responsive.
 */
@Service(Service.Level.PROJECT)
class PluginLogger(project: Project) {
    private val platformLogger = Logger.getInstance(PluginLogger::class.java)
    private val defaultLogDir: Path = Path.of(PathManager.getLogPath()).resolve("BlenderExtensions")
    private val writeLock = Any()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    
    /**
     * Appends a raw line to the plugin's daily log file. Safe to call from any thread.
     *
     * @param message log message text.
     * @return `Unit`.
     */
    fun log(message: String) {
        val now = LocalDateTime.now()
        val timestamp = now.format(timestampFormatter)
        val date = now.format(dateFormatter)
        val line = "[$timestamp] $message${System.lineSeparator()}"

        // Dispatch I/O to a background thread to keep the IDE responsive.
        ApplicationManager.getApplication().executeOnPooledThread {
            synchronized(writeLock) {
                val logDir = resolveLogDir()
                try {
                    if (!logDir.exists()) logDir.createDirectories()
                    val logFile = logDir.resolve("blender_plugin_$date.log")
                    Files.write(
                        logFile,
                        line.toByteArray(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                    )
                } catch (e: Exception) {
                    // Surface the failure via the platform logger so it is still discoverable.
                    platformLogger.warn("Failed to write to plugin log file at $logDir", e)
                }
            }
        }
    }

    private fun resolveLogDir(): Path {
        val configuredPath = PluginConfig.getInstance().state.logPath.trim()
        if (configuredPath.isBlank()) return defaultLogDir

        return try {
            Path.of(configuredPath)
        } catch (_: InvalidPathException) {
            platformLogger.warn("Configured plugin log path is invalid: '$configuredPath'. Falling back to $defaultLogDir")
            defaultLogDir
        }
    }

    
    /**
     * Writes a debug entry to both IntelliJ logs and plugin logs.
     *
     * @param message debug message text.
     * @return `Unit`.
     */
    fun debug(message: String) {
        platformLogger.debug(message)
        // Always persist debug entries to the plugin's own log file; the platform logger
        // filters its own output independently based on the IDE's debug categories.
        log("[DEBUG] $message")
    }

    
    /**
     * Writes a warning entry to both IntelliJ logs and plugin logs.
     *
     * @param message warning message text.
     * @return `Unit`.
     */
    fun warn(message: String) {
        platformLogger.warn(message)
        log("[WARN] $message")
    }

    
    /**
     * Writes a warning entry with stack trace details.
     *
     * @param message warning message text.
     * @param throwable associated exception.
     * @return `Unit`.
     */
    fun warn(message: String, throwable: Throwable) {
        platformLogger.warn(message, throwable)
        log("[WARN] $message: ${throwable.stackTraceToString()}")
    }

    
    /**
     * Writes a typed plugin error to IntelliJ logs and plugin logs.
     *
     * @param errorType canonical plugin error code.
     * @param throwable optional exception details.
     * @return `Unit`.
     */
    fun error(errorType: ErrorTypes, throwable: Throwable? = null) {
        platformLogger.error(errorType.message, throwable)
        val suffix = throwable?.let { ": ${it.stackTraceToString()}" } ?: ""
        log("[ERROR] ${errorType.name}: ${errorType.message}$suffix")
    }

    companion object {
        /**
         * Returns the logger service for the given project.
         *
         * @param project target project.
         * @return project-level [PluginLogger] service.
         */
        fun getInstance(project: Project = currentProject()): PluginLogger = project.service()

        
        /**
         * Convenience static wrapper for [log].
         *
         * @param project target project.
         * @param message log message text.
         * @return `Unit`.
         */
        fun log(project: Project = currentProject(), message: String) = getInstance(project).log(message)

        
        /**
         * Convenience static wrapper for [debug].
         *
         * @param project target project.
         * @param message debug message text.
         * @return `Unit`.
         */
        fun debug(project: Project = currentProject(), message: String) = getInstance(project).debug(message)

        
        /**
         * Convenience static wrapper for [warn].
         *
         * @param project target project.
         * @param message warning message text.
         * @return `Unit`.
         */
        fun warn(project: Project = currentProject(), message: String) = getInstance(project).warn(message)


        /**
         * Convenience static wrapper for [error].
         *
         * @param project target project.
         * @param errorType canonical plugin error code.
         * @param throwable optional exception details.
         * @return `Unit`.
         */
        fun error(project: Project = currentProject(), errorType: ErrorTypes, throwable: Throwable? = null) =
            getInstance(project).error(errorType, throwable)
    }
}
