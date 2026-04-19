package com.sakurasedaia.blenderextensions.listeners

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.sakurasedaia.blenderextensions.blender.services.BlenderService
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings

class BlenderFileSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            if (project.isDisposed) continue
            
            val fileIndex = ProjectFileIndex.getInstance(project)
            if (fileIndex.isInContent(file)) {
                val settings = BlenderSettings.getInstance(project).state
                if (settings.autoReload) {
                    BlenderService.getInstance(project).reloadExtension()
                }
            }
        }
    }
}
