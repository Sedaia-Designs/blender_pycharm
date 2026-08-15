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

package com.sakurasedaia.blenderdevelopment.lib

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import com.sakurasedaia.blenderdevelopment.ui.IconBundle

/** Contributes Blender Python templates to IntelliJ's "New File" template groups. */
class TemplateGroupDescription : FileTemplateGroupDescriptorFactory {
  /**
   * Creates the Blender file template group descriptor shown in the "New File" dialog.
   *
   * @return descriptor containing the plugin's predefined Blender Python templates.
   */
  override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
    val group = FileTemplateGroupDescriptor("Blender", IconBundle.BlenderColor)
    group.addTemplate(FileTemplateDescriptor("Main Script.py", IconBundle.PythonIcon))
    group.addTemplate(FileTemplateDescriptor("Component.py", IconBundle.PythonIcon))
    return group
  }
}
