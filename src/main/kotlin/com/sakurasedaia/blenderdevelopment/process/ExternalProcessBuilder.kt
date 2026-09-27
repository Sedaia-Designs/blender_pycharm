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

package com.sakurasedaia.blenderdevelopment.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class ExternalProcessBuilder(val project: Project) {
    private val logger = PluginLogger.getInstance(project)

    private enum class ProcessResult {
        COMPLETED,
        CANCELLED,
        TIMED_OUT,
    }

    data class ProcessExecutionResult(
        val command: String,
        val args: List<String>,
        val output: String,
        val exitCode: Int?,
        val cancelled: Boolean = false,
        val failure: Throwable? = null,
        val timedOut: Boolean = false,
    ) {
        val firstLine: String
            get() = output.lineSequence().firstOrNull().orEmpty()
    }

    /**
     * Launches a process synchronously and captures combined standard output and standard error.
     *
     * @param command executable path or command name to run.
     * @param args command arguments.
     * @param shouldCancel optional callback polled during execution; when it returns `true`, the process is terminated and the result is
     *   marked as cancelled.
     * @param internalBinary optional macOS app bundle binary name used when [command] points to an app bundle.
     * @param timeout optional maximum process runtime; `null` or [Duration.ZERO] disables the timeout.
     * @return captured process execution details including output, exit code, and cancellation state.
     */
    fun launchAndCaptureOutput(
        command: String,
        vararg args: String,
        shouldCancel: (() -> Boolean)? = null,
        internalBinary: String? = null,
        timeout: Duration? = null,
    ): ProcessExecutionResult {
        require(timeout == null || !timeout.isNegative) {
            "Timeout must be zero or greater"
        }

        // Normalize zero to the disabled-timeout representation.
        val actualTimeout = if (timeout == Duration.ZERO) null else timeout

        val argumentList = args.toMutableList()
        val processedCommand = prepareCommandForLaunch(command, argumentList, internalBinary)

        val result: ProcessExecutionResult =
            try {
                val process = ProcessBuilder(processedCommand, *argumentList.toTypedArray()).redirectErrorStream(true).start()
                val outputFuture =
                    AppExecutorUtil.getAppExecutorService().submit<String> {
                        process.inputStream.bufferedReader().use { it.readText() }
                    }

                try {
                    val processResult = waitForTermination(process, shouldCancel, actualTimeout)

                    if (processResult != ProcessResult.COMPLETED) {
                        killProcess(process)
                    }

                    // TODO(V1): Bound or cancel output collection after termination so inherited stdout cannot block this worker
                    // indefinitely.
                    val output = outputFuture.get()
                    val exitCode = runCatching { process.exitValue() }.getOrNull()

                    logger.debug("Process `${buildCommandString(command, argumentList)}` finished with exit code $exitCode")

                    ProcessExecutionResult(
                        command = command,
                        args = argumentList,
                        exitCode = exitCode,
                        output = output,
                        cancelled = processResult == ProcessResult.CANCELLED,
                        timedOut = processResult == ProcessResult.TIMED_OUT,
                    )
                } catch (exception: InterruptedException) {
                    killProcess(process)
                    throw exception
                }
            } catch (exception: IOException) {
                logger.warn(ErrorTypes.PROCESS_EXECUTION_FAILED.format(buildCommandString(command, argumentList)), exception)
                ProcessExecutionResult(
                    command = command,
                    args = argumentList,
                    output = "",
                    exitCode = null,
                    failure = exception,
                )
            } catch (exception: InterruptedException) {
                logger.warn(ErrorTypes.PROCESS_EXECUTION_INTERRUPTED.format(buildCommandString(command, argumentList)), exception)
                Thread.currentThread().interrupt()
                ProcessExecutionResult(
                    command = command,
                    args = argumentList,
                    output = "",
                    exitCode = null,
                    cancelled = true,
                    failure = exception,
                )
            } catch (exception: ExecutionException) {
                logger.warn(ErrorTypes.PROCESS_EXECUTION_FAILED.format(buildCommandString(command, argumentList)), exception)
                ProcessExecutionResult(
                    command = command,
                    args = argumentList,
                    output = "",
                    exitCode = null,
                    failure = exception,
                )
            }
        return result
    }

    /**
     * Convenience overload of [launchAndCaptureOutput] that accepts a list of arguments.
     *
     * @param command executable path or command name to run.
     * @param args command arguments.
     * @param shouldCancel optional callback polled during execution to request cancellation.
     * @param internalBinary optional macOS app bundle binary name used when [command] points to an app bundle.
     * @param timeout optional maximum process runtime; `null` or [Duration.ZERO] disables the timeout.
     * @return captured process execution details including output, exit code, and cancellation state.
     */
    fun launchAndCaptureOutput(
        command: String,
        args: List<String>,
        shouldCancel: (() -> Boolean)? = null,
        internalBinary: String? = null,
        timeout: Duration? = null,
    ): ProcessExecutionResult {
        return launchAndCaptureOutput(
            command,
            *args.toTypedArray(),
            shouldCancel = shouldCancel,
            internalBinary = internalBinary,
            timeout = timeout,
        )
    }

    /**
     * Builds and starts an IDE-managed process handler for run/debug flows.
     *
     * @param command executable path or command name to run.
     * @param args command arguments.
     * @param workDirectory optional working directory for process execution.
     * @param internalBinary optional macOS app bundle binary name used when [command] points to an app bundle.
     * @return started [OSProcessHandler].
     */
    fun startProcessHandler(
        command: String,
        args: List<String> = emptyList(),
        workDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        internalBinary: String? = null,
    ): OSProcessHandler {
        val argumentList = args.toMutableList()
        val processedCommand = prepareCommandForLaunch(command, argumentList, internalBinary)
        val commandLine = GeneralCommandLine(processedCommand).withWorkDirectory(workDirectory)
        commandLine.environment.putAll(environment)
        if (argumentList.isNotEmpty()) {
            commandLine.addParameters(argumentList)
        }
        return OSProcessHandler(commandLine)
    }

    /**
     * Waits for the termination of a specified process, with optional cancellation and timeout checks.
     *
     * @param process the process to monitor for termination.
     * @param shouldCancel an optional callback that determines whether the process should be cancelled. If the callback returns `true`, the
     *   process is terminated.
     * @param timeout an optional duration to wait for the process to complete.
     * @return the mode of termination, indicating whether the process completed normally, was cancelled, or timed out.
     */
    private fun waitForTermination(
        process: Process,
        shouldCancel: (() -> Boolean)?,
        timeout: Duration? = null,
    ): ProcessResult {
        val startedAtNanos = System.nanoTime()

        while (true) {
            if (process.waitFor(PROCESS_WAIT_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                return ProcessResult.COMPLETED
            }

            if (shouldCancel?.invoke() == true) {
                return ProcessResult.CANCELLED
            }

            val elapsed = Duration.ofNanos(System.nanoTime() - startedAtNanos)
            if (timeout != null && elapsed >= timeout) {
                return ProcessResult.TIMED_OUT
            }
        }
    }

    private fun killProcess(process: Process) {
        val descendants = process.descendants().use { stream -> stream.toList().asReversed() }

        descendants.forEach { it.destroy() }
        process.destroy()
        if (!process.waitFor(PROCESS_DESTROY_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
    }

    private fun buildCommandString(command: String, args: List<String>): String {
        // TODO(V1): Redact secret-bearing arguments such as --access-token before writing command diagnostics.
        return listOf(command).plus(args).joinToString(" ")
    }

    private fun prepareCommandForLaunch(
        command: String,
        argumentList: MutableList<String>,
        internalBinary: String? = null,
    ): String {
        var processedCommand = command
        if (SystemInfo.getSysInfo.osName == "macos") {
            if (internalBinary != null) {
                processedCommand = java.nio.file.Path.of(command, "Contents", "MacOS", internalBinary).toString()
            } else if (command.removeSuffix("/").endsWith(".app", ignoreCase = true)) {
                processedCommand = "open"
                argumentList.add(0, command)
                argumentList.add(1, "--args")
            }
        }
        return processedCommand
    }

    companion object {
        private const val PROCESS_WAIT_INTERVAL_MS = 200L
        private const val PROCESS_DESTROY_GRACE_PERIOD_MS = 2_000L
    }
}
