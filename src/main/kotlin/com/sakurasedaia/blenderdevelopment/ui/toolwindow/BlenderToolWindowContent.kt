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
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.core.BlenderRuntimeCommandService
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.stubs.BlenderStubInstallationService
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Creates and owns the project-lifetime boundary for Project Blender Manager. */
class BlenderToolWindowContent(
    project: Project,
    onScanInstallations: (onCompleted: () -> Unit) -> Unit,
) : Disposable {
  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("Project Blender Manager"))
  private val view = BlenderToolWindowView(project)

  init {
    val logger = PluginLogger.getInstance(project)
    val notifications = NotificationModal.getInstance(project)
    val pluginConfig = PluginConfig.getInstance()
    val projectConfig = ProjectConfig.getInstance(project)
    BlenderToolWindowController(
        scope = scope,
        view = view,
        projectConfig = projectConfig,
        pluginConfig = pluginConfig,
        scanInstallations = onScanInstallations,
        installStubs = { blenderVersion ->
          scope.launch {
            BlenderStubInstallationService.getInstance(project).installForProject(blenderVersion)
          }
        },
        reloadAddon = BlenderRuntimeCommandService.getInstance(project)::sendReloadCommand,
        saveWorkspaceConfig = {
          scope.launch {
            runCatching { projectConfig.saveWorkspaceState() }
                .onSuccess { path ->
                  notifications.sendInfo(MessageBundle.message("notification.workspace.config.saved", path.fileName.toString()))
                }
                .onFailure { error ->
                  logger.warn(ErrorTypes.WORKSPACE_CONFIG_SAVE_FAILED.format(project.basePath), error)
                  notifications.sendError(MessageBundle.message("notification.workspace.config.save.failed"))
                }
          }
        },
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
