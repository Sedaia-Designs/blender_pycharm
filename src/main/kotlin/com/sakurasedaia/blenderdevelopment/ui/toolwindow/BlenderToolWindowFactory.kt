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

package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.sakurasedaia.blenderdevelopment.lib.services.SettingsInstallationScanService

/** Registers and populates the Blender tool window content. */
class BlenderToolWindowFactory : ToolWindowFactory {
  /**
   * Creates the tool window tab content for a project.
   *
   * @param project current project instance.
   * @param toolWindow target tool window container.
   * @return `Unit`.
   */
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val toolWindowContent =
        BlenderToolWindowContent(
            project = project,
            onScanInstallations = { onCompleted ->
              SettingsInstallationScanService.getInstance()
                  .scanInstallations(
                      projectOverride = project,
                      onComplete = { onCompleted() },
                  )
            },
        )
    val content =
        ContentFactory.getInstance()
            .createContent(
                toolWindowContent.getContent(),
                "",
                false,
            )
            .apply {
              setDisposer(toolWindowContent)
            }
    toolWindow.contentManager.addContent(content)
  }
}
