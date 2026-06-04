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

package com.sakurasedaia.blenderdevelopment.system

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JComponent
import kotlin.coroutines.resume

/**
 * Result of running an external tool.
 *
 * @param exitCode Process exit code (0 == success for most CLIs including `uv`).
 * @param component The console UI component; safe to embed in a ToolWindow immediately, output streams live.
 */
data class ExternalToolResult(val exitCode: Int, val component: JComponent)

/** Executes external commands and provides live IntelliJ console output. */
class ExternalProcessUtil(private val project: Project) {

    /**
     * Executes an external tool asynchronously via Kotlin Coroutines.
     * This ensures the Event Dispatch Thread (EDT) and Read Locks are never blocked.
     *
     * The console UI is created on the EDT and returned together with the eventual exit code.
     * Output (including ANSI color from `uv`/`pip`/`python`) streams into the console while the
     * process runs. Cancelling the calling coroutine destroys the underlying process.
     *
     * @param executable The command or path to the executable (e.g., "git", "pip", "uv").
     * @param arguments The list of arguments to pass to the executable.
     * @param workingDir The directory where the command should execute.
     * @param env Extra environment variables merged on top of the inherited shell environment.
     * @param parentDisposable Lifetime owner for the ConsoleView (e.g. a ToolWindow content disposable).
     * @return [ExternalToolResult] with exit code and the embeddable console component.
     */
    suspend fun runExternalToolAsync(
        executable: String,
        arguments: List<String>,
        workingDir: String,
        env: Map<String, String> = emptyMap(),
        parentDisposable: Disposable = project,
    ): ExternalToolResult {

        val workDir = File(workingDir).takeIf { it.isDirectory }
            ?: error("Working directory does not exist: $workingDir")

        val resolved = resolveExecutable(executable)

        // 1. Configure the command line execution
        val commandLine = GeneralCommandLine(resolved)
            .withParameters(arguments)
            .withWorkDirectory(workDir)
            .withCharset(Charsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(buildMap {
                // Force colored output from uv / pip / python tooling even though stdout is a pipe.
                put("FORCE_COLOR", "1")
                put("CLICOLOR_FORCE", "1")
                put("PY_COLORS", "1")
                // Ensure clean UTF-8 I/O across platforms (esp. Windows + non-ASCII paths).
                put("PYTHONIOENCODING", "utf-8")
                put("PYTHONUTF8", "1")
                // Stream Python child output promptly instead of buffering.
                put("PYTHONUNBUFFERED", "1")
                putAll(env)
            })

        // 2/3/4. Create the visual Console UI component and bind it to the process handler -- on EDT.
        val (consoleView, processHandler) = withContext(Dispatchers.EDT) {
            val view: ConsoleView = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project).console
            Disposer.register(parentDisposable, view) // prevent leaks on repeated invocations
            val handler = ColoredProcessHandler(commandLine)
            view.attachToProcess(handler)
            view to handler
        }

        // 5. Add custom logging hooks for backend tracking
        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                // region ===== REVIEW: only log stderr to avoid log spam from uv progress bars / ANSI on stdout =====
                if (outputType === ProcessOutputType.STDERR) {
                    PluginLogger.debug(project, "[stderr] ${event.text.trimEnd()}")
                }
                // endregion ===== REVIEW =====
            }

            override fun processTerminated(event: ProcessEvent) {
                PluginLogger.log(
                    project,
                    "External process '$executable ${arguments.joinToString(" ")}' " +
                        "finished with exit code: ${event.exitCode}"
                )
            }
        })

        // 6/7. Start the process and suspend until termination -- cancellation kills the process.
        val exitCode = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                processHandler.addProcessListener(object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) {
                        if (cont.isActive) cont.resume(event.exitCode)
                    }
                })
                cont.invokeOnCancellation { processHandler.destroyProcess() }
                processHandler.startNotify()
            }
        }
        return ExternalToolResult(exitCode, consoleView.component)
    }

    /**
     * Resolves an executable name by:
     *  1. Returning it as-is if it's already an absolute path.
     *  2. Looking it up via the IDE's PATH helper.
     *  3. Falling back to common per-user install locations (`~/.local/bin`, `~/.cargo/bin`)
     *     which `uv`'s official installer uses but which are often missing from the IDE's
     *     inherited PATH on macOS when the IDE is launched from Finder/Spotlight.
     *
     * @param name executable name or path.
     * @return resolved executable path, or original [name] if unresolved.
     */
    private fun resolveExecutable(name: String): String {
        if (File(name).isAbsolute) return name
        PathEnvironmentVariableUtil.findInPath(name)?.let { return it.absolutePath }
        val home = System.getProperty("user.home")
        val candidates = if (SystemInfo.isWindows) {
            listOf("$home\\.local\\bin\\$name.exe", "$home\\.cargo\\bin\\$name.exe")
        } else {
            listOf("$home/.local/bin/$name", "$home/.cargo/bin/$name")
        }
        return candidates.firstOrNull { File(it).canExecute() } ?: name
    }
}
