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

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.appendText
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
internal class PluginLogger(private val project: Project) {
    private val platformLogger = Logger.getInstance(PluginLogger::class.java)
    
    fun log(message: String) {
        // Platform Logging
        platformLogger.info(message)
        
        // Official Intellij Log Directory
        val logPath = Path.of(PathManager.getLogPath()).resolve("blender-plugin")
        
        try {
            if (!logPath.exists()) {
                Files.createDirectories(logPath)
            }
            
            // Custom File Logging
            val dateTime = { format: String -> LocalDateTime.now().format(DateTimeFormatter.ofPattern(format)) }
            val date = dateTime("yyyy-MM-dd")
            val timestamp = dateTime("yyyy-MM-dd HH:mm:ss")
            val logFile = logPath.resolve("blender_plugin_$date.log")
            
            logFile.appendText("[$timestamp] $message\n")
        } catch (_: Exception) {
            // Silently ignore logging errors
        }
    }
    
    fun debug(message: String) {
        println("[DEBUG]: $message")
        
        platformLogger.debug(message)
        if (platformLogger.isDebugEnabled) {
            log("[DEBUG]: $message")
        }
    }
    
    fun warn(message: String) {
        platformLogger.warn(message)
        log("[WARN]: $message")
    }
    
    fun error(message: String) {
        platformLogger.error(message)
        log("[ERROR]: $message")
    }
    
    companion object {
        fun getInstance(project: Project): PluginLogger = project.service()
        
        fun log(project: Project, message: String) {
            project.let { getInstance(it ).log(message) }
        }
        
        fun debug(project: Project, message: String) {
            project.let { getInstance(it ).debug(message) }
        }
        
        fun warn(project: Project, message: String) {
            project.let { getInstance(it ).warn(message) }
        }
        
        fun error(project: Project, message: String) {
            project.let { getInstance(it ).error(message) }
        }
    }
}