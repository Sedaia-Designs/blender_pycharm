package com.sakurasedaia.blenderextensions.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.icons.BlenderIcons

class BlenderCreatePackageAction : CreateFileFromTemplateAction(
    LangManager.messagePointer("action.create.blender.package.text"),
    LangManager.messagePointer("action.create.blender.package.description"),
    BlenderIcons.Blender
), DumbAware {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(LangManager.message("dialog.title.new.blender.package"))
            .addKind("Blender Package", BlenderIcons.Blender, "Blender Package")
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
        LangManager.message("action.create.blender.package.text")

    override fun createFile(name: String, templateName: String, dir: PsiDirectory): PsiFile? {
        val project = dir.project
        val template = FileTemplateManager.getInstance(project).getInternalTemplate(templateName)
        
        // Create the subdirectory
        val subDir = dir.createSubdirectory(name)
        
        // Create __init__.py inside the subdirectory using the template
        return FileTemplateUtil.createFromTemplate(template, "__init__", null, subDir) as? PsiFile
    }
}