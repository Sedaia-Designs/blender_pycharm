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

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.process.ExternalProcessBuilder
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle

/** Describes one transparent invocation of the configured Blender executable. */
internal data class BlenderLaunchRequest(
  val blenderPath: String = "",
  val arguments: List<String> = emptyList(),
  val environment: Map<String, String> = emptyMap(),
  val workingDirectory: String? = null,
)

/** Starts Blender processes without adding a Python script or another launch mode. */
@Service(Service.Level.PROJECT)
internal class BlenderLauncher(private val project: Project) {
  private val projectConfig = ProjectConfig.getInstance(project)
  private val pluginConfig = PluginConfig.getInstance()

  /** Starts the Blender process described exactly by [request]. */
  fun start(request: BlenderLaunchRequest): OSProcessHandler {
    val blenderPath = resolveBlenderPath(request.blenderPath)
    val processHandler = ExternalProcessBuilder(project).startProcessHandler(
      command = blenderPath,
      args = request.arguments,
      workDirectory = request.workingDirectory ?: project.basePath,
      environment = buildEnvironment(request.environment),
      internalBinary = resolveMacInternalBinary(blenderPath),
    )
    ProcessTerminatedListener.attach(processHandler)
    return processHandler
  }

  private fun resolveBlenderPath(requestedPath: String): String {
    val blenderPath = requestedPath.ifBlank { projectConfig.getBlenderPath().trim() }
    require(blenderPath.isNotEmpty()) {
      MessageBundle.message("run.configuration.blender.launch.error.blender.path.empty")
    }
    return blenderPath
  }

  private fun buildEnvironment(overrides: Map<String, String>): Map<String, String> {
    val environment = pluginConfig.getGlobalEnvironmentVariables()
      .filterKeys(String::isNotBlank)
      .filterValues(String::isNotBlank)
      .toMutableMap()
    environment.putAll(
      projectConfig.getEnvironmentVariables()
        .filterKeys(String::isNotBlank)
        .filterValues(String::isNotBlank)
    )
    environment.putAll(overrides)
    return environment
  }

  private fun resolveMacInternalBinary(blenderPath: String): String? {
    return if (blenderPath.removeSuffix("/").endsWith(".app", ignoreCase = true)) "Blender" else null
  }

  companion object {
    /** Returns the project-scoped raw Blender launcher. */
    fun getInstance(project: Project): BlenderLauncher = project.service()
  }
}
