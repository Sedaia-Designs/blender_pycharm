package com.sakurasedaia.blenderextensions.actions

import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.icons.BlenderIcons

class BlenderTemplateGroupFactory : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group = FileTemplateGroupDescriptor(LangManager.message("action.blender.menu.text"), BlenderIcons.Blender)
        group.addTemplate(FileTemplateGroupDescriptor("Component", BlenderIcons.Blender))
        group.addTemplate(FileTemplateGroupDescriptor("Module", BlenderIcons.Blender))
        return group
    }
}
