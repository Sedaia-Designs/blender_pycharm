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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class BlenderToolWindowController(
  private val scope: CoroutineScope,
  private val view: BlenderToolWindowView,
  private val config: ProjectConfig,
  initialInstallations: List<PluginConfig.BlendInstallInfo>,
  private val scanInstallations: (onCompleted: () -> Unit) -> Unit,
  private val detectedInstallations: () -> List<PluginConfig.BlendInstallInfo>,
  private val reloadAddon: () -> Unit,
  private val logAutosave: (String) -> Unit,
) {
  private val installations = MutableStateFlow(initialInstallations.toList())

  init {
    bindView()
    view.render(toViewState(config.stateFlow.value, installations.value))
    scope.launch(Dispatchers.EDT) {
      combine(config.stateFlow, installations, ::toViewState).collect(view::render)
    }
  }

  private fun bindView() {
    view.onBlenderPathChanged = { save("blenderPath") { config.setBlenderPath(it.trim()) } }
    view.onAddonSymlinkNameChanged = ::saveAddonSymlinkName
    view.onSourceFolderChanged = { save("sourceFolder") { config.setSourceFolder(it.trim()) } }
    view.onRunArgumentsChanged = { save("runArguments") { config.setRunArguments(it.trim()) } }
    view.onBlenderLogLevelChanged = { save("blenderLogLevel") { config.setBlenderLogLevel(it) } }
    view.onReloadOnSaveChanged = { save("reloadOnSave") { config.setReloadOnSave(it) } }
    view.onJustMyCodeChanged = { save("justMyCode") { config.setJustMyCode(it) } }
    view.onExtensionsRepositoryChanged = ::saveExtensionsRepository
    view.onEnvironmentVariablesChanged = {
      save("environmentVariables") { config.setEnvironmentVariables(it) }
    }
    view.onScriptDirectoriesChanged = {
      save("scriptDirectories") { config.setScriptDirectories(it.ifEmpty { null }) }
    }
    view.onReloadRequested = reloadAddon
    view.onScanInstallationsRequested = ::scanForInstallations
  }

  internal fun scanForInstallations() {
    scanInstallations(::onInstallationScanCompleted)
  }

  private fun onInstallationScanCompleted() {
    val previousInstallations = installations.value
    val updatedInstallations = detectedInstallations().toList()
    val configuredPath = config.stateFlow.value.blenderPath
    installations.value = updatedInstallations

    val removedSelectedInstall = previousInstallations.any { it.path == configuredPath } &&
      updatedInstallations.none { it.path == configuredPath }
    if (removedSelectedInstall) {
      save("blenderPath") {
        config.setBlenderPath(updatedInstallations.firstOrNull()?.path.orEmpty())
      }
    }
  }

  private fun saveAddonSymlinkName(candidate: String) {
    val normalizedCandidate = candidate.trim()
    if (!PythonModuleNameValidator.isValid(normalizedCandidate)) return
    save("addonSymlinkName") {
      config.setAddonSymlinkName(normalizedCandidate)
    }
  }

  private fun saveExtensionsRepository(candidate: String) {
    val normalizedCandidate = candidate.trim()
    if (!PythonModuleNameValidator.isValid(normalizedCandidate)) return
    save("extensionsRepository") {
      config.setExtensionsRepository(normalizedCandidate)
    }
  }

  private fun save(fieldName: String, update: () -> Unit) {
    update()
    logAutosave(fieldName)
  }

  private fun toViewState(
    projectState: ProjectConfig.ProjectSnapshot,
    detectedInstallations: List<PluginConfig.BlendInstallInfo>,
  ): BlenderToolWindowState {
    return BlenderToolWindowState(
      blenderPath = projectState.blenderPath,
      detectedBlenderInstalls = detectedInstallations,
      addonSymlinkName = projectState.addonSymlinkName,
      sourceFolder = projectState.sourceFolder,
      runArguments = projectState.runArguments,
      blenderLogLevel = projectState.blenderLogLevel,
      reloadOnSave = projectState.reloadOnSave,
      justMyCode = projectState.justMyCode,
      extensionsRepository = projectState.extensionsRepository,
      environmentVariables = projectState.environmentVariables,
      scriptDirectories = projectState.scriptDirectories.orEmpty(),
    )
  }
}
