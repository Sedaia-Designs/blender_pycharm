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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.sakurasedaia.blenderdevelopment.core.BlenderRuntimeCommandService
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import kotlinx.coroutines.cancel
import javax.swing.JComponent

/** Creates and owns the project-lifetime boundary for Project Blender Manager. */
class BlenderToolWindowContent(
  project: Project,
  onScanInstallations: (onCompleted: () -> Unit) -> Unit,
) : Disposable {
  private val scope = (project as ComponentManagerEx)
    .getCoroutineScope()
    .childScope("Project Blender Manager")
  private val view = BlenderToolWindowView(project)

  init {
    val logger = PluginLogger.getInstance(project)
    val pluginConfig = PluginConfig.getInstance()
    BlenderToolWindowController(
      scope = scope,
      view = view,
      projectConfig = ProjectConfig.getInstance(project),
      pluginConfig = pluginConfig,
      scanInstallations = onScanInstallations,
      detectedInstallations = pluginConfig::getDetectedBlenderInstalls,
      reloadAddon = BlenderRuntimeCommandService.getInstance(project)::sendReloadCommand,
      logAutosave = { fieldName ->
        logger.debug("Autosaved `$fieldName` from Blender tool window.")
      },
    )
  }

  /** Returns the root Swing component for the Blender tool window. */
  fun getContent(): JComponent = view.component

  override fun dispose() {
    scope.cancel()
  }
}
