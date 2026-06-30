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

import com.sakurasedaia.blenderdevelopment.config.ProjectConfig
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JCheckBox
import javax.swing.JPanel

/** Basic editor UI for Blender run configuration settings. */
class BlenderRunSettingsEditor : SettingsEditor<BlenderRunConfiguration>() {
    private lateinit var sourcePathField: JBTextField
    private lateinit var projectDirectoryField: JBTextField
    private lateinit var sandboxCheckBox: JCheckBox
    private lateinit var argumentsField: JBTextField

    private val root: JPanel = panel {
        row(MessageBundle.message("run.configuration.editor.source.path")) {
            textField()
                .align(AlignX.FILL)
                .applyToComponent { sourcePathField = this }
        }
        row {
            checkBox(MessageBundle.message("ui.toolwindow.group.workspace.sandbox"))
                .applyToComponent { sandboxCheckBox = this }
        }
        row(MessageBundle.message("run.configuration.editor.working.directory")) {
            textField()
                .align(AlignX.FILL)
                .enabled(false)
                .applyToComponent { projectDirectoryField = this }
        }
        row(MessageBundle.message("run.configuration.editor.arguments")) {
            textField()
                .align(AlignX.FILL)
                .applyToComponent { argumentsField = this }
        }
    }

    override fun resetEditorFrom(configuration: BlenderRunConfiguration) {
        val projectConfig = ProjectConfig.getInstance(configuration.project)
        sourcePathField.text = projectConfig.getSourceFolder()
        sandboxCheckBox.isSelected = projectConfig.getSandbox()
        projectDirectoryField.text = configuration.project.basePath.orEmpty()
        argumentsField.text = projectConfig.getRunArguments()
    }

    override fun applyEditorTo(configuration: BlenderRunConfiguration) {
        val projectConfig = ProjectConfig.getInstance(configuration.project)
        projectConfig.setSourceFolder(sourcePathField.text.trim())
        projectConfig.setSandbox(sandboxCheckBox.isSelected)
        projectConfig.setRunArguments(argumentsField.text.trim())
    }

    override fun createEditor(): JComponent = root
}
