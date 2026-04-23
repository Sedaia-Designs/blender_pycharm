package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

object ExternalProcessUtil {

    fun executeGroup(commandLines: List<GeneralCommandLine>, silentFailure: Boolean = false, logger: BlenderLogger? = null) {
        for (commandLine in commandLines) {
            try {
                val handler = OSProcessHandler(commandLine)
                handler.startNotify()
                waitWithProgress(handler)

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
            waitWithProgress(handler)

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

    fun execAndGetOutput(command: GeneralCommandLine): ProcessOutput {
        val output = ProcessOutput()
        val handler = OSProcessHandler(command)
        handler.addProcessListener(CapturingProcessAdapter(output))
        handler.startNotify()
        waitWithProgress(handler)
        return output
    }

    private fun waitWithProgress(handler: OSProcessHandler) {
        val semaphore = Semaphore(0)
        val listener = object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                semaphore.release()
            }
        }
        handler.addProcessListener(listener)

        try {
            while (true) {
                ProgressManager.checkCanceled()
                if (semaphore.tryAcquire(500, TimeUnit.MILLISECONDS)) {
                    break
                }
            }
        } catch (e: ProcessCanceledException) {
            handler.destroyProcess()
            throw e
        } finally {
            handler.removeProcessListener(listener)
        }
    }
}
