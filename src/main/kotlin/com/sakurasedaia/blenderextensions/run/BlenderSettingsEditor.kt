package com.sakurasedaia.blenderextensions.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.sakurasedaia.blenderextensions.blender.services.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.common.utils.BlenderTaskManager
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * UI for editing Blender run configurations.
 */
class BlenderSettingsEditor(private val project: Project) : SettingsEditor<BlenderRunConfiguration>() {
    private val downloader = BlenderDownloader.getInstance(project)
    
    // UI Components
    private val myBlenderVersionComboBox = ComboBox<String>()
    private val myBlenderCommandField = JBTextField()
    private val myIsSandboxedCheckBox = JBCheckBox(LangManager.message("run.configuration.setting.sandboxed"))
    private val myImportUserConfigCheckBox = JBCheckBox(LangManager.message("run.configuration.setting.import.user.config"))
    private val myAddonSymlinkNameField = JBTextField()
    private val myAddonSourceDirectoryField = TextFieldWithBrowseButton()
    private val myAdditionalArgumentsField = JBTextField()
    private val myDownloadButton = JButton(LangManager.message("run.configuration.button.download"), BlenderIcons.Install)

    // Labeled Components
    private val myBlenderCommandComponent = LabeledComponent.create(myBlenderCommandField, "${LangManager.message("run.configuration.setting.cli.args")}:")
    private val myAddonSymlinkComponent = LabeledComponent.create(myAddonSymlinkNameField, "${LangManager.message("run.configuration.setting.symlink.name")}:")
    private val myAddonSourceDirComponent = LabeledComponent.create(myAddonSourceDirectoryField, "${LangManager.message("run.configuration.setting.src")}:")
    private val myArgumentsComponent = LabeledComponent.create(myAdditionalArgumentsField, "${LangManager.message("run.configuration.setting.cli.args")}:")

    init {
        // Initialize version combo box
        val versions = BlenderVersions.getAllSelectableVersions()
        myBlenderVersionComboBox.model = DefaultComboBoxModel(versions.toTypedArray())
        
        // Setup download button action
        myDownloadButton.addActionListener {
            val selected = myBlenderVersionComboBox.selectedItem as? String ?: return@addActionListener
            if (!downloader.isDownloaded(selected)) {
                BlenderTaskManager.getInstance().run(project, LangManager.message("action.download.blender.task", selected)) {
                    downloader.getOrDownloadBlenderPath(selected)
                    SwingUtilities.invokeLater {
                        updateDownloadButtonVisibility()
                    }
                }
            }
        }
    }

    /**
     * Updates the visibility of the download button based on whether the selected version is already downloaded.
     */
    private fun updateDownloadButtonVisibility() {
        val selected = myBlenderVersionComboBox.selectedItem as? String
        myDownloadButton.isVisible = !downloader.isDownloaded(selected)
    }

    /**
     * Resets the editor UI from the given run configuration settings.
     * Also handles visibility of fields based on the configuration factory type.
     */
    override fun resetEditorFrom(s: BlenderRunConfiguration) {
        val options = s.getOptions()
        myBlenderVersionComboBox.selectedItem = options.blenderVersion ?: "5.0"
        updateDownloadButtonVisibility()
        myIsSandboxedCheckBox.isSelected = options.isSandboxed
        myImportUserConfigCheckBox.isSelected = options.importUserConfig
        myBlenderCommandField.text = options.blenderCommand ?: ""
        myAddonSymlinkNameField.text = options.addonSymlinkName ?: ""
        myAddonSourceDirectoryField.text = options.addonSourceDirectory ?: ""
        myAdditionalArgumentsField.text = options.additionalArguments ?: ""

        // Adjust visibility based on the factory type
        val factory = s.factory
        val isTesting = factory is BlenderStartBlenderConfigurationFactory
        val isCommand = factory is BlenderCommandConfigurationFactory
        val isBuildOrValidate = factory is BlenderBuildConfigurationFactory || factory is BlenderValidateConfigurationFactory

        myIsSandboxedCheckBox.isVisible = isTesting
        myImportUserConfigCheckBox.isVisible = isTesting
        myAddonSymlinkComponent.isVisible = isTesting
        myAddonSourceDirComponent.isVisible = isTesting
        myArgumentsComponent.isVisible = isTesting
        
        myBlenderCommandComponent.isVisible = isCommand || isBuildOrValidate
        myBlenderCommandField.isEnabled = isCommand
    }

    /**
     * Applies the current UI state to the run configuration settings.
     */
    override fun applyEditorTo(s: BlenderRunConfiguration) {
        val options = s.getOptions()
        options.blenderVersion = myBlenderVersionComboBox.selectedItem as? String
        options.isSandboxed = myIsSandboxedCheckBox.isSelected
        options.importUserConfig = myImportUserConfigCheckBox.isSelected
        options.blenderCommand = myBlenderCommandField.text
        options.addonSymlinkName = myAddonSymlinkNameField.text
        options.addonSourceDirectory = myAddonSourceDirectoryField.text
        options.additionalArguments = myAdditionalArgumentsField.text
    }

    /**
     * Creates the main editor panel.
     */
    override fun createEditor(): JComponent {
        myAddonSourceDirectoryField.addBrowseFolderListener(
            com.intellij.openapi.ui.TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project
            )
        )

        return panel {
            row("${LangManager.message("run.configuration.form.version")}:") {
                cell(myBlenderVersionComboBox).align(AlignX.FILL).resizableColumn()
                cell(myDownloadButton)
            }
            row {
                cell(myBlenderCommandComponent).align(AlignX.FILL)
            }
            row {
                cell(myIsSandboxedCheckBox)
            }
            row {
                cell(myImportUserConfigCheckBox)
            }
            row {
                cell(myAddonSymlinkComponent).align(AlignX.FILL)
            }
            row {
                cell(myAddonSourceDirComponent).align(AlignX.FILL)
            }
            row {
                cell(myArgumentsComponent).align(AlignX.FILL)
            }
        }
    }
}
