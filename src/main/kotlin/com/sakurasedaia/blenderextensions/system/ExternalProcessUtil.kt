package com.sakurasedaia.blenderextensions.system

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
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
                    logger?.log("Command failed with exit code ${handler.exitCode}: ${commandLine.commandLineString}")
                    break
                }
            } catch (e: Exception) {
                if (e is ProcessCanceledException) {
                    logger?.log("Execution cancelled by user")
                    throw e
                }
                if (!silentFailure) {
                    logger?.log("Failed to execute command: ${e.message}")
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
                logger?.log("Command failed with exit code ${handler.exitCode}: ${command.commandLineString}")
            }
            return handler.exitCode ?: -1
        } catch (e: Exception) {
            if (e is ProcessCanceledException) {
                logger?.log("Execution cancelled by user")
                throw e
            }
            if (!silentFailure) {
                logger?.log("Failed to execute command: ${e.message}")
            }
            return -1
        }
    }

    fun execAndGetOutput(command: GeneralCommandLine): com.intellij.execution.process.ProcessOutput {
        return com.intellij.execution.util.ExecUtil.execAndGetOutput(command)
    }
}
