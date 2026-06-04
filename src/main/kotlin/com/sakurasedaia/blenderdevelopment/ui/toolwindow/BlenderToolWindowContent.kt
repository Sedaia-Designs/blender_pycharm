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

package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderdevelopment.common.MessageBundle
import com.sakurasedaia.blenderdevelopment.config.BlenderProjectConfig
import javax.swing.JComponent
import javax.swing.JTextField

class BlenderToolWindowContent(private val project: Project) {
    fun getContent(): JComponent {
        val config = BlenderProjectConfig.getInstance(project)

        lateinit var blenderPathField: JTextField
        lateinit var addonSymlinkField: JTextField
        lateinit var sourceFolderField: JTextField
        lateinit var sandboxCheckBox: javax.swing.JCheckBox
        val statusLabel = JBLabel("")

        fun loadFromConfig() {
            blenderPathField.text = config.getBlenderPath()
            addonSymlinkField.text = config.getAddonSymlinkName()
            sourceFolderField.text = config.getSourceFolder()
            sandboxCheckBox.isSelected = config.getSandbox()
            statusLabel.text = ""
        }

        return panel {
            group(MessageBundle.message("ui.toolwindow.group.workspace.title")) {
                row(MessageBundle.message("ui.toolwindow.group.workspace.blender.path")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent { blenderPathField = this }
                }
                row(MessageBundle.message("ui.toolwindow.group.workspace.addon.symlink.name")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent { addonSymlinkField = this }
                }
                row(MessageBundle.message("ui.toolwindow.group.workspace.source.folder")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent { sourceFolderField = this }
                }
                row {
                    checkBox(MessageBundle.message("ui.toolwindow.group.workspace.sandbox"))
                        .applyToComponent { sandboxCheckBox = this }
                }
                row {
                    button(MessageBundle.message("ui.toolwindow.group.workspace.save")) {
                        val sourceFolder = sourceFolderField.text.trim()
                        if (sourceFolder.isEmpty()) {
                            return@button
                        }

                        config.setBlenderPath(blenderPathField.text.trim())
                        config.setAddonSymlinkName(addonSymlinkField.text.trim())
                        config.setSourceFolder(sourceFolder)
                        config.setSandbox(sandboxCheckBox.isSelected)
                    }

                    button(MessageBundle.message("ui.toolwindow.group.workspace.reload")) {
                        loadFromConfig()
                    }
                }
                row {
                    cell(statusLabel)
                }
            }
        }.apply {
            border = JBUI.Borders.empty(8, 10)
            loadFromConfig()
        }
    }
}
