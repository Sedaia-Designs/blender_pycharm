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

package com.sakurasedaia.blenderdevelopment.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.execution.ParametersListUtil
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.util.BlenderBootstrapScriptCleanup
import com.sakurasedaia.blenderdevelopment.util.BlenderRuntimeResources
import com.sakurasedaia.blenderdevelopment.util.PluginResources
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import com.sakurasedaia.blenderdevelopment.process.ExternalProcessBuilder

import java.nio.file.Files
import java.nio.file.Path

data class BlenderArguments(
  val blenderPath: String,
  val scriptPath: Path? = null,
  val additionalArgs: List<String> = mutableListOf(),
  val debugger: Boolean = false
)

@Service(Service.Level.PROJECT)
internal class Launcher(private val project: Project) {
  val logger = PluginLogger.getInstance(project)
  val notifModal = NotificationModal.getInstance(project)
  val projectConfig = ProjectConfig.getInstance(project)
  
  
  companion object {
    fun getInstance(project: Project): Launcher = project.service()
  }

  fun startBlender(args: BlenderArguments): OSProcessHandler {
    val blenderPath = resolveBlenderPath(args)
    val launchCommand = buildLaunchCommand(args)
    val processBuilder = ExternalProcessBuilder(project)
    val processHandler = processBuilder.startProcessHandler(
      command = blenderPath,
      args = launchCommand.arguments,
      workDirectory = project.basePath,
      internalBinary = resolveMacInternalBinary(blenderPath),
    )
    if (launchCommand.generatedBootstrapScript != null) {
      processHandler.addProcessListener(object : ProcessListener {
        override fun processTerminated(event: ProcessEvent) {
          BlenderBootstrapScriptCleanup.cleanupScript(
            path = launchCommand.generatedBootstrapScript,
            debugLog = logger::debug,
            warnLog = logger::warn,
          )
        }
      })
    }
    ProcessTerminatedListener.attach(processHandler)
    return processHandler
  }

  fun startProcess(args: BlenderArguments) {
    logger.log(MessageBundle.message("notification.blender.launching"))
    try {
      val processHandler = startBlender(args)
      processHandler.startNotify()
    } catch (e: Exception) {
      logger.error(ErrorTypes.BLENDER_LAUNCH_ERROR, e)
      notifModal.sendError(
        e.message ?: "",
        MessageBundle.message("notification.blender.launching.error", "")
      )
    }
  }

  private fun resolveBlenderPath(args: BlenderArguments): String {
    val blenderPath = args.blenderPath.ifBlank { projectConfig.getBlenderPath().trim() }
    require(blenderPath.isNotEmpty()) {
      MessageBundle.message("run.configuration.blender.launch.error.blender.path.empty")
    }
    return blenderPath
  }

  private fun buildLaunchCommand(args: BlenderArguments): LaunchCommand {
    val argList: MutableList<String> = mutableListOf()
    argList.addAll(buildDebugArguments(projectConfig.getBlenderLogLevel()))

    val workspaceRunArguments = projectConfig.getRunArguments().trim()
    if (workspaceRunArguments.isNotEmpty()) {
      argList.addAll(ParametersListUtil.parse(workspaceRunArguments))
    }

    val debugScriptPath = if (args.debugger) createScratchDebugLaunchScript() else null
    val scriptPath = args.scriptPath ?: debugScriptPath
    if (scriptPath != null) {
      argList.add("--python")
      argList.add(scriptPath.toString())
    }

    argList.addAll(args.additionalArgs)
    return LaunchCommand(
      arguments = argList,
      generatedBootstrapScript = debugScriptPath,
    )
  }

  private fun createScratchDebugLaunchScript(): Path {
    val scratchDirPath = PathManager.getScratchDir()
    require(scratchDirPath.toString().isNotBlank()) {
      MessageBundle.message("run.configuration.blender.launch.error.scratch.path.empty")
    }

    Files.createDirectories(scratchDirPath)

    val scratchVfsDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(scratchDirPath)
      ?: throw IllegalStateException(
        MessageBundle.message("run.configuration.blender.launch.error.scratch.directory.missing", scratchDirPath.toString())
      )

    val includeDirectoryPath = BlenderRuntimeResources.ensureRuntimeExtracted()
    val scriptFileName = BlenderBootstrapScriptCleanup.newScriptFileName()
    val projectBasePath = project.basePath ?: ""
    val sourceFolder = projectConfig.getSourceFolder()
    val addonSymlinkName = projectConfig.getAddonSymlinkName()

    WriteAction.run<Throwable> {
      PluginResources.createFromTemplate(
        project = project,
        name = scriptFileName,
        template = "BlenderRuntimeLaunch",
        destination = scratchVfsDirectory,
        internal = true,
        "includeDirLiteral" to toPythonStringLiteral(includeDirectoryPath.toString()),
        "projectPathLiteral" to toPythonStringLiteral(projectBasePath),
        "sourceFolderLiteral" to toPythonStringLiteral(sourceFolder),
        "addonSymlinkNameLiteral" to toPythonStringLiteral(addonSymlinkName),
      )
    }

    return Path.of(scratchVfsDirectory.path, scriptFileName)
  }

  private fun toPythonStringLiteral(value: String): String {
    val builder = StringBuilder(value.length + 8)
    value.forEach { ch ->
      when (ch) {
        '\\' -> builder.append("\\\\")
        '\'' -> builder.append("\\'")
        '\n' -> builder.append("\\n")
        '\r' -> builder.append("\\r")
        '\t' -> builder.append("\\t")
        else -> builder.append(ch)
      }
    }
    return builder.toString()
  }

  private fun resolveMacInternalBinary(blenderPath: String): String? {
    return if (blenderPath.removeSuffix("/").endsWith(".app", ignoreCase = true)) "Blender" else null
  }

  private fun buildDebugArguments(logLevel: BlenderLogLevel): List<String> {
    return when (logLevel) {
      BlenderLogLevel.FATAL -> listOf("--log-level", "fatal")
      BlenderLogLevel.ERROR -> listOf("--log-level", "error")
      BlenderLogLevel.WARNING -> listOf("--log-level", "warning")
      BlenderLogLevel.INFO -> listOf("--log-level", "info")
      BlenderLogLevel.DEBUG -> listOf("--log-level", "debug")
      BlenderLogLevel.TRACE -> listOf("--log-level", "trace")
    }
  }
}
  private data class LaunchCommand(
    val arguments: List<String>,
    val generatedBootstrapScript: Path? = null,
  )
