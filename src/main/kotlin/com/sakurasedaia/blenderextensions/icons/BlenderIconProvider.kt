package com.sakurasedaia.blenderextensions.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDirectory
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import javax.swing.Icon

class BlenderIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            val name = element.name
            if (name.endsWith(".blend")) {
                return BlenderIcons.BlendFile
            }
        } else if (element is PsiDirectory) {
            val project = element.project
            val path = element.virtualFile.path
            if (BlenderSettings.getInstance(project).isSourceFolder(path)) {
                return BlenderIcons.AddonSrcFolder
            }
        }
        return null
    }
}
