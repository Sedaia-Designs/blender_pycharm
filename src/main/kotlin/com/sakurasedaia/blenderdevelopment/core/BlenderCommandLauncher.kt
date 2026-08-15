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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig

/** Describes a Blender `--command` invocation without including the mode option itself. */
internal data class BlenderCommandLaunchRequest(
  val commandArguments: List<String>,
  val blenderPath: String = "",
)

/** Builds and starts Blender's command-line command mode. */
@Service(Service.Level.PROJECT)
internal class BlenderCommandLauncher(project: Project) {
  private val projectConfig = ProjectConfig.getInstance(project)
  private val blenderLauncher = BlenderLauncher.getInstance(project)

  /** Starts Blender with `--command` followed by the supplied command arguments. */
  fun start(request: BlenderCommandLaunchRequest): OSProcessHandler {
    val arguments = BlenderLaunchArguments.command(
      logLevel = projectConfig.getBlenderLogLevel(),
      commandArguments = request.commandArguments,
    )
    return blenderLauncher.start(
      BlenderLaunchRequest(
        blenderPath = request.blenderPath,
        arguments = arguments,
      )
    )
  }

  companion object {
    /** Returns the project-scoped Blender command launcher. */
    fun getInstance(project: Project): BlenderCommandLauncher = project.service()
  }
}
