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
import com.sakurasedaia.blenderdevelopment.lib.ProjectConfig.BlenderLogLevel
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Builds the Blender tool window UI for editing workspace configuration values. */
class BlenderToolWindowContent(private val project: Project,
                               private val onScanInstallations: (onCompleted: () -> Unit) -> Unit,) {
    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 500
    }

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
        lateinit var blenderLogLevelCombo: JComboBox<String>
        lateinit var useCustomBlenderInstall: JCheckBox
        lateinit var availableBlenderInstalls: JComboBox<String>
        val blenderLogLevels = BlenderLogLevel.entries
        val uiState = UiState()
        var isLoadingFromConfig = false

        fun logLevelLabel(level: BlenderLogLevel): String {
            return when (level) {
                BlenderLogLevel.FATAL -> MessageBundle.message("ui.toolwindow.group.workspace.log.level.fatal")
                BlenderLogLevel.ERROR -> MessageBundle.message("ui.toolwindow.group.workspace.log.level.error")
                BlenderLogLevel.WARNING -> MessageBundle.message("ui.toolwindow.group.workspace.log.level.warning")
                BlenderLogLevel.INFO -> MessageBundle.message("ui.toolwindow.group.workspace.log.level.info")
                BlenderLogLevel.DEBUG -> MessageBundle.message("ui.toolwindow.group.workspace.log.level.debug")
                BlenderLogLevel.TRACE -> MessageBundle.message("ui.toolwindow.group.workspace.log.level.trace")
            }
        }

        fun getSelectedBlenderLogLevel(): BlenderLogLevel {
            val selectedIndex = blenderLogLevelCombo.selectedIndex
            return blenderLogLevels.getOrNull(selectedIndex) ?: BlenderLogLevel.INFO
        }

        fun selectBlenderLogLevel(logLevel: BlenderLogLevel) {
            val selectedIndex = blenderLogLevels.indexOf(logLevel).takeIf { it >= 0 } ?: 0
            blenderLogLevelCombo.selectedIndex = selectedIndex
        }

        fun saveToConfig(showValidationNotification: Boolean): Boolean {
            val sourceFolder = sourceFolderField.text.trim()
            if (sourceFolder.isEmpty()) {
                if (showValidationNotification) {
                    logger.warn("Workspace save blocked: source folder is empty")
                    notifications.sendWarning(MessageBundle.message("ui.toolwindow.group.workspace.save.validation.source.empty"))
                }
                return false
            }
            
            config.setBlenderPath(blenderPathField.text.trim())
            config.setAddonSymlinkName(addonSymlinkField.text.trim())
            config.setSourceFolder(sourceFolder)
            config.setRunArguments(runArgumentsField.text.trim())
            config.setBlenderLogLevel(getSelectedBlenderLogLevel())
            return true
        }

        val autosaveTimer = Timer(AUTOSAVE_DEBOUNCE_MS) {
            if (isLoadingFromConfig) return@Timer
            if (saveToConfig(showValidationNotification = false)) {
                logger.debug("Autosaved workspace settings from Blender tool window.")
            }
        }.apply {
            isRepeats = false
        }

        fun scheduleAutosave() {
            if (isLoadingFromConfig) return
            autosaveTimer.restart()
        }

        fun addAutosaveListener(textField: JTextField) {
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = scheduleAutosave()
                override fun removeUpdate(e: DocumentEvent?) = scheduleAutosave()
                override fun changedUpdate(e: DocumentEvent?) = scheduleAutosave()
            })
        }

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
            val installPath = selectedInstallPath() ?: return
            if (blenderPathField.text != installPath) {
                blenderPathField.text = installPath
            }
        }

        fun installDisplayValues(installs: List<PluginConfig.BlendInstallInfo>): Array<String> {
            return installs.map { install ->
                val label = install.name.trim()
                label.ifBlank { install.path }
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
            isLoadingFromConfig = true
            addonSymlinkField.text = config.getAddonSymlinkName()
            sourceFolderField.text = config.getSourceFolder()
            runArgumentsField.text = config.getRunArguments()
            selectBlenderLogLevel(config.getBlenderLogLevel())

            refreshInstallWidgetsFromPluginState()
            applyBlenderInstallSelectionFromProjectConfig()
            isLoadingFromConfig = false
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
                                addActionListener {
                                    syncBlenderPathFromInstallSelection()
                                    scheduleAutosave()
                                }
                            }
                    }.visibleIf(useCustomBlenderInstallProperty.equalsTo(false))
                    row {
                        textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
                            .align(AlignX.FILL)
                            .applyToComponent {
                                blenderPathField = this
                                addAutosaveListener(textField)
                            }
                    }.visibleIf(useCustomBlenderInstallProperty.equalsTo(true))
                    row {
                        checkBox(MessageBundle.message("ui.toolwindow.group.workspace.blender.path.use.custom"))
                            .bindSelected(useCustomBlenderInstallProperty)
                            .applyToComponent {
                                useCustomBlenderInstall = this
                                addActionListener {
                                    updateInstallControlState()
                                    scheduleAutosave()
                                }
                            }
                        button(MessageBundle.message("ui.settings.group.discovery.scan.button")) {
                            onScanInstallations { refreshInstallWidgetsFromPluginState() }
                        }
                    }
                }

                row(MessageBundle.message("ui.toolwindow.group.workspace.addon.symlink.name")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent {
                            addonSymlinkField = this
                            addAutosaveListener(this)
                        }
                }
                row(MessageBundle.message("ui.toolwindow.group.workspace.source.folder")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent {
                            sourceFolderField = this
                            addAutosaveListener(this)
                        }
                }
                row(MessageBundle.message("ui.toolwindow.group.workspace.run.arguments")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent {
                            runArgumentsField = this
                            addAutosaveListener(this)
                        }
                }
                row(MessageBundle.message("ui.toolwindow.group.workspace.log.level")) {
                    comboBox(blenderLogLevels.map(::logLevelLabel))
                        .align(AlignX.FILL)
                        .applyToComponent {
                            blenderLogLevelCombo = this
                            addActionListener { scheduleAutosave() }
                        }
                }
                row {
                    button(MessageBundle.message("ui.toolwindow.group.workspace.save")) {
                        logger.log("Saving workspace settings from Blender tool window")
                        if (!saveToConfig(showValidationNotification = true)) return@button
                        logger.debug(
                            "Workspace settings saved (blenderPath='${config.getBlenderPath()}', " +
                                "addonSymlink='${config.getAddonSymlinkName()}', sourceFolder='${config.getSourceFolder()}', " +
                                "runArguments='${config.getRunArguments()}', blenderLogLevel='${config.getBlenderLogLevel()}')"
                        )
                        notifications.sendInfo(MessageBundle.message("ui.toolwindow.group.workspace.save.confirmation"))
                    }

                    button(MessageBundle.message("ui.toolwindow.group.workspace.reload")) {
                        loadFromConfig()
                        autosaveTimer.stop()
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
