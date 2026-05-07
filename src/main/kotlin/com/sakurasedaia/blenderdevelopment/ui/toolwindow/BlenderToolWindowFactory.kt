package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class BlenderToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(
            BlenderToolWindowContent(project).getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
}