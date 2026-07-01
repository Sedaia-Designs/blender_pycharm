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

package com.sakurasedaia.blenderdevelopment.blender

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.application.PathManager
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.lib.PluginConfig
import com.sakurasedaia.blenderdevelopment.lib.ProjectConfig

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
  val projectConfig = ProjectConfig.getInstance(project).state
  
  
  companion object {
    fun getInstance(project: Project): Launcher = project.service()
  }
  
  fun startProcess(args: BlenderArguments) {
    if (args.blenderPath.isEmpty() && projectConfig.blenderPath.isEmpty()) {
      // Prevent process execution if neither the project nor the run configuration config have a Blender path set.
      return
    }
    
    
    val argList: MutableList<String> = mutableListOf()
    if (args.debugger) {
      // TODO: Implement Debugger integration
    }
    
    if (args.scriptPath != null) {
      argList.add("--python")
      argList.add(args.scriptPath.toString())
    }
    
    logger.log(MessageBundle.message("notification.blender.launching"))
    
    try {
    
    } catch (e: Exception) {
      logger.error(ErrorTypes.BLENDER_LAUNCH_ERROR, e)
      notifModal.sendError(e.message ?: "",
      MessageBundle.message("notification.blender.launching.error", "")
      )
    }
  }
  
  
}