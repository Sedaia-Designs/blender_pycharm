package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.ui.components.EnvironmentVariablesTable
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

internal class BlenderSettingsView(
  versionManagementComponent: JComponent,
  isValidMinorVersion: (String) -> Boolean,
) {
  private val blenderInstallPath = folderField()
  private val codeCompletionPath = folderField()
  private val logPath = folderField()
  private val downloadPath = TextFieldWithBrowseButton()
  private val clearDownloadsAfterInstall = JBCheckBox(
    MessageBundle.message("ui.settings.group.download.clear-after-install"),
  )
  private lateinit var minimumBlenderVersion: JBTextField
  private val globalEnvironmentVariables = EnvironmentVariablesTable()

  private val root = panel {
    group(MessageBundle.message("ui.settings.group.filepaths.title")) {
      row {
        cell(blenderInstallPath)
          .comment(MessageBundle.message("ui.settings.group.blender.comment"))
          .align(AlignX.FILL)
      }
      row {
        cell(codeCompletionPath)
          .comment(MessageBundle.message("ui.settings.group.code-completion.comment"))
          .align(AlignX.FILL)
      }
      row {
        cell(logPath)
          .comment(MessageBundle.message("ui.settings.group.log.comment"))
          .align(AlignX.FILL)
      }
      row {
        cell(downloadPath)
          .comment(MessageBundle.message("ui.settings.group.download.comment"))
          .align(AlignX.FILL)
      }
      row {
        cell(clearDownloadsAfterInstall)
          .align(AlignX.FILL)
      }
    }
    group(MessageBundle.message("ui.settings.group.versions.title")) {
      row(MessageBundle.message("ui.settings.group.versions.minimum-version.label")) {
        textField()
          .columns(8)
          .validationOnInput {
            if (isValidMinorVersion(it.text)) null
            else error(MessageBundle.message("ui.settings.group.versions.minimum-version.validation"))
          }
          .validationOnApply {
            if (isValidMinorVersion(it.text)) null
            else error(MessageBundle.message("ui.settings.group.versions.minimum-version.validation"))
          }
          .applyToComponent { minimumBlenderVersion = this }
          .comment(MessageBundle.message("ui.settings.group.versions.minimum-version.comment"))
      }
      row {
        cell(versionManagementComponent)
          .align(AlignX.FILL)
          .resizableColumn()
      }
    }
    group(MessageBundle.message("ui.settings.group.environment.variables.title")) {
      row {
        cell(globalEnvironmentVariables.component())
          .align(AlignX.FILL)
          .resizableColumn()
        contextHelp(MessageBundle.message("ui.settings.group.environment.variables.comment"))
      }.resizableRow()
    }
  }

  private val scrollPane = JBScrollPane(
    root,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
  ).apply {
    border = null
    verticalScrollBar.unitIncrement = 16
  }

  fun component(): JComponent = scrollPane

  fun renderForm(form: BlenderSettingsForm) {
    blenderInstallPath.text = form.blenderInstallPath
    codeCompletionPath.text = form.codeCompletionPath
    logPath.text = form.logPath
    downloadPath.text = form.downloadPath
    clearDownloadsAfterInstall.isSelected = form.clearDownloadsAfterInstall
    minimumBlenderVersion.text = form.minimumBlenderVersion
    globalEnvironmentVariables.setVariables(form.globalEnvironmentVariables)
  }

  fun readForm(): BlenderSettingsForm = BlenderSettingsForm(
    blenderInstallPath = blenderInstallPath.text,
    codeCompletionPath = codeCompletionPath.text,
    logPath = logPath.text,
    downloadPath = downloadPath.text,
    clearDownloadsAfterInstall = clearDownloadsAfterInstall.isSelected,
    minimumBlenderVersion = minimumBlenderVersion.text,
    globalEnvironmentVariables = globalEnvironmentVariables.getVariables(),
  )

  private fun folderField(): TextFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(
      TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()),
    )
  }
}
