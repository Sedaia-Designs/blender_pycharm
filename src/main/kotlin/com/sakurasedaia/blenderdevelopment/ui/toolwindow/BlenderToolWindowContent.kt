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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderdevelopment.core.BlenderRuntimeCommandService
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.ui.components.EnvironmentVariablesTable
import com.sakurasedaia.blenderdevelopment.ui.components.ScriptDirectoriesTable
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import com.sakurasedaia.blenderdevelopment.stubs.BlenderStubInstallationService
import com.sakurasedaia.blenderdevelopment.stubs.BlenderStubRequirementResolver
import com.sakurasedaia.blenderdevelopment.util.PythonModuleNameValidator
import com.jetbrains.python.sdk.PythonSdkUtil
import kotlinx.coroutines.runBlocking
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JButton
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

/** Builds the Blender tool window UI for editing workspace configuration values. */
class BlenderToolWindowContent(
  private val project: Project,
  private val onScanInstallations: (onCompleted: () -> Unit) -> Unit,
) {
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
    val logger = PluginLogger.getInstance(project)
    val runtimeCommandService = BlenderRuntimeCommandService.getInstance(project)

    lateinit var blenderPathField: TextFieldWithBrowseButton
    lateinit var addonSymlinkField: JTextField
    lateinit var sourceFolderField: JTextField
    lateinit var runArgumentsField: JTextField
    lateinit var blenderLogLevelCombo: JComboBox<String>
    lateinit var reloadOnSaveCheckBox: JCheckBox
    lateinit var justMyCodeCheckBox: JCheckBox
    lateinit var extensionsRepositoryField: JTextField
    lateinit var useCustomBlenderInstall: JCheckBox
    lateinit var availableBlenderInstalls: JComboBox<String>
    lateinit var blenderVersionCombo: JComboBox<String>
    lateinit var updateStubsButton: JButton
    val environmentVariablesTable = EnvironmentVariablesTable()
    val scriptDirectoriesTable = ScriptDirectoriesTable(project)
    val blenderLogLevels = BlenderLogLevel.entries
    val uiState = UiState()
    val uiStateGraph = PropertyGraph()
    val showStubUpdateProperty: GraphProperty<Boolean> = uiStateGraph.property(false)
    var isLoadingFromConfig = false
    lateinit var loadFromConfig: () -> Unit

    fun logLevelLabel(level: BlenderLogLevel): String {
      return when (level) {
        BlenderLogLevel.FATAL -> MessageBundle.message("ui.toolwindow.group.environment.log.level.fatal")
        BlenderLogLevel.ERROR -> MessageBundle.message("ui.toolwindow.group.environment.log.level.error")
        BlenderLogLevel.WARNING -> MessageBundle.message("ui.toolwindow.group.environment.log.level.warning")
        BlenderLogLevel.INFO -> MessageBundle.message("ui.toolwindow.group.environment.log.level.info")
        BlenderLogLevel.DEBUG -> MessageBundle.message("ui.toolwindow.group.environment.log.level.debug")
        BlenderLogLevel.TRACE -> MessageBundle.message("ui.toolwindow.group.environment.log.level.trace")
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

    fun autosaveField(fieldName: String, write: () -> Unit) {
      if (isLoadingFromConfig) return
      write()
      logger.debug("Autosaved `$fieldName` from Blender tool window.")
    }

    fun autosaveBlenderPath() = autosaveField("blenderPath") {
      config.setBlenderPath(blenderPathField.text.trim())
    }

    fun autosaveAddonSymlinkName() {
      val candidate = addonSymlinkField.text.trim()
      if (!PythonModuleNameValidator.isValid(candidate)) {
        logger.debug("Skipped autosave for `addonSymlinkName`: value is not a valid Python module name.")
        return
      }
      autosaveField("addonSymlinkName") {
        config.setAddonSymlinkName(candidate)
      }
    }

    fun autosaveSourceFolder() = autosaveField("sourceFolder") {
      config.setSourceFolder(sourceFolderField.text.trim())
    }

    fun autosaveRunArguments() = autosaveField("runArguments") {
      config.setRunArguments(runArgumentsField.text.trim())
    }

    fun autosaveBlenderLogLevel() = autosaveField("blenderLogLevel") {
      config.setBlenderLogLevel(getSelectedBlenderLogLevel())
    }

    fun autosaveReloadOnSave() = autosaveField("reloadOnSave") {
      config.setReloadOnSave(reloadOnSaveCheckBox.isSelected)
    }

    fun autosaveJustMyCode() = autosaveField("justMyCode") {
      config.setJustMyCode(justMyCodeCheckBox.isSelected)
    }

    fun autosaveExtensionsRepository() = autosaveField("extensionsRepository") {
      val candidate = extensionsRepositoryField.text.trim()
      if (!PythonModuleNameValidator.isValid(candidate)) {
        logger.debug("Skipped autosave for `extensionsRepository`: value is not a valid Python module name.")
        return@autosaveField
      }
      config.setExtensionsRepository(candidate)
    }

    fun autosaveEnvironmentVariables() = autosaveField("environmentVariables") {
      config.setEnvironmentVariables(environmentVariablesTable.getVariables())
    }

    fun autosaveScriptDirectories() = autosaveField("scriptDirectories") {
      config.setScriptDirectories(scriptDirectoriesTable.getDirectories().ifEmpty { null })
    }

    environmentVariablesTable.setOnChangeListener(::autosaveEnvironmentVariables)
    scriptDirectoriesTable.setOnChangeListener(::autosaveScriptDirectories)

    fun addAutosaveListener(textComponent: JTextComponent, onChange: () -> Unit) {
      textComponent.document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = onChange()
        override fun removeUpdate(e: DocumentEvent?) = onChange()
        override fun changedUpdate(e: DocumentEvent?) = onChange()
      })
    }

    fun installRealtimeSymlinkNormalization(field: JTextField) {
      val document = field.document as? AbstractDocument ?: return
      document.documentFilter = object : DocumentFilter() {
        fun normalized(value: String?): String? {
          if (value == null) return null
          return value.replace(' ', '_').replace('-', '_')
        }

        override fun insertString(
          fb: FilterBypass,
          offset: Int,
          string: String?,
          attr: AttributeSet?,
        ) {
          super.insertString(fb, offset, normalized(string), attr)
        }

        override fun replace(
          fb: FilterBypass,
          offset: Int,
          length: Int,
          text: String?,
          attrs: AttributeSet?,
        ) {
          super.replace(fb, offset, length, normalized(text), attrs)
        }
      }
    }

    fun updateInstallControlState() {
      val useCustomPath = useCustomBlenderInstall.isSelected
      blenderPathField.isEnabled = useCustomPath
      availableBlenderInstalls.isEnabled = !useCustomPath && uiState.detectedBlenderInstalls.isNotEmpty()
    }

    /** Updates whether the version-specific stub replacement action should be offered. */
    fun updateStubControlState() {
      val selectedVersion = blenderVersionCombo.selectedItem as? String ?: return
      val targetRequirement = BlenderStubRequirementResolver.resolve(selectedVersion)
      val installedRequirement = config.getInstalledStubRequirement().takeIf(String::isNotBlank)
      showStubUpdateProperty.set(targetRequirement == null || targetRequirement != installedRequirement)
    }

    /** Saves the target Blender version and refreshes the conditional update action. */
    fun autosaveBlenderVersion() = autosaveField("blenderVersion") {
      val selectedVersion = blenderVersionCombo.selectedItem as? String ?: return@autosaveField
      config.setBlenderVersion(selectedVersion)
      updateStubControlState()
    }

    /** Replaces the previous linting package on a pooled thread and refreshes the action state. */
    fun replaceBlenderStubs() {
      val notifications = NotificationModal.getInstance(project)
      val module = ModuleManager.getInstance(project).modules.firstOrNull()
      val sdk = module?.let(PythonSdkUtil::findPythonSdk)
      if (module == null || sdk == null) {
        notifications.sendError(MessageBundle.message("notification.blender.stubs.sdk.missing"))
        return
      }
      val selectedVersion = blenderVersionCombo.selectedItem as? String ?: return
      updateStubsButton.isEnabled = false
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          runBlocking {
            BlenderStubInstallationService.getInstance(project)
              .replaceForChangedVersion(module, sdk, selectedVersion)
          }
        } catch (exception: Exception) {
          logger.warn("Blender API stub replacement failed unexpectedly.", exception)
          notifications.sendError(
            MessageBundle.message(
              "notification.blender.stubs.update.failed",
              exception.message ?: exception.javaClass.simpleName,
            ),
          )
        } finally {
          ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
              updateStubsButton.isEnabled = true
              updateStubControlState()
            }
          }
        }
      }
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
          previousSelectedPath != null -> uiState.detectedBlenderInstalls.indexOfFirst { it.path == previousSelectedPath }
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
    loadFromConfig = {
      isLoadingFromConfig = true
      addonSymlinkField.text = config.getAddonSymlinkName()
      sourceFolderField.text = config.getSourceFolder()
      runArgumentsField.text = config.getRunArguments()
      selectBlenderLogLevel(config.getBlenderLogLevel())
      reloadOnSaveCheckBox.isSelected = config.getReloadOnSave()
      justMyCodeCheckBox.isSelected = config.getJustMyCode()
      extensionsRepositoryField.text = config.getExtensionsRepository()
      environmentVariablesTable.setVariables(config.getEnvironmentVariables())
      scriptDirectoriesTable.setDirectories(config.getScriptDirectories().orEmpty())

      val configuredBlenderVersion = BlenderVersions.normalizeVersionFromList(config.getBlenderVersion())
      blenderVersionCombo.selectedItem = configuredBlenderVersion

      refreshInstallWidgetsFromPluginState()
      applyBlenderInstallSelectionFromProjectConfig()
      updateStubControlState()
      isLoadingFromConfig = false
    }

    val contentPanel = panel {
      /*
      row {
        button(MessageBundle.message("ui.toolwindow.group.runtime.script")) {
          runtimeCommandService.sendRunScriptCommand()
        } // TODO: Make it's own Run Configuration
      }
      */
      group(MessageBundle.message("ui.toolwindow.group.executable.title")) {
        val useCustomBlenderInstallProperty: GraphProperty<Boolean> = uiStateGraph.property(false)
        row(MessageBundle.message("ui.toolwindow.group.executable.target-version")) {
          comboBox(BlenderVersions.LIST.map { it.blMajorMinor })
            .align(AlignX.FILL)
            .applyToComponent {
              blenderVersionCombo = this
              addActionListener { autosaveBlenderVersion() }
            }
        }
        row {
          comboBox(emptyList<String>())
            .align(AlignX.FILL)
            .applyToComponent {
              availableBlenderInstalls = this
              addActionListener {
                syncBlenderPathFromInstallSelection()
                autosaveBlenderPath()
              }
            }
        }.visibleIf(useCustomBlenderInstallProperty.equalsTo(false))
        row {
          textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
            .align(AlignX.FILL)
            .applyToComponent {
              blenderPathField = this
              addAutosaveListener(textField, ::autosaveBlenderPath)
            }
        }.visibleIf(useCustomBlenderInstallProperty.equalsTo(true))
        row {
          checkBox(MessageBundle.message("ui.toolwindow.group.blender.path-use-custom"))
            .bindSelected(useCustomBlenderInstallProperty)
            .applyToComponent {
              useCustomBlenderInstall = this
              addActionListener {
                updateInstallControlState()
                autosaveBlenderPath()
              }
          }
          button(MessageBundle.message("ui.toolwindow.group.executable.scan-for-install")) {
            onScanInstallations {
              refreshInstallWidgetsFromPluginState()
              autosaveBlenderPath()
            }
          }
        }
        row {
          button(MessageBundle.message("ui.toolwindow.group.executable.update-stubs")) {
            replaceBlenderStubs()
          }.applyToComponent {
            updateStubsButton = this
          }
        }.visibleIf(showStubUpdateProperty)
      }

      group(MessageBundle.message("ui.toolwindow.group.debugger.title")) {
        row {
          checkBox(MessageBundle.message("ui.toolwindow.group.debugger.reload-on-save"))
            .applyToComponent {
              reloadOnSaveCheckBox = this
              addActionListener { autosaveReloadOnSave() }
            }
          checkBox(MessageBundle.message("ui.toolwindow.group.debugger.just-my-code"))
            .applyToComponent {
              justMyCodeCheckBox = this
              addActionListener { autosaveJustMyCode() }
            }
        }
        row {
          button(MessageBundle.message("ui.toolwindow.group.debugger.reload")) {
            runtimeCommandService.sendReloadCommand()
          }
        }
      }
      group("Environment Settings") {
        row(MessageBundle.message("ui.toolwindow.group.environment.addon-symlink-name")) {
          textField()
            .align(AlignX.FILL)
            .validationOnInput {
              if (!PythonModuleNameValidator.isValid(it.text.trim())) {
                error(MessageBundle.message("ui.common.python.module.name.validation"))
              } else {
                null
              }
            }
            .applyToComponent {
              addonSymlinkField = this
              installRealtimeSymlinkNormalization(this)
              addAutosaveListener(this, ::autosaveAddonSymlinkName)
            }
        }
        row(MessageBundle.message("ui.toolwindow.group.environment.source-folder")) {
          textField()
            .align(AlignX.FILL)
            .applyToComponent {
              sourceFolderField = this
              addAutosaveListener(this, ::autosaveSourceFolder)
            }
        }
        row(MessageBundle.message("ui.toolwindow.group.environment.log-level")) {
          comboBox(blenderLogLevels.map(::logLevelLabel))
            .align(AlignX.FILL)
            .applyToComponent {
              blenderLogLevelCombo = this
              addActionListener { autosaveBlenderLogLevel() }
            }
        }
        row(MessageBundle.message("ui.toolwindow.group.environment.extensions-repository")) {
          textField()
            .align(AlignX.FILL)
            .validationOnInput {
              if (!PythonModuleNameValidator.isValid(it.text.trim())) {
                error(MessageBundle.message("ui.common.python.module.name.validation"))
              } else {
                null
              }
            }
            .applyToComponent {
              extensionsRepositoryField = this
              addAutosaveListener(this, ::autosaveExtensionsRepository)
            }
        }
        row(MessageBundle.message("ui.toolwindow.group.environment.run-arguments")) {
          textField()
            .align(AlignX.FILL)
            .applyToComponent {
              runArgumentsField = this
              addAutosaveListener(this, ::autosaveRunArguments)
            }
        }
        row {
          label(MessageBundle.message("ui.toolwindow.group.environment.script-directories"))
        }
        row {
          cell(scriptDirectoriesTable.component())
            .align(AlignX.FILL)
            .resizableColumn()
        }
        row {
          label(MessageBundle.message("ui.toolwindow.group.environment.title"))
          contextHelp(MessageBundle.message("ui.toolwindow.group.environment.variables.comment"))
        }
        row {
          cell(environmentVariablesTable.component())
            .align(AlignX.FILL)
            .resizableColumn()
        }
      }
    }.apply {
      border = JBUI.Borders.empty(8, 10)
      loadFromConfig()
    }

    return JBScrollPane(contentPanel).apply {
      horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()
    }
  }
}
