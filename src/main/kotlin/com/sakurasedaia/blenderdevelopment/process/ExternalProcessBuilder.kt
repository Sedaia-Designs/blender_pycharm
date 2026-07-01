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

import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.lib.SystemHelper
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ExternalProcessBuilder(val project: Project) {
  private val logger = PluginLogger.getInstance(project)

  data class ProcessExecutionResult(
    val command: String,
    val args: List<String>,
    val output: String,
    val exitCode: Int?,
    val cancelled: Boolean,
    val failure: Throwable? = null,
  ) {
    val firstLine: String
      get() = output.lineSequence().firstOrNull().orEmpty()
  }

  /**
   * Launches a process synchronously and captures combined standard output and standard error.
   *
   * @param command executable path or command name to run.
   * @param args command arguments.
   * @param shouldCancel optional callback polled during execution; when it returns `true`,
   * the process is terminated and the result is marked as cancelled.
   * @param internalBinary optional macOS app bundle binary name used when [command] points to an app bundle.
   * @return captured process execution details including output, exit code, and cancellation state.
   */
  fun launchAndCaptureOutput(
    command: String,
    vararg args: String,
    shouldCancel: (() -> Boolean)? = null,
    internalBinary: String? = null,
  ): ProcessExecutionResult {
    val argumentList = args.toMutableList()
    val processedCommand = prepareCommandForLaunch(command, argumentList, internalBinary)
    return try {
      val process = ProcessBuilder(processedCommand, *argumentList.toTypedArray()).redirectErrorStream(true).start()
      val outputFuture = AppExecutorUtil.getAppExecutorService().submit<String> {
        process.inputStream.bufferedReader().use { it.readText() }
      }

      val wasCancelled = waitForTermination(process, shouldCancel)
      val output = outputFuture.get()
      val exitCode = runCatching { process.exitValue() }.getOrNull()

      logger.debug("Process `${buildCommandString(command, argumentList)}` finished with exit code $exitCode")

      ProcessExecutionResult(
        command = command,
        args = argumentList,
        output = output,
        exitCode = exitCode,
        cancelled = wasCancelled,
      )
    } catch (exception: IOException) {
      logger.warn("Failed to execute `${buildCommandString(command, argumentList)}`", exception)
      ProcessExecutionResult(
        command = command,
        args = argumentList,
        output = "",
        exitCode = null,
        cancelled = false,
        failure = exception,
      )
    } catch (exception: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.warn("Interrupted while executing `${buildCommandString(command, argumentList)}`", exception)
      ProcessExecutionResult(
        command = command,
        args = argumentList,
        output = "",
        exitCode = null,
        cancelled = true,
        failure = exception,
      )
    }
  }

  /**
   * Launches a process on the application executor and returns a [Future] for its captured result.
   *
   * This method applies the same command preprocessing and cancellation semantics as
   * [launchAndCaptureOutput].
   *
   * @param command executable path or command name to run.
   * @param args command arguments.
   * @param shouldCancel optional callback polled during execution to request cancellation.
   * @param internalBinary optional macOS app bundle binary name used when [command] points to an app bundle.
   * @return a future that completes with the process execution result.
   */
  fun launchAndCaptureOutputAsync(
    command: String,
    vararg args: String,
    shouldCancel: (() -> Boolean)? = null,
    internalBinary: String? = null,
  ): Future<ProcessExecutionResult> {
    val argumentList = args.toMutableList()
    val processedCommand = prepareCommandForLaunch(command, argumentList, internalBinary)
    return AppExecutorUtil.getAppExecutorService().submit<ProcessExecutionResult> {
      launchAndCaptureOutput(processedCommand, argumentList, shouldCancel = shouldCancel)
    }
  }

  /**
   * Convenience overload of [launchAndCaptureOutput] that accepts a list of arguments.
   *
   * @param command executable path or command name to run.
   * @param args command arguments.
   * @param shouldCancel optional callback polled during execution to request cancellation.
   * @return captured process execution details including output, exit code, and cancellation state.
   */
  fun launchAndCaptureOutput(
    command: String,
    args: List<String>,
    shouldCancel: (() -> Boolean)? = null,
  ): ProcessExecutionResult {
    return launchAndCaptureOutput(command, *args.toTypedArray(), shouldCancel = shouldCancel)
  }

  /**
   * Convenience overload of [launchAndCaptureOutputAsync] that accepts a list of arguments.
   *
   * @param command executable path or command name to run.
   * @param args command arguments.
   * @param shouldCancel optional callback polled during execution to request cancellation.
   * @param internalBinary optional macOS app bundle binary name used when [command] points to an app bundle.
   * @return a future that completes with the process execution result.
   */
  fun launchAndCaptureOutputAsync(
    command: String,
    args: List<String>,
    shouldCancel: (() -> Boolean)? = null,
    internalBinary: String? = null,
  ): Future<ProcessExecutionResult> {
    val argumentList = args.toMutableList()
    val processedCommand = prepareCommandForLaunch(command, argumentList, internalBinary)
    return AppExecutorUtil.getAppExecutorService().submit<ProcessExecutionResult> {
      launchAndCaptureOutput(processedCommand, argumentList, shouldCancel = shouldCancel)
    }
  }

  private fun waitForTermination(process: Process, shouldCancel: (() -> Boolean)?): Boolean {
    while (true) {
      if (process.waitFor(PROCESS_WAIT_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
        return false
      }

      if (shouldCancel?.invoke() == true) {
        process.destroy()
        if (!process.waitFor(PROCESS_DESTROY_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS)) {
          process.destroyForcibly()
          process.waitFor()
        }
        return true
      }
    }
  }

  private fun buildCommandString(command: String, args: List<String>): String {
    return listOf(command).plus(args).joinToString(" ")
  }

  private fun prepareCommandForLaunch(
    command: String,
    argumentList: MutableList<String>,
    internalBinary: String? = null,
  ): String {
    var processedCommand = command
    if (SystemHelper.getSysInfo.osName == "macos") {
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
