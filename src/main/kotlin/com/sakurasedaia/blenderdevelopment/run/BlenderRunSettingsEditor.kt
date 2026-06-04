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

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPanel

/** Basic editor UI for Blender run configuration settings. */
class BlenderRunSettingsEditor : SettingsEditor<BlenderRunConfiguration>() {
    private lateinit var scriptPathField: JBTextField
    private lateinit var workingDirectoryField: JBTextField
    private lateinit var argumentsField: JBTextField

    private val root: JPanel = panel {
        row("Script Path") {
            textField()
                .align(AlignX.FILL)
                .applyToComponent { scriptPathField = this }
        }
        row("Working Directory") {
            textField()
                .align(AlignX.FILL)
                .applyToComponent { workingDirectoryField = this }
        }
        row("Arguments") {
            textField()
                .align(AlignX.FILL)
                .applyToComponent { argumentsField = this }
        }
    }

    override fun resetEditorFrom(configuration: BlenderRunConfiguration) {
        scriptPathField.text = configuration.data.scriptPath
        workingDirectoryField.text = configuration.data.workingDirectory
        argumentsField.text = configuration.data.arguments
    }

    override fun applyEditorTo(configuration: BlenderRunConfiguration) {
        configuration.data.scriptPath = scriptPathField.text.trim()
        configuration.data.workingDirectory = workingDirectoryField.text.trim()
        configuration.data.arguments = argumentsField.text.trim()
    }

    override fun createEditor(): JComponent = root
}

