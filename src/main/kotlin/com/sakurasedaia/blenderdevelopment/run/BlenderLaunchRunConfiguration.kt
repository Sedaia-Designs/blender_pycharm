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

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.core.BlenderDebugAttachService
import com.sakurasedaia.blenderdevelopment.core.BlenderPythonLaunchRequest
import com.sakurasedaia.blenderdevelopment.core.BlenderPythonLauncher
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import javax.swing.JComponent
import javax.swing.JPanel

/** Launches Blender using the project-scoped runtime settings from [ProjectConfig]. */
internal class BlenderLaunchRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<Any?>(project, factory, name) {

  override fun getConfigurationEditor(): SettingsEditor<BlenderLaunchRunConfiguration> =
      object : SettingsEditor<BlenderLaunchRunConfiguration>() {
        override fun resetEditorFrom(configuration: BlenderLaunchRunConfiguration) = Unit

        override fun applyEditorTo(configuration: BlenderLaunchRunConfiguration) = Unit

        override fun createEditor(): JComponent = JPanel()
      }

  override fun checkConfiguration() {
    val blenderPath = ProjectConfig.getInstance(project).getBlenderPath().trim()
    if (blenderPath.isEmpty()) {
      throw RuntimeConfigurationError(MessageBundle.message("run.configuration.blender.error.blender.path.empty"))
    }
  }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
    val shouldAttachDebugger = executor.id == DefaultDebugExecutor.EXECUTOR_ID
    return object : CommandLineState(environment) {
      override fun startProcess(): OSProcessHandler {
        return BlenderPythonLauncher.getInstance(project)
            .start(
                BlenderPythonLaunchRequest(
                    blenderPath = ProjectConfig.getInstance(project).getBlenderPath().trim(),
                    debugger = shouldAttachDebugger,
                )
            )
      }

      override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val executionResult = super.execute(executor, runner)
        if (shouldAttachDebugger) {
          val sessionIdentifier = executionResult.processHandler.getUserData(BlenderPythonLauncher.LAUNCH_SESSION_IDENTIFIER_KEY)
          if (!sessionIdentifier.isNullOrBlank()) {
            BlenderDebugAttachService.getInstance(project)
                .scheduleAttach(
                    environment = environment,
                    executionResult = executionResult,
                    sessionIdentifier = sessionIdentifier,
                )
          }
        }
        return executionResult
      }
    }
  }
}
