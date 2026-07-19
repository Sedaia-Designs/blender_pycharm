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

package com.sakurasedaia.blenderdevelopment.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.sakurasedaia.blenderdevelopment.core.BlenderRuntimeCommandService
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle

internal abstract class BaseBlenderRuntimeAction(
  textKey: String,
  descriptionKey: String,
) : AnAction(
  MessageBundle.message(textKey),
  MessageBundle.message(descriptionKey),
  null,
), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val project = event.project
    if (project == null) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    event.presentation.isVisible = true
    val runtimeService = BlenderRuntimeCommandService.getInstance(project)
    event.presentation.isEnabled = runtimeService.hasActiveSession() && isActionApplicable(event)
  }

  open fun isActionApplicable(event: AnActionEvent): Boolean = true
}

internal class BlenderRuntimeRunScriptAction : BaseBlenderRuntimeAction(
  textKey = "action.blender.runtime.script.text",
  descriptionKey = "action.blender.runtime.script.description",
) {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    BlenderRuntimeCommandService.getInstance(project).sendRunScriptCommand()
  }

  override fun isActionApplicable(event: AnActionEvent): Boolean {
    val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
    return selectedFile?.extension.equals("py", ignoreCase = true)
  }
}

internal class BlenderRuntimeReloadAction : BaseBlenderRuntimeAction(
  textKey = "action.blender.runtime.reload.text",
  descriptionKey = "action.blender.runtime.reload.description",
) {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    BlenderRuntimeCommandService.getInstance(project).sendReloadCommand()
  }
}

internal class BlenderRuntimeStopAction : BaseBlenderRuntimeAction(
  textKey = "action.blender.runtime.stop.text",
  descriptionKey = "action.blender.runtime.stop.description",
) {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    BlenderRuntimeCommandService.getInstance(project).sendStopCommand()
  }
}
