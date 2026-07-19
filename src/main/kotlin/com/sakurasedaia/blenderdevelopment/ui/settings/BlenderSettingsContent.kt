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

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.ui.components.EnvironmentVariablesTable
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import javax.swing.JComponent

/** Settings panel content for global Blender plugin configuration. */
internal class BlenderSettingsContent(
    private val onScanInstallations: () -> Unit,
) {
    private data class SettingBinding(
      val getFromConfig: (PluginConfig) -> String,
      val getFromField: () -> String,
      val setToField: (String) -> Unit,
      val setToConfig: (PluginConfig, String) -> Unit,
    )

    private lateinit var customBlenderInstallPath: TextFieldWithBrowseButton
    private lateinit var customCodeCompletionPath: TextFieldWithBrowseButton
    private lateinit var customLogPath: TextFieldWithBrowseButton
    private lateinit var downloadPath: TextFieldWithBrowseButton
    private lateinit var clearDownloadAfterInstall: JBCheckBox
    private val globalEnvironmentVariablesTable = EnvironmentVariablesTable()
    
    
    private val root = panel {
        group(MessageBundle.message("ui.settings.group.filepaths.title")) {
            row {
                cell(TextFieldWithBrowseButton())
                    .applyToComponent {
                        customBlenderInstallPath = this
                        addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()))
                    }
                    .comment(MessageBundle.message("ui.settings.group.blender.comment"))
                    .align(AlignX.FILL)
            }
            row {
                cell(TextFieldWithBrowseButton())
                    .applyToComponent {
                        customCodeCompletionPath = this
                        addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()))
                    }
                    .comment(MessageBundle.message("ui.settings.group.code-completion.comment"))
                    .align(AlignX.FILL)
            }
            row {
                cell(TextFieldWithBrowseButton())
                    .applyToComponent {
                        customLogPath = this
                        addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()))
                    }
                    .comment(MessageBundle.message("ui.settings.group.log.comment"))
                    .align(AlignX.FILL)
            }
            row {
                cell(TextFieldWithBrowseButton())
                    .comment(MessageBundle.message("ui.settings.group.download.comment"))
                    .applyToComponent { downloadPath = this }
                    .align(AlignX.FILL)
            }
            row {
                cell(JBCheckBox(MessageBundle.message("ui.settings.group.download.clear-after-install")))
                    .applyToComponent { clearDownloadAfterInstall = this }
                    .align(AlignX.FILL)
            }
        }
        group(MessageBundle.message("ui.settings.group.discovery.title")) {
            row {
                button(MessageBundle.message("ui.settings.group.discovery.scan.button")) {
                    onScanInstallations()
                }
            }.comment(MessageBundle.message("ui.settings.group.discovery.scan.comment"))
        }
        group(MessageBundle.message("ui.settings.group.environment.variables.title")) {
            row {
                cell(globalEnvironmentVariablesTable.component())
                    .align(AlignX.FILL)
                    .resizableColumn()
                contextHelp(MessageBundle.message("ui.settings.group.environment.variables.comment"))
            }.resizableRow()
        }
    }

    private val settingsBindings: List<SettingBinding>
        get() = listOf(
            SettingBinding(
                getFromConfig = { it.getBlenderInstallPath() },
                getFromField = { customBlenderInstallPath.text },
                setToField = { customBlenderInstallPath.text = it },
                setToConfig = { config, value -> config.setBlenderInstallPath(value) },
            ),
            SettingBinding(
                getFromConfig = { it.getCodeCompletionPath() },
                getFromField = { customCodeCompletionPath.text },
                setToField = { customCodeCompletionPath.text = it },
                setToConfig = { config, value -> config.setCodeCompletionPath(value) },
            ),
            SettingBinding(
                getFromConfig = { it.getLogPath() },
                getFromField = { customLogPath.text },
                setToField = { customLogPath.text = it },
                setToConfig = { config, value -> config.setLogPath(value) },
            ),
        )

    internal fun component(): JComponent = root

    internal fun reset(config: PluginConfig) {
        settingsBindings.forEach { binding ->
            binding.setToField(binding.getFromConfig(config))
        }
        downloadPath.text = config.getDownloadPath()
        clearDownloadAfterInstall.isSelected = config.getClearDownloadsAfterInstall()
        globalEnvironmentVariablesTable.setVariables(config.getGlobalEnvironmentVariables())
    }

    internal fun isModified(config: PluginConfig): Boolean =
        settingsBindings.any { binding ->
            binding.getFromConfig(config) != binding.getFromField()
        } ||
            config.getDownloadPath() != downloadPath.text ||
            config.getClearDownloadsAfterInstall() != clearDownloadAfterInstall.isSelected ||
            config.getGlobalEnvironmentVariables() != globalEnvironmentVariablesTable.getVariables()

    internal fun apply(config: PluginConfig) {
        settingsBindings.forEach { binding ->
            binding.setToConfig(config, binding.getFromField())
        }
        config.setDownloadPath(downloadPath.text)
        config.setClearDownloadsAfterInstall(clearDownloadAfterInstall.isSelected)
        config.setGlobalEnvironmentVariables(globalEnvironmentVariablesTable.getVariables())
    }
}
