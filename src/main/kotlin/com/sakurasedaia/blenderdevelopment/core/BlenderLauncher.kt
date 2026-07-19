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
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.util.execution.ParametersListUtil
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.lib.PluginConfig
import com.sakurasedaia.blenderdevelopment.lib.ProjectConfig
import com.sakurasedaia.blenderdevelopment.lib.ProjectConfig.BlenderLogLevel
import com.sakurasedaia.blenderdevelopment.process.ExternalProcessBuilder

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
  val projectPath = project.basePath ?: ""
  val scratchPath = PathManager.getScratchDir()
  val pluginConfig = PluginConfig.getInstance().state
  val projectConfig = ProjectConfig.getInstance(project)
  
  
  companion object {
    fun getInstance(project: Project): Launcher = project.service()
  }

  fun createProcessHandler(args: BlenderArguments): OSProcessHandler {
    val blenderPath = resolveBlenderPath(args)
    val processBuilder = ExternalProcessBuilder(project)
    val processHandler = processBuilder.startProcessHandler(
      command = blenderPath,
      args = buildLaunchArguments(args),
      workDirectory = project.basePath,
      internalBinary = resolveMacInternalBinary(blenderPath),
    )
    ProcessTerminatedListener.attach(processHandler)
    return processHandler
  }

  fun startProcess(args: BlenderArguments) {
    logger.log(MessageBundle.message("notification.blender.launching"))
    try {
      val processHandler = createProcessHandler(args)
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

  private fun buildLaunchArguments(args: BlenderArguments): List<String> {
    val argList: MutableList<String> = mutableListOf()
    argList.addAll(buildDebugArguments(projectConfig.getBlenderLogLevel()))

    val workspaceRunArguments = projectConfig.getRunArguments().trim()
    if (workspaceRunArguments.isNotEmpty()) {
      argList.addAll(ParametersListUtil.parse(workspaceRunArguments))
    }

    if (args.debugger) {
      // TODO: Implement Debugger integration
    }

    if (args.scriptPath != null) {
      argList.add("--python")
      argList.add(args.scriptPath.toString())
    }

    argList.addAll(args.additionalArgs)
    return argList
  }

  private fun resolveMacInternalBinary(blenderPath: String): String? {
    return if (blenderPath.removeSuffix("/").endsWith(".app", ignoreCase = true)) "Blender" else null
  }

  private fun buildDebugArguments(logLevel: ProjectConfig.BlenderLogLevel): List<String> {
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
