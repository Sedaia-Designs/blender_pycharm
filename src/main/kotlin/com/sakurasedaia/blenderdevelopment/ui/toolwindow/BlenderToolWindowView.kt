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

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import com.sakurasedaia.blenderdevelopment.ui.IconBundle
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.ui.components.EnvironmentVariablesTable
import com.sakurasedaia.blenderdevelopment.ui.components.ScriptDirectoriesTable
import com.sakurasedaia.blenderdevelopment.util.PythonModuleNameValidator
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import javax.swing.text.JTextComponent

internal class BlenderToolWindowView(project: Project) {
    var onBlenderPathChanged: (String) -> Unit = {}
    var onAddonSymlinkNameChanged: (String) -> Unit = {}
    var onSourceFolderChanged: (String) -> Unit = {}
    var onRunArgumentsChanged: (String) -> Unit = {}
    var onBlenderLogLevelChanged: (BlenderLogLevel) -> Unit = {}
    var onReloadOnSaveChanged: (Boolean) -> Unit = {}
    var onJustMyCodeChanged: (Boolean) -> Unit = {}
    var onSaveWorkspaceConfigRequested: () -> Unit = {}
    var onEnvironmentVariablesChanged: (Map<String, String>) -> Unit = {}
    var onScriptDirectoriesChanged: (List<String>) -> Unit = {}
    var onReloadRequested: () -> Unit = {}
    var onScanInstallationsRequested: () -> Unit = {}
    var onInstallStubsRequested: (String) -> Unit = {}

    private val environmentVariablesTable = EnvironmentVariablesTable()
    private val scriptDirectoriesTable = ScriptDirectoriesTable(project)
    private val blenderLogLevels = BlenderLogLevel.entries
    private val propertyGraph = PropertyGraph()
    private val useCustomBlenderInstallProperty: GraphProperty<Boolean> = propertyGraph.property(false)

    private lateinit var blenderPathField: TextFieldWithBrowseButton
    private lateinit var addonSymlinkField: JTextField
    private lateinit var sourceFolderField: JTextField
    private lateinit var runArgumentsField: JTextField
    private lateinit var blenderLogLevelCombo: JComboBox<String>
    private lateinit var reloadOnSaveCheckBox: JCheckBox
    private lateinit var justMyCodeCheckBox: JCheckBox
    private lateinit var useCustomBlenderInstall: JCheckBox
    private lateinit var availableBlenderInstalls: JComboBox<String>
    private lateinit var installStubsButton: JButton
    private lateinit var saveWorkspaceConfigButton: JButton

    private var detectedBlenderInstalls: List<PluginConfig.BlendInstallInfo> = emptyList()
    private var isRendering = false

    val component: JComponent
    val blenderPath: String
        get() = blenderPathField.text

    init {
        component =
            JBScrollPane(createContentPanel()).apply {
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                border = JBUI.Borders.empty()
                viewportBorder = JBUI.Borders.empty()
            }
        environmentVariablesTable.setOnChangeListener { variables ->
            emit { onEnvironmentVariablesChanged(variables) }
        }
        scriptDirectoriesTable.setOnChangeListener { directories ->
            emit { onScriptDirectoriesChanged(directories) }
        }
    }

    fun render(state: BlenderToolWindowState) {
        isRendering = true
        try {
            addonSymlinkField.text = state.addonSymlinkName
            sourceFolderField.text = state.sourceFolder
            runArgumentsField.text = state.runArguments
            selectBlenderLogLevel(state.blenderLogLevel)
            reloadOnSaveCheckBox.isSelected = state.reloadOnSave
            justMyCodeCheckBox.isSelected = state.justMyCode
            saveWorkspaceConfigButton.text =
                MessageBundle.message(
                    if (state.workspaceConfigEnabled) "ui.toolwindow.workspace.update" else "ui.toolwindow.workspace.save"
                )
            environmentVariablesTable.setVariables(state.environmentVariables)
            scriptDirectoriesTable.setDirectories(state.scriptDirectories)
            detectedBlenderInstalls = state.detectedBlenderInstalls
            updateInstallModel()
            applyBlenderInstallSelection(state.blenderPath)
        } finally {
            isRendering = false
        }
    }

    private fun createContentPanel(): JComponent = panel {
        group(MessageBundle.message("ui.toolwindow.group.executable.title")) {
            row {
                    comboBox(detectedBlenderInstalls.map { it.name }.toList())
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .applyToComponent {
                            availableBlenderInstalls = this
                            addActionListener {
                                syncBlenderPathFromInstallSelection()
                                updateInstallControlState()
                                emitBlenderPath()
                            }
                        }
                        .gap(RightGap.SMALL)
                    button("") {
                            onScanInstallationsRequested()
                        }
                        .applyToComponent {
                            icon = IconBundle.Refresh
                            toolTipText = MessageBundle.message("ui.toolwindow.group.executable.scan-for-install")
                            accessibleContext.accessibleName = MessageBundle.message("ui.toolwindow.group.executable.scan-for-install")
                        }
                    button("") {
                            selectedBlenderVersion()?.let(onInstallStubsRequested)
                        }
                        .applyToComponent {
                            installStubsButton = this
                            icon = IconBundle.InstallStubs
                            toolTipText = MessageBundle.message("ui.toolwindow.group.executable.install-stubs")
                            accessibleContext.accessibleName = MessageBundle.message("ui.toolwindow.group.executable.install-stubs")
                        }
                }
                .visibleIf(useCustomBlenderInstallProperty.equalsTo(false))
            row {
                    @Suppress("UnstableApiUsage")
                    textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
                        .align(AlignX.FILL)
                        .applyToComponent {
                            blenderPathField = this
                            addDocumentListener(textField, ::emitBlenderPath)
                        }
                }
                .visibleIf(useCustomBlenderInstallProperty.equalsTo(true))
            row {
                checkBox(MessageBundle.message("ui.toolwindow.group.blender.path-use-custom"))
                    .bindSelected(useCustomBlenderInstallProperty)
                    .applyToComponent {
                        useCustomBlenderInstall = this
                        addActionListener {
                            updateInstallControlState()
                            emitBlenderPath()
                        }
                    }
            }
        }

        group(MessageBundle.message("ui.toolwindow.group.run-and-debug.title")) {
            row(MessageBundle.message("ui.toolwindow.group.environment.addon-symlink-name")) {
                textField()
                    .align(AlignX.FILL)
                    .validationOnInput {
                        if (PythonModuleNameValidator.isValid(it.text.trim())) null
                        else error(MessageBundle.message("ui.common.python.module.name.validation"))
                    }
                    .applyToComponent {
                        addonSymlinkField = this
                        installRealtimeSymlinkNormalization(this)
                        addDocumentListener(this) { emit { onAddonSymlinkNameChanged(text) } }
                    }
            }
            row(MessageBundle.message("ui.toolwindow.group.environment.source-folder")) {
                textField().align(AlignX.FILL).applyToComponent {
                    sourceFolderField = this
                    addDocumentListener(this) { emit { onSourceFolderChanged(text) } }
                }
            }
            row(MessageBundle.message("ui.toolwindow.group.environment.run-arguments")) {
                textField().align(AlignX.FILL).applyToComponent {
                    runArgumentsField = this
                    addDocumentListener(this) { emit { onRunArgumentsChanged(text) } }
                }
            }
            row(MessageBundle.message("ui.toolwindow.group.environment.log-level")) {
                comboBox(blenderLogLevels.map(::logLevelLabel)).align(AlignX.FILL).applyToComponent {
                    blenderLogLevelCombo = this
                    addActionListener { emit { onBlenderLogLevelChanged(selectedBlenderLogLevel()) } }
                }
            }
            row {
                checkBox(MessageBundle.message("ui.toolwindow.group.debugger.reload-on-save")).applyToComponent {
                    reloadOnSaveCheckBox = this
                    addActionListener { emit { onReloadOnSaveChanged(isSelected) } }
                }
                checkBox(MessageBundle.message("ui.toolwindow.group.debugger.just-my-code")).applyToComponent {
                    justMyCodeCheckBox = this
                    addActionListener { emit { onJustMyCodeChanged(isSelected) } }
                }
            }
            row {
                button(MessageBundle.message("ui.toolwindow.group.debugger.reload")) {
                    onReloadRequested()
                }
                button(MessageBundle.message("ui.toolwindow.workspace.save")) {
                        onSaveWorkspaceConfigRequested()
                    }
                    .applyToComponent {
                        saveWorkspaceConfigButton = this
                        toolTipText = MessageBundle.message("ui.toolwindow.workspace.comment")
                    }
            }
        }

        group(MessageBundle.message("ui.toolwindow.group.environment.section-title")) {
            row {
                label(MessageBundle.message("ui.toolwindow.group.environment.script-directories"))
            }
            row {
                cell(scriptDirectoriesTable.component()).align(AlignX.FILL).resizableColumn()
            }
            row {
                label(MessageBundle.message("ui.toolwindow.group.environment.title"))
                contextHelp(MessageBundle.message("ui.toolwindow.group.environment.variables.comment"))
            }
            row {
                cell(environmentVariablesTable.component()).align(AlignX.FILL).resizableColumn()
            }
        }
    }
        .apply {
            border = JBUI.Borders.empty(8, 10)
        }

    private fun applyBlenderInstallSelection(configuredPath: String) {
        val selectedIndex = detectedBlenderInstalls.indexOfFirst { it.path == configuredPath.trim() }
        when {
            selectedIndex >= 0 -> {
                useCustomBlenderInstall.isSelected = false
                availableBlenderInstalls.selectedIndex = selectedIndex
                blenderPathField.text = detectedBlenderInstalls[selectedIndex].path
            }
            configuredPath.isBlank() -> {
                useCustomBlenderInstall.isSelected = false
                availableBlenderInstalls.selectedIndex = if (detectedBlenderInstalls.isEmpty()) -1 else 0
                blenderPathField.text = detectedBlenderInstalls.firstOrNull()?.path.orEmpty()
            }
            else -> {
                useCustomBlenderInstall.isSelected = true
                blenderPathField.text = configuredPath
            }
        }
        updateInstallControlState()
    }

    private fun updateInstallModel() {
        val displayValues =
            detectedBlenderInstalls
                .map { install ->
                    install.name.trim().ifBlank { install.path }
                }
                .toTypedArray()
        availableBlenderInstalls.model = DefaultComboBoxModel(displayValues)
    }

    private fun updateInstallControlState() {
        val useCustomPath = useCustomBlenderInstall.isSelected
        blenderPathField.isEnabled = useCustomPath
        availableBlenderInstalls.isEnabled = !useCustomPath && detectedBlenderInstalls.isNotEmpty()
        installStubsButton.isEnabled = !useCustomPath && selectedBlenderVersion() != null
    }

    private fun selectedInstallPath(): String? {
        return detectedBlenderInstalls.getOrNull(availableBlenderInstalls.selectedIndex)?.path
    }

    private fun selectedBlenderVersion(): String? {
        return detectedBlenderInstalls.getOrNull(availableBlenderInstalls.selectedIndex)?.version
    }

    private fun syncBlenderPathFromInstallSelection() {
        if (useCustomBlenderInstall.isSelected) return
        selectedInstallPath()?.let { selectedPath ->
            if (blenderPathField.text != selectedPath) blenderPathField.text = selectedPath
        }
    }

    private fun emitBlenderPath() {
        emit { onBlenderPathChanged(blenderPathField.text) }
    }

    private fun emit(callback: () -> Unit) {
        if (!isRendering) callback()
    }

    private fun selectedBlenderLogLevel(): BlenderLogLevel {
        return blenderLogLevels.getOrNull(blenderLogLevelCombo.selectedIndex) ?: BlenderLogLevel.INFO
    }

    private fun selectBlenderLogLevel(logLevel: BlenderLogLevel) {
        blenderLogLevelCombo.selectedIndex = blenderLogLevels.indexOf(logLevel).takeIf { it >= 0 } ?: 0
    }

    private fun logLevelLabel(level: BlenderLogLevel): String {
        return MessageBundle.message("ui.toolwindow.group.environment.log.level.${level.name.lowercase()}")
    }

    private fun addDocumentListener(textComponent: JTextComponent, onChange: () -> Unit) {
        textComponent.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent?) = onChange()

                override fun removeUpdate(event: DocumentEvent?) = onChange()

                override fun changedUpdate(event: DocumentEvent?) = onChange()
            }
        )
    }

    private fun installRealtimeSymlinkNormalization(field: JTextField) {
        val document = field.document as? AbstractDocument ?: return
        document.documentFilter =
            object : DocumentFilter() {
                override fun insertString(filterBypass: FilterBypass, offset: Int, string: String?, attributes: AttributeSet?) {
                    super.insertString(filterBypass, offset, normalize(string), attributes)
                }

                override fun replace(
                    filterBypass: FilterBypass,
                    offset: Int,
                    length: Int,
                    text: String?,
                    attributes: AttributeSet?,
                ) {
                    super.replace(filterBypass, offset, length, normalize(text), attributes)
                }

                private fun normalize(value: String?): String? = value?.replace(' ', '_')?.replace('-', '_')
            }
    }
}
