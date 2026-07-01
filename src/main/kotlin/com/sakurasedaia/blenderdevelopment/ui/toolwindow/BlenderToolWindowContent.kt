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
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
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
    private data class UiState(
        var detectedBlenderInstalls: List<PluginConfig.BlendInstallInfo> = emptyList(),
    )

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
        val uiState = UiState()

        fun updateInstallControlState() {
            val useCustomPath = useCustomBlenderInstall.isSelected
            blenderPathField.isEnabled = useCustomPath
            availableBlenderInstalls.isEnabled = !useCustomPath && uiState.detectedBlenderInstalls.isNotEmpty()
        }

        fun selectedInstallPath(): String? {
            val selectedIndex = availableBlenderInstalls.selectedIndex
            return uiState.detectedBlenderInstalls.getOrNull(selectedIndex)?.path
        }

        fun syncBlenderPathFromInstallSelection() {
            if (useCustomBlenderInstall.isSelected) return
            selectedInstallPath()?.let { blenderPathField.text = it }
        }

        fun installDisplayValues(installs: List<PluginConfig.BlendInstallInfo>): Array<String> {
            return installs.map { install ->
                val label = install.name.trim()
                if (label.isNotBlank()) label else install.path
            }.toTypedArray()
        }

        fun refreshInstallWidgetsFromPluginState() {
            val previousSelectedPath = if (useCustomBlenderInstall.isSelected) null else selectedInstallPath()

            uiState.detectedBlenderInstalls = pluginConfig.getDetectedBlenderInstalls()
            availableBlenderInstalls.model = DefaultComboBoxModel(installDisplayValues(uiState.detectedBlenderInstalls))

            if (!useCustomBlenderInstall.isSelected) {
                val indexToSelect = when {
                    previousSelectedPath != null ->
                        uiState.detectedBlenderInstalls.indexOfFirst { it.path == previousSelectedPath }
                    else -> -1
                }

                if (indexToSelect >= 0) {
                    availableBlenderInstalls.selectedIndex = indexToSelect
                } else if (uiState.detectedBlenderInstalls.isNotEmpty()) {
                    availableBlenderInstalls.selectedIndex = 0
                }
                syncBlenderPathFromInstallSelection()
            }

            updateInstallControlState()
        }

        fun applyBlenderInstallSelectionFromProjectConfig() {
            val configuredBlenderPath = config.getBlenderPath().trim()
            val selectedDetectedInstallIndex =
                uiState.detectedBlenderInstalls.indexOfFirst { it.path == configuredBlenderPath }

            if (selectedDetectedInstallIndex >= 0) {
                useCustomBlenderInstall.isSelected = false
                availableBlenderInstalls.selectedIndex = selectedDetectedInstallIndex
                blenderPathField.text = uiState.detectedBlenderInstalls[selectedDetectedInstallIndex].path
            } else if (configuredBlenderPath.isBlank()) {
                useCustomBlenderInstall.isSelected = false
                if (uiState.detectedBlenderInstalls.isNotEmpty()) {
                    availableBlenderInstalls.selectedIndex = 0
                    blenderPathField.text = uiState.detectedBlenderInstalls.first().path
                } else {
                    blenderPathField.text = ""
                }
            } else {
                useCustomBlenderInstall.isSelected = true
                blenderPathField.text = configuredBlenderPath
            }
            updateInstallControlState()
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

            refreshInstallWidgetsFromPluginState()
            applyBlenderInstallSelectionFromProjectConfig()
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
                                addActionListener { syncBlenderPathFromInstallSelection() }
                            }
                    }.visibleIf(useCustomBlenderInstallProperty.equalsTo(false))
                    row {
                        textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
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
                            onScanInstallations { refreshInstallWidgetsFromPluginState() }
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
            }
        }.apply {
            border = JBUI.Borders.empty(8, 10)
            loadFromConfig()
        }
    }
}
