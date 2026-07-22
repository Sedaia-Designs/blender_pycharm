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

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.ui.components.EnvironmentVariablesTable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.ListSelectionModel

internal data class BlenderVersionSettingsRow(
    val version: BlenderVersion,
    val pythonVersion: String,
    val installStatus: String,
)

/** Settings panel content for global Blender plugin configuration. */
internal class BlenderSettingsContent(
    private val onScanInstallations: ((List<PluginConfig.BlendInstallInfo>) -> Unit) -> Unit,
    private val onRefreshVersions: ((Result<List<BlenderVersion>>) -> Unit) -> Unit,
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
    private lateinit var minimumBlenderVersion: JBTextField
    private val versionTableModel = ListTableModel<BlenderVersionSettingsRow>(
        object : ColumnInfo<BlenderVersionSettingsRow, String>(MessageBundle.message("ui.settings.group.versions.column.version")) {
            override fun valueOf(item: BlenderVersionSettingsRow): String = item.version.blVersion
        },
        object : ColumnInfo<BlenderVersionSettingsRow, String>(MessageBundle.message("ui.settings.group.versions.column.python")) {
            override fun valueOf(item: BlenderVersionSettingsRow): String = item.pythonVersion
        },
        object : ColumnInfo<BlenderVersionSettingsRow, String>(MessageBundle.message("ui.settings.group.versions.column.status")) {
            override fun valueOf(item: BlenderVersionSettingsRow): String = item.installStatus
        },
    )
    private val versionTable = JBTable(versionTableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        emptyText.text = MessageBundle.message("ui.settings.group.versions.empty")
        setShowGrid(false)
        tableHeader.reorderingAllowed = false
    }
    private val lastRefreshedLabel = JBLabel()
    private val downloadVersionButton = JButton(MessageBundle.message("ui.settings.group.versions.download.button")).apply {
        isEnabled = false
        addActionListener {
            selectedVersion()?.getDownloadURL()?.takeIf(String::isNotBlank)?.let(BrowserUtil::browse)
        }
    }
    private val globalEnvironmentVariablesTable = EnvironmentVariablesTable()

    init {
        versionTable.selectionModel.addListSelectionListener {
            downloadVersionButton.isEnabled = selectedVersion()?.getDownloadURL()?.isNotBlank() == true
        }
    }

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
            row(MessageBundle.message("ui.settings.group.discovery.minimum-version.label")) {
                textField()
                    .columns(8)
                    .validationOnInput {
                        if (PluginConfig.isValidMinorVersion(it.text)) null
                        else error(MessageBundle.message("ui.settings.group.discovery.minimum-version.validation"))
                    }
                    .validationOnApply {
                        if (PluginConfig.isValidMinorVersion(it.text)) null
                        else error(MessageBundle.message("ui.settings.group.discovery.minimum-version.validation"))
                    }
                    .applyToComponent { minimumBlenderVersion = this }
                    .comment(MessageBundle.message("ui.settings.group.discovery.minimum-version.comment"))
            }
            row {
                button(MessageBundle.message("ui.settings.group.discovery.scan.button")) {
                    onScanInstallations(::refreshVersionRows)
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
        group(MessageBundle.message("ui.settings.group.versions.title")) {
            row {
                cell(ScrollPaneFactory.createScrollPane(versionTable, true))
                    .align(AlignX.FILL)
                    .resizableColumn()
            }.resizableRow()
            row {
                cell(lastRefreshedLabel)
            }.comment(MessageBundle.message("ui.settings.group.versions.management.comment"))
            row {
                button(MessageBundle.message("ui.settings.group.versions.refresh.button")) {
                    lastRefreshedLabel.text = MessageBundle.message("ui.settings.group.versions.refreshing")
                    onRefreshVersions(::onVersionsRefreshed)
                }
                button(MessageBundle.message("ui.settings.group.versions.scan.button")) {
                    onScanInstallations(::refreshVersionRows)
                }
                cell(downloadVersionButton)
            }
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
        minimumBlenderVersion.text = config.getMinimumBlenderVersion()
        globalEnvironmentVariablesTable.setVariables(config.getGlobalEnvironmentVariables())
        refreshVersionRows(config.getDetectedBlenderInstalls())
        updateLastRefreshed(config.getBlenderUpdateCheck().lastCheckedEpochMillis)
    }

    internal fun isModified(config: PluginConfig): Boolean =
        settingsBindings.any { binding ->
            binding.getFromConfig(config) != binding.getFromField()
        } ||
            config.getDownloadPath() != downloadPath.text ||
            config.getClearDownloadsAfterInstall() != clearDownloadAfterInstall.isSelected ||
            config.getMinimumBlenderVersion() != minimumBlenderVersion.text ||
            config.getGlobalEnvironmentVariables() != globalEnvironmentVariablesTable.getVariables()

    internal fun apply(config: PluginConfig) {
        settingsBindings.forEach { binding ->
            binding.setToConfig(config, binding.getFromField())
        }
        config.setDownloadPath(downloadPath.text)
        config.setClearDownloadsAfterInstall(clearDownloadAfterInstall.isSelected)
        config.setMinimumBlenderVersion(minimumBlenderVersion.text)
        config.setGlobalEnvironmentVariables(globalEnvironmentVariablesTable.getVariables())
    }

    private fun onVersionsRefreshed(result: Result<List<BlenderVersion>>) {
        result.onSuccess { versions ->
            refreshVersionRows(PluginConfig.getInstance().getDetectedBlenderInstalls(), versions)
            updateLastRefreshed(PluginConfig.getInstance().getBlenderUpdateCheck().lastCheckedEpochMillis)
        }.onFailure {
            lastRefreshedLabel.text = MessageBundle.message("ui.settings.group.versions.refresh.failed")
        }
    }

    private fun refreshVersionRows(
        installs: List<PluginConfig.BlendInstallInfo>,
        versions: List<BlenderVersion> = BlenderVersions.LIST,
    ) {
        versionTableModel.items = buildVersionSettingsRows(versions, installs)
        if (versionTableModel.rowCount > 0 && versionTable.selectedRow < 0) {
            versionTable.setRowSelectionInterval(0, 0)
        }
    }

    private fun updateLastRefreshed(epochMillis: Long) {
        lastRefreshedLabel.text = if (epochMillis <= 0) {
            MessageBundle.message("ui.settings.group.versions.last-refreshed.never")
        } else {
            MessageBundle.message(
                "ui.settings.group.versions.last-refreshed.value",
                LAST_REFRESHED_FORMATTER.format(Instant.ofEpochMilli(epochMillis)),
            )
        }
    }

    private fun selectedVersion(): BlenderVersion? {
        val selectedRow = versionTable.selectedRow
        return if (selectedRow < 0) null else versionTableModel.getItem(selectedRow).version
    }

    companion object {
        private val LAST_REFRESHED_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())

        internal fun buildVersionSettingsRows(
            versions: List<BlenderVersion>,
            installs: List<PluginConfig.BlendInstallInfo>,
        ): List<BlenderVersionSettingsRow> {
            val installsByMinor = installs.associateBy { BlenderVersions.normalizeVersion(it.version) }
            return versions.map { version ->
                val installed = installsByMinor[version.blMajorMinor]
                BlenderVersionSettingsRow(
                    version = version,
                    pythonVersion = installed?.let { version.pyVersion } ?: "—",
                    installStatus = installed?.let {
                        MessageBundle.message("ui.settings.group.versions.status.installed", it.version)
                    } ?: MessageBundle.message("ui.settings.group.versions.status.not-detected"),
                )
            }
        }
    }
}
