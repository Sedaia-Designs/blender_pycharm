package com.sakurasedaia.blenderextensions.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderService
import com.sakurasedaia.blenderextensions.icons.BlenderIcons

class ReloadExtensionAction : AnAction(
    LangManager.messagePointer("action.reload.extension.text"),
    LangManager.messagePointer("action.reload.extension.description"),
    BlenderIcons.Refresh
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        BlenderService.getInstance(project).reloadExtension()
    }
}
