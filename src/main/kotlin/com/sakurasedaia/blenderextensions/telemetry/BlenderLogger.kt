package com.sakurasedaia.blenderextensions.telemetry

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.appendText
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class BlenderLogger(private val project: Project) {
    private val platformLogger = Logger.getInstance(BlenderLogger::class.java)

    fun log(message: String) {
        // Platform logging
        platformLogger.info(message)

        // Use official IntelliJ log directory
        val logPath = Path.of(PathManager.getLogPath()).resolve("blender-plugin")
        
        try {
            if (!logPath.exists()) {
                Files.createDirectories(logPath)
            }

            // Custom file logging
            val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val logFile = logPath.resolve("blender_plugin_$date.log")
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            logFile.appendText("[$timestamp] $message\n")
        } catch (_: Exception) {
            // Silently ignore logging errors
        }
    }

    fun debug(message: String) {
        // Also print to stdout for visibility in the IDE debug console during development
        println("[DEBUG] $message")
        
        platformLogger.debug(message)
        if (platformLogger.isDebugEnabled) {
            log("DEBUG: $message")
        }
    }

    fun error(message: String, e: Throwable? = null) {
        platformLogger.error(message, e)
        log("ERROR: $message" + (if (e != null) " - ${e.message}" else ""))
    }

    companion object {
        fun getInstance(project: Project): BlenderLogger = project.getService(BlenderLogger::class.java)

        fun log(project: Project?, message: String) {
            project?.let { getInstance(it).log(message) } ?: Logger.getInstance(BlenderLogger::class.java).info(message)
        }

        fun debug(project: Project?, message: String) {
            project?.let { getInstance(it).debug(message) } ?: Logger.getInstance(BlenderLogger::class.java).debug(message)
        }

        fun error(project: Project?, message: String, e: Throwable? = null) {
            project?.let { getInstance(it).error(message, e) } ?: Logger.getInstance(BlenderLogger::class.java).error(message, e)
        }
    }
}