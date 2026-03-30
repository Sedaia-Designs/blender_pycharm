package com.sakurasedaia.blenderextensions.system

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderLogger

object ExternalProcessUtil {

    fun executeGroup(commandLines: List<GeneralCommandLine>, silentFailure: Boolean = false, logger: BlenderLogger? = null) {
        for (commandLine in commandLines) {
            try {
                val handler = OSProcessHandler(commandLine)
                handler.startNotify()
                while (!handler.waitFor(100)) {
                    ProgressManager.checkCanceled()
                }
                if (handler.exitCode != 0 && !silentFailure) {
                    logger?.log(LangManager.message("log.external.command.failed", handler.exitCode ?: -1, commandLine.commandLineString))
                    break
                }
            } catch (e: Exception) {
                if (e is ProcessCanceledException) {
                    logger?.log(LangManager.message("log.external.execution.cancelled"))
                    throw e
                }
                if (!silentFailure) {
                    logger?.log(LangManager.message("log.external.execution.failed", e.message ?: "Unknown error"))
                }
                break
            }
        }
    }

    fun executeCommand(command: GeneralCommandLine, silentFailure: Boolean = false, logger: BlenderLogger? = null): Int {
        try {
            val handler = OSProcessHandler(command)
            handler.startNotify()
            while (!handler.waitFor(100)) {
                ProgressManager.checkCanceled()
            }
            if (handler.exitCode != 0 && !silentFailure) {
                logger?.log(LangManager.message("log.external.command.failed", handler.exitCode ?: -1, command.commandLineString))
            }
            return handler.exitCode ?: -1
        } catch (e: Exception) {
            if (e is ProcessCanceledException) {
                logger?.log(LangManager.message("log.external.execution.cancelled"))
                throw e
            }
            if (!silentFailure) {
                logger?.log(LangManager.message("log.external.execution.failed", e.message ?: "Unknown error"))
            }
            return -1
        }
    }

    fun execAndGetOutput(command: GeneralCommandLine): com.intellij.execution.process.ProcessOutput {
        return com.intellij.execution.util.ExecUtil.execAndGetOutput(command)
    }
}
