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
import com.intellij.util.ui.FormBuilder
import com.sakurasedaia.blenderextensions.blender.services.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class BlenderSettingsEditor(private val project: Project) : SettingsEditor<BlenderRunConfiguration>() {
    private val downloader = BlenderDownloader.getInstance(project)
    private val myBlenderVersionComboBox = ComboBox<String>()
    private val myBlenderCommandField = JBTextField()
    private val myIsSandboxedCheckBox = JBCheckBox(LangManager.message("run.configuration.setting.sandboxed"))
    private val myImportUserConfigCheckBox = JBCheckBox(LangManager.message("run.configuration.setting.import.user.config"))
    private val myAddonSymlinkNameField = JBTextField()
    private val myAddonSourceDirectoryField = TextFieldWithBrowseButton()
    private val myAdditionalArgumentsField = JBTextField()
    private val myDownloadButton = JButton(LangManager.message("run.configuration.button.download"), BlenderIcons.Install)

    private val myBlenderCommandComponent = LabeledComponent.create(myBlenderCommandField, "${LangManager.message("run.configuration.setting.cli.args")}:")
    private val myAddonSymlinkComponent = LabeledComponent.create(myAddonSymlinkNameField, "${LangManager.message("run.configuration.setting.symlink.name")}:")
    private val myAddonSourceDirComponent = LabeledComponent.create(myAddonSourceDirectoryField, "${LangManager.message("run.configuration.setting.src")}:")
    private val myArgumentsComponent = LabeledComponent.create(myAdditionalArgumentsField, "${LangManager.message("run.configuration.setting.cli.args")}:")

    init {
        val versions = BlenderVersions.getAllSelectableVersions()
        myBlenderVersionComboBox.model = DefaultComboBoxModel(versions.toTypedArray())
        
        myDownloadButton.addActionListener {
            val selected = myBlenderVersionComboBox.selectedItem as? String ?: return@addActionListener
            if (!downloader.isDownloaded(selected)) {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, LangManager.message("action.download.blender.task", selected)) {
                    override fun run(indicator: ProgressIndicator) {
                        downloader.getOrDownloadBlenderPath(selected)
                        SwingUtilities.invokeLater {
                            updateDownloadButtonVisibility()
                        }
                    }
                })
            }
        }
    }

    private fun updateDownloadButtonVisibility() {
        val selected = myBlenderVersionComboBox.selectedItem as? String
        myDownloadButton.isVisible = !downloader.isDownloaded(selected)
    }

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

    override fun createEditor(): JComponent {
        myAddonSourceDirectoryField.addBrowseFolderListener(
            com.intellij.openapi.ui.TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project
            )
        )

        val versionPanel = JPanel(BorderLayout())
        versionPanel.add(myBlenderVersionComboBox, BorderLayout.CENTER)
        versionPanel.add(myDownloadButton, BorderLayout.EAST)

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("${LangManager.message("run.configuration.form.version")}:", versionPanel)
            .addComponent(myBlenderCommandComponent)
            .addComponent(myIsSandboxedCheckBox)
            .addComponent(myImportUserConfigCheckBox)
            .addComponent(myAddonSymlinkComponent)
            .addComponent(myAddonSourceDirComponent)
            .addComponent(myArgumentsComponent)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}
