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

package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.sakurasedaia.blenderdevelopment.config.BlenderPluginConfig
import javax.swing.JComponent

/** Settings panel content for global Blender plugin configuration. */
internal class BlenderSettingsContent {
    private lateinit var templateCheckBox: JBCheckBox

    private val root = panel {
        group(MessageBundle.message("ui.settings.group.template.title")) {
            row {
                checkBox(MessageBundle.message("ui.settings.group.template.enabled"))
                    .applyToComponent { templateCheckBox = this }
            }
        }
    }

    internal fun component(): JComponent = root

    internal fun reset(config: BlenderPluginConfig) {
        templateCheckBox.isSelected = config.getMyBool()
    }

    internal fun isModified(config: BlenderPluginConfig): Boolean {
        return templateCheckBox.isSelected != config.getMyBool()
    }

    internal fun apply(config: BlenderPluginConfig) {
        config.setMyBool(templateCheckBox.isSelected)
    }
}
