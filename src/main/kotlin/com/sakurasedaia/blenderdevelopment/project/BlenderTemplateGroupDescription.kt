package com.sakurasedaia.blenderdevelopment.project

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import com.sakurasedaia.blenderdevelopment.icons.BlenderIcons

class BlenderTemplateGroupDescription : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group = FileTemplateGroupDescriptor("Blender", BlenderIcons.BlenderColor)
        group.addTemplate(FileTemplateDescriptor("Main Script.py", BlenderIcons.PythonIcon))
        group.addTemplate(FileTemplateDescriptor("Component.py", BlenderIcons.PythonIcon))
        return group
    }
}