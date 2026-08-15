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
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineField
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.ui.components.JBLabel
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.annotations.OptionTag
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandCatalog
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandLaunchRequest
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandLauncher
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandValidationIssue
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandValidator
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import javax.swing.JComponent

/** Persists the arguments entered for a Blender command run configuration. */
internal class BlenderCommandRunConfigurationOptions : RunConfigurationOptions() {
  @get:OptionTag("commandArguments") var commandArguments: String? by string()
}

/** Runs one persisted Blender `--command` invocation. */
internal class BlenderCommandRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<BlenderCommandRunConfigurationOptions>(project, factory, name) {
  internal var commandArguments: String
    get() = (options as BlenderCommandRunConfigurationOptions).commandArguments.orEmpty()
    set(value) {
      (options as BlenderCommandRunConfigurationOptions).commandArguments = value
    }

  override fun getConfigurationEditor(): SettingsEditor<BlenderCommandRunConfiguration> {
    return BlenderCommandSettingsEditor(project)
  }

  override fun checkConfiguration() {
    if (ProjectConfig.getInstance(project).getBlenderPath().isBlank()) {
      throw RuntimeConfigurationError(MessageBundle.message("run.configuration.blender.error.blender.path.empty"))
    }
    val parsedArguments = ParametersListUtil.parse(commandArguments.trim())
    BlenderCommandValidator.validate(parsedArguments)?.let { issue ->
      throw RuntimeConfigurationError(issue.toErrorMessage())
    }
  }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
    return object : CommandLineState(environment) {
      override fun startProcess(): OSProcessHandler {
        val projectConfig = ProjectConfig.getInstance(project)
        return BlenderCommandLauncher.getInstance(project)
            .start(
                BlenderCommandLaunchRequest(
                    blenderPath = projectConfig.getBlenderPath().trim(),
                    commandArguments = ParametersListUtil.parse(commandArguments.trim()),
                )
            )
      }
    }
  }
}

private fun BlenderCommandValidationIssue.toErrorMessage(): String {
  return when (this) {
    BlenderCommandValidationIssue.MissingCommand -> MessageBundle.message("run.configuration.blender.command.error.arguments.empty")
    BlenderCommandValidationIssue.DuplicateCommandOption ->
        MessageBundle.message("run.configuration.blender.command.error.option.duplicate")
    is BlenderCommandValidationIssue.MissingOptionValue ->
        MessageBundle.message("run.configuration.blender.command.error.option.value.missing", optionName)
    is BlenderCommandValidationIssue.MissingRequiredOption ->
        MessageBundle.message("run.configuration.blender.command.error.option.required", optionName)
    is BlenderCommandValidationIssue.MissingPositional ->
        MessageBundle.message("run.configuration.blender.command.error.positional.required", positionalName)
  }
}

/** Represents the command selector and arguments editor values stored in one persisted command line. */
internal data class BlenderCommandEditorState(
    val command: String,
    val arguments: String,
) {
  /** Combines the editor values into the existing persisted command-line format. */
  fun toCommandLine(): String {
    return listOf(command.trim(), arguments.trim()).filter(String::isNotEmpty).joinToString(" ")
  }

  companion object {
    /** Splits a persisted command line into its command identifier and remaining arguments. */
    fun fromCommandLine(commandLine: String): BlenderCommandEditorState {
      val trimmedCommandLine = commandLine.trim()
      val separatorIndex = trimmedCommandLine.indexOfFirst(Char::isWhitespace)
      if (separatorIndex < 0) {
        return BlenderCommandEditorState(trimmedCommandLine, "")
      }
      return BlenderCommandEditorState(
          command = trimmedCommandLine.substring(0, separatorIndex),
          arguments = trimmedCommandLine.substring(separatorIndex).trimStart(),
      )
    }
  }
}

/** Edits the persisted argument string for a Blender command configuration. */
private class BlenderCommandSettingsEditor(project: Project) : SettingsEditor<BlenderCommandRunConfiguration>() {
  private val completionModificationTracker = SimpleModificationTracker()
  private val commandField =
      ComboBox(BlenderCommandCatalog.commandIds.toTypedArray()).apply {
        isEditable = true
        addActionListener { completionModificationTracker.incModificationCount() }
      }
  private val commandLabel =
      JBLabel(MessageBundle.message("run.configuration.blender.command.command.label")).apply {
        labelFor = commandField
      }
  private val commandArgumentsField =
      CommandLineField(
          project,
          BlenderCommandLineInfo(::selectedCommand, completionModificationTracker),
          this,
      )
  private val commandArgumentsLabel =
      JBLabel(MessageBundle.message("run.configuration.blender.command.arguments.label")).apply {
        labelFor = commandArgumentsField
      }
  private val component =
      FormBuilder.createFormBuilder()
          .addLabeledComponent(commandLabel, commandField, 1, false)
          .addLabeledComponent(commandArgumentsLabel, commandArgumentsField, 1, false)
          .addComponentFillVertically(javax.swing.JPanel(), 0)
          .panel

  override fun resetEditorFrom(configuration: BlenderCommandRunConfiguration) {
    val editorState = BlenderCommandEditorState.fromCommandLine(configuration.commandArguments)
    commandField.selectedItem = editorState.command.ifBlank { BlenderCommandCatalog.commandIds.first() }
    commandArgumentsField.commandLine = editorState.arguments
  }

  override fun applyEditorTo(configuration: BlenderCommandRunConfiguration) {
    configuration.commandArguments =
        BlenderCommandEditorState(
                command = selectedCommand(),
                arguments = commandArgumentsField.commandLine,
            )
            .toCommandLine()
  }

  override fun createEditor(): JComponent = component

  private fun selectedCommand(): String {
    return commandField.editor.item?.toString().orEmpty()
  }
}
