package com.sakurasedaia.blenderextensions.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for the Blender Tool Window.
 * 
 * The UI Tool Window module follows this progression:
 * 1. [BlenderToolWindowFactory] registers and creates the tool window.
 * 2. [BlenderToolWindowContent] manages the main UI components and layout.
 * 3. Interaction with buttons (e.g., Scan, Clear Sandbox) is delegated to [BlenderService].
 * 4. Real-time updates from [BlenderDownloader] or [BlenderService] are reflected in the UI.
 */
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
