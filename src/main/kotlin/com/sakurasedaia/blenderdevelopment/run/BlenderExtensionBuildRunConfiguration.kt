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

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.annotations.OptionTag
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandLaunchRequest
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandLauncher
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import javax.swing.JComponent

/** Identifies the extension packaging operation performed by a build run configuration. */
internal enum class BlenderExtensionBuildOperation {
  BUILD,
  VALIDATE,
}

/** Persists one Blender extension build or validation workflow. */
internal class BlenderExtensionBuildRunConfigurationOptions : RunConfigurationOptions() {
  @get:OptionTag("operation")
  var operation: String? by string(BlenderExtensionBuildOperation.BUILD.name)

  @get:OptionTag("sourcePath")
  var sourcePath: String? by string()

  @get:OptionTag("outputDirectory")
  var outputDirectory: String? by string()
}

/** Converts typed extension build settings into Blender `extension` command arguments. */
internal object BlenderExtensionBuildCommand {
  /** Builds the arguments following Blender's automatically supplied `--command` option. */
  fun arguments(
    operation: BlenderExtensionBuildOperation,
    sourcePath: String,
    outputDirectory: String,
  ): List<String> {
    return when (operation) {
      BlenderExtensionBuildOperation.BUILD -> listOf(
        "extension",
        "build",
        "--source-dir",
        sourcePath,
        "--output-dir",
        outputDirectory,
      )
      BlenderExtensionBuildOperation.VALIDATE -> listOf("extension", "validate", sourcePath)
    }
  }
}

/** Runs Blender extension build and validation commands from typed path settings. */
internal class BlenderExtensionBuildRunConfiguration(
  project: Project,
  factory: ConfigurationFactory,
  name: String,
) : RunConfigurationBase<BlenderExtensionBuildRunConfigurationOptions>(project, factory, name) {
  internal var operation: BlenderExtensionBuildOperation
    get() {
      val persistedName = (options as BlenderExtensionBuildRunConfigurationOptions).operation
      return BlenderExtensionBuildOperation.entries.firstOrNull { it.name == persistedName }
        ?: BlenderExtensionBuildOperation.BUILD
    }
    set(value) {
      (options as BlenderExtensionBuildRunConfigurationOptions).operation = value.name
    }

  internal var sourcePath: String
    get() = (options as BlenderExtensionBuildRunConfigurationOptions).sourcePath.orEmpty()
    set(value) {
      (options as BlenderExtensionBuildRunConfigurationOptions).sourcePath = value
    }

  internal var outputDirectory: String
    get() = (options as BlenderExtensionBuildRunConfigurationOptions).outputDirectory.orEmpty()
    set(value) {
      (options as BlenderExtensionBuildRunConfigurationOptions).outputDirectory = value
    }

  override fun getConfigurationEditor(): SettingsEditor<BlenderExtensionBuildRunConfiguration> {
    return BlenderExtensionBuildSettingsEditor(project)
  }

  override fun checkConfiguration() {
    if (ProjectConfig.getInstance(project).getBlenderPath().isBlank()) {
      throw RuntimeConfigurationError(
        MessageBundle.message("run.configuration.blender.error.blender.path.empty")
      )
    }
    if (sourcePath.isBlank()) {
      throw RuntimeConfigurationError(
        MessageBundle.message("run.configuration.blender.extension.build.error.source.empty")
      )
    }
    if (operation == BlenderExtensionBuildOperation.BUILD && outputDirectory.isBlank()) {
      throw RuntimeConfigurationError(
        MessageBundle.message("run.configuration.blender.extension.build.error.output.empty")
      )
    }
  }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
    return object : CommandLineState(environment) {
      override fun startProcess(): OSProcessHandler {
        return BlenderCommandLauncher.getInstance(project).start(
          BlenderCommandLaunchRequest(
            blenderPath = ProjectConfig.getInstance(project).getBlenderPath().trim(),
            commandArguments = BlenderExtensionBuildCommand.arguments(
              operation = operation,
              sourcePath = sourcePath.trim(),
              outputDirectory = outputDirectory.trim(),
            ),
          )
        )
      }
    }
  }
}

/** Edits the operation and paths for an extension build run configuration. */
private class BlenderExtensionBuildSettingsEditor(project: Project) :
  SettingsEditor<BlenderExtensionBuildRunConfiguration>() {
  private val operationField = ComboBox(BlenderExtensionBuildOperation.entries.toTypedArray()).apply {
    renderer = textListCellRenderer { operation -> operation.presentableName() }
  }
  private val operationLabel = JBLabel(
    MessageBundle.message("run.configuration.blender.extension.build.operation.label")
  ).apply {
    labelFor = operationField
  }
  private val sourcePathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
  }
  private val sourcePathLabel = JBLabel(
    MessageBundle.message("run.configuration.blender.extension.build.source.label")
  ).apply {
    labelFor = sourcePathField.textField
  }
  private val outputDirectoryField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
  }
  private val outputDirectoryLabel = JBLabel(
    MessageBundle.message("run.configuration.blender.extension.build.output.label")
  ).apply {
    labelFor = outputDirectoryField.textField
  }
  private val component = FormBuilder.createFormBuilder()
    .addLabeledComponent(operationLabel, operationField, 1, false)
    .addLabeledComponent(sourcePathLabel, sourcePathField, 1, false)
    .addLabeledComponent(outputDirectoryLabel, outputDirectoryField, 1, false)
    .addComponentFillVertically(javax.swing.JPanel(), 0)
    .panel

  init {
    operationField.addActionListener { updateOutputVisibility() }
  }

  override fun resetEditorFrom(configuration: BlenderExtensionBuildRunConfiguration) {
    operationField.selectedItem = configuration.operation
    sourcePathField.text = configuration.sourcePath
    outputDirectoryField.text = configuration.outputDirectory
    updateOutputVisibility()
  }

  override fun applyEditorTo(configuration: BlenderExtensionBuildRunConfiguration) {
    configuration.operation = selectedOperation()
    configuration.sourcePath = sourcePathField.text.trim()
    configuration.outputDirectory = outputDirectoryField.text.trim()
  }

  override fun createEditor(): JComponent = component

  private fun selectedOperation(): BlenderExtensionBuildOperation {
    return operationField.selectedItem as? BlenderExtensionBuildOperation ?: BlenderExtensionBuildOperation.BUILD
  }

  private fun updateOutputVisibility() {
    val outputVisible = selectedOperation() == BlenderExtensionBuildOperation.BUILD
    outputDirectoryLabel.isVisible = outputVisible
    outputDirectoryField.isVisible = outputVisible
  }
}

private fun BlenderExtensionBuildOperation.presentableName(): String {
  return when (this) {
    BlenderExtensionBuildOperation.BUILD ->
      MessageBundle.message("run.configuration.blender.extension.build.operation.build")
    BlenderExtensionBuildOperation.VALIDATE ->
      MessageBundle.message("run.configuration.blender.extension.build.operation.validate")
  }
}
