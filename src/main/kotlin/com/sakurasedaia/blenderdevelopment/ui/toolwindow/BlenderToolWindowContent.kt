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

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.sakurasedaia.blenderdevelopment.lib.PluginConfig
import com.sakurasedaia.blenderdevelopment.lib.ProjectConfig
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField

/** Builds the Blender tool window UI for editing workspace configuration values. */
class BlenderToolWindowContent(private val project: Project,
                               private val onScanInstallations: (onCompleted: () -> Unit) -> Unit,) {
    /**
     * Creates and returns the tool window Swing content component.
     *
     * @return root Swing component for the Blender tool window.
     */
    fun getContent(): JComponent {
        val config = ProjectConfig.getInstance(project)
        val pluginConfig = PluginConfig.getInstance()
        val notifications = NotificationModal.getInstance(project)
        val logger = PluginLogger.getInstance(project)

        lateinit var blenderPathField: TextFieldWithBrowseButton
        lateinit var addonSymlinkField: JTextField
        lateinit var sourceFolderField: JTextField
        lateinit var runArgumentsField: JTextField
        lateinit var useCustomBlenderInstall: JCheckBox
        lateinit var availableBlenderInstalls: JComboBox<String>
        val statusLabel = JBLabel("")
        var detectedBlenderInstalls: List<PluginConfig.BlendInstallInfo> = emptyList()

        fun syncBlenderPathFromInstallSelection() {
            if (useCustomBlenderInstall.isSelected) return

            val selectedIndex = availableBlenderInstalls.selectedIndex
            if (selectedIndex in detectedBlenderInstalls.indices) {
                blenderPathField.text = detectedBlenderInstalls[selectedIndex].path
            }
        }

        fun updateInstallControlState() {
            val useCustomPath = useCustomBlenderInstall.isSelected
            blenderPathField.isEnabled = useCustomPath
            availableBlenderInstalls.isEnabled = !useCustomPath && detectedBlenderInstalls.isNotEmpty()
            syncBlenderPathFromInstallSelection()
        }

        /**
         * Refreshes all form fields from persisted workspace configuration.
         *
         * @return `Unit`.
         */
        fun loadFromConfig() {
            addonSymlinkField.text = config.getAddonSymlinkName()
            sourceFolderField.text = config.getSourceFolder()
            runArgumentsField.text = config.getRunArguments()

            detectedBlenderInstalls = pluginConfig.getDetectedBlenderInstalls()
            availableBlenderInstalls.model = DefaultComboBoxModel(
                detectedBlenderInstalls
                    .map { it.name.trim() }
                    .toTypedArray()
            )

            val configuredBlenderPath = config.getBlenderPath()
            val selectedDetectedInstallIndex = detectedBlenderInstalls.indexOfFirst { it.path == configuredBlenderPath }

            when {
                selectedDetectedInstallIndex >= 0 -> {
                    useCustomBlenderInstall.isSelected = false
                    availableBlenderInstalls.selectedIndex = selectedDetectedInstallIndex
                    blenderPathField.text = detectedBlenderInstalls[selectedDetectedInstallIndex].path
                }
                configuredBlenderPath.isBlank() && detectedBlenderInstalls.isNotEmpty() -> {
                    useCustomBlenderInstall.isSelected = false
                    availableBlenderInstalls.selectedIndex = 0
                    blenderPathField.text = detectedBlenderInstalls.first().path
                }
                else -> {
                    useCustomBlenderInstall.isSelected = true
                    blenderPathField.text = configuredBlenderPath
                }
            }

            updateInstallControlState()
            statusLabel.text = ""
        }

        return panel {
            group(MessageBundle.message("ui.toolwindow.group.workspace.title")) {
                group(MessageBundle.message("ui.toolwindow.group.workspace.blender.install")) {
                    val uiStateGraph = PropertyGraph()
                    val useCustomBlenderInstallProperty: GraphProperty<Boolean> = uiStateGraph.property(false)
                    row {
                        comboBox(emptyList<String>())
                            .align(AlignX.FILL)
                            .applyToComponent {
                                availableBlenderInstalls = this
                                addActionListener { updateInstallControlState() }
                            }
                    }.visibleIf(useCustomBlenderInstallProperty.equalsTo(false))
                    row {
                        textFieldWithBrowseButton(fileChooserDescriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
                            .align(AlignX.FILL)
                            .applyToComponent { blenderPathField = this }
                    }.visibleIf(useCustomBlenderInstallProperty.equalsTo(true))
                    row {
                        checkBox(MessageBundle.message("ui.toolwindow.group.workspace.blender.path.use.custom"))
                            .bindSelected(useCustomBlenderInstallProperty)
                            .applyToComponent {
                                useCustomBlenderInstall = this
                                addActionListener { updateInstallControlState() }
                            }
                        button(MessageBundle.message("ui.settings.group.discovery.scan.button")) {
                            onScanInstallations { loadFromConfig() }
                        }
                    }
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
                row(MessageBundle.message("ui.toolwindow.group.workspace.run.arguments")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent { runArgumentsField = this }
                }
                row {
                    button(MessageBundle.message("ui.toolwindow.group.workspace.save")) {
                        val sourceFolder = sourceFolderField.text.trim()
                        if (sourceFolder.isEmpty()) {
                            logger.warn("Workspace save blocked: source folder is empty")
                            notifications.sendWarning(MessageBundle.message("ui.toolwindow.group.workspace.save.validation.source.empty"))
                            return@button
                        }

                        logger.log("Saving workspace settings from Blender tool window")
                        config.setBlenderPath(blenderPathField.text.trim())
                        config.setAddonSymlinkName(addonSymlinkField.text.trim())
                        config.setSourceFolder(sourceFolder)
                        config.setRunArguments(runArgumentsField.text.trim())
                        logger.debug(
                            "Workspace settings saved (blenderPath='${config.getBlenderPath()}', " +
                                "addonSymlink='${config.getAddonSymlinkName()}', sourceFolder='${config.getSourceFolder()}', " +
                                "runArguments='${config.getRunArguments()}')"
                        )
                        notifications.sendInfo(MessageBundle.message("ui.toolwindow.group.workspace.save.confirmation"))
                    }

                    button(MessageBundle.message("ui.toolwindow.group.workspace.reload")) {
                        loadFromConfig()
                        logger.log("Reloaded workspace settings in Blender tool window")
                        notifications.sendInfo(MessageBundle.message("ui.toolwindow.group.workspace.reload.confirmation"))
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
