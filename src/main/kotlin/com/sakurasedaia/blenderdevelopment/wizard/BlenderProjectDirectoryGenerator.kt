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

package com.sakurasedaia.blenderdevelopment.wizard

import com.jetbrains.python.newProjectWizard.PyV3ProjectBaseGenerator
import com.sakurasedaia.blenderdevelopment.ui.IconBundle
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import javax.swing.Icon

/** Blender project generator backed by PyCharm's native Python environment workflow. */
class BlenderProjectDirectoryGenerator : PyV3ProjectBaseGenerator<BlenderProjectSettings>(
    typeSpecificSettings = BlenderProjectSettings(),
    typeSpecificUI = BlenderProjectUI,
    _newProjectName = MessageBundle.message("ui.project.wizard.default.project.name"),
    supportsNotEmptyModuleStructure = false,
) {
    /**
     * Returns the localized project type shown by PyCharm.
     *
     * @return Blender project type name.
     */
    override fun getName(): String = MessageBundle.message("ui.project.wizard.template.title")

    /**
     * Returns the icon shown for the Blender project type.
     *
     * @return Blender color icon.
     */
    override fun getLogo(): Icon = IconBundle.BlenderColor
}
