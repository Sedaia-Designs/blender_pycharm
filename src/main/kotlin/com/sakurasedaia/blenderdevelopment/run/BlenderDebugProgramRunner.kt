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

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.sakurasedaia.blenderdevelopment.core.BlenderRuntimeCommandService
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

internal class BlenderDebugProgramRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "BlenderDebugProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is BlenderLaunchRunConfiguration
  }

  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val executionResult = state.execute(environment.executor, this)
    val processHandler = executionResult?.processHandler
    if (processHandler != null) {
      installReloadOnSaveListener(environment, processHandler)
    }
    return resolvedPromise(showRunContent(executionResult, environment))
  }

  private fun installReloadOnSaveListener(
      environment: ExecutionEnvironment,
      processHandler: com.intellij.execution.process.ProcessHandler,
  ) {
    val project = environment.project
    val connection = project.messageBus.connect()
    val projectConfig = ProjectConfig.getInstance(project)
    val runtimeCommandService = BlenderRuntimeCommandService.getInstance(project)

    connection.subscribe(
        FileDocumentManagerListener.TOPIC,
        object : FileDocumentManagerListener {
          override fun beforeDocumentSaving(document: com.intellij.openapi.editor.Document) {
            if (!projectConfig.getReloadOnSave()) {
              return
            }
            if (!runtimeCommandService.hasActiveSession()) {
              return
            }
            runtimeCommandService.sendReloadCommand(showSuccessNotification = false)
          }
        },
    )

    processHandler.addProcessListener(
        object : ProcessListener {
          override fun processTerminated(event: ProcessEvent) {
            connection.disconnect()
          }
        }
    )
  }
}
