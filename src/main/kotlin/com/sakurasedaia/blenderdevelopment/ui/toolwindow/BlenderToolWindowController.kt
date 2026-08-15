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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.openapi.application.EDT
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.util.PythonModuleNameValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class BlenderToolWindowController(
    scope: CoroutineScope,
    private val view: BlenderToolWindowView,
    private val projectConfig: ProjectConfig,
    private val pluginConfig: PluginConfig,
    private val scanInstallations: (onCompleted: () -> Unit) -> Unit,
    private val installStubs: (String) -> Unit,
    private val reloadAddon: () -> Unit,
    private val logAutosave: (String) -> Unit,
) {
  init {
    bindView()
    view.render(
        toViewState(
            projectConfig.stateFlow.value,
            pluginConfig.stateFlow.value,
        )
    )
    scope.launch(Dispatchers.EDT) {
      combine(
              projectConfig.stateFlow,
              pluginConfig.stateFlow,
              ::toViewState,
          )
          .collect(view::render)
    }
  }

  private fun bindView() {
    view.onBlenderPathChanged = { save("blenderPath") { projectConfig.setBlenderPath(it.trim()) } }
    view.onAddonSymlinkNameChanged = ::saveAddonSymlinkName
    view.onSourceFolderChanged = { save("sourceFolder") { projectConfig.setSourceFolder(it.trim()) } }
    view.onRunArgumentsChanged = { save("runArguments") { projectConfig.setRunArguments(it.trim()) } }
    view.onBlenderLogLevelChanged = { save("blenderLogLevel") { projectConfig.setBlenderLogLevel(it) } }
    view.onReloadOnSaveChanged = { save("reloadOnSave") { projectConfig.setReloadOnSave(it) } }
    view.onJustMyCodeChanged = { save("justMyCode") { projectConfig.setJustMyCode(it) } }
    view.onEnvironmentVariablesChanged = {
      save("environmentVariables") { projectConfig.setEnvironmentVariables(it) }
    }
    view.onScriptDirectoriesChanged = {
      save("scriptDirectories") { projectConfig.setScriptDirectories(it.ifEmpty { null }) }
    }
    view.onReloadRequested = reloadAddon
    view.onScanInstallationsRequested = ::scanForInstallations
    view.onInstallStubsRequested = installStubs
  }

  internal fun scanForInstallations() {
    val previousInstallations = pluginConfig.stateFlow.value.detectedBlenderInstalls

    scanInstallations {
      onInstallationScanCompleted(previousInstallations)
    }
  }

  private fun onInstallationScanCompleted(previousInstallations: List<PluginConfig.BlendInstallInfo>) {
    val updatedInstallations = pluginConfig.stateFlow.value.detectedBlenderInstalls
    val configuredPath = projectConfig.stateFlow.value.blenderPath

    val removeSelectedInstall =
        previousInstallations.any { it.path == configuredPath } && updatedInstallations.none { it.path == configuredPath }

    if (removeSelectedInstall) {
      save("blenderPath") {
        projectConfig.setBlenderPath(updatedInstallations.firstOrNull()?.path.orEmpty())
      }
    }
  }

  private fun saveAddonSymlinkName(candidate: String) {
    val normalizedCandidate = candidate.trim()
    if (!PythonModuleNameValidator.isValid(normalizedCandidate)) return
    save("addonSymlinkName") {
      projectConfig.setAddonSymlinkName(normalizedCandidate)
    }
  }

  private fun save(fieldName: String, update: () -> Unit) {
    update()
    logAutosave(fieldName)
  }

  private fun toViewState(
      projectState: ProjectConfig.ProjectSnapshot,
      pluginState: PluginConfig.PluginSnapshot,
  ): BlenderToolWindowState {
    return BlenderToolWindowState(
        blenderPath = projectState.blenderPath,
        detectedBlenderInstalls = pluginState.detectedBlenderInstalls,
        addonSymlinkName = projectState.addonSymlinkName,
        sourceFolder = projectState.sourceFolder,
        runArguments = projectState.runArguments,
        blenderLogLevel = projectState.blenderLogLevel,
        reloadOnSave = projectState.reloadOnSave,
        justMyCode = projectState.justMyCode,
        environmentVariables = projectState.environmentVariables,
        scriptDirectories = projectState.scriptDirectories.orEmpty(),
    )
  }
}
