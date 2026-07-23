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

package com.sakurasedaia.blenderdevelopment.state

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.util.PythonModuleNameValidator

/** Project-level persisted configuration for Blender project settings. */
@Service(Service.Level.PROJECT)
@State(
  name = "BlenderExtensionManifest",
  storages = [
    Storage("\$PROJECT_CONFIG_DIR\$/blender-workspace.xml"),
    Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true),
  ]
)
class ProjectConfig(private val project: Project): PersistentStateComponent<ProjectConfig.ProjectState> {
  /** Available log levels according to Blender Documentation */
  enum class BlenderLogLevel {
    FATAL,
    ERROR,
    WARNING,
    INFO,
    DEBUG,
    TRACE
  }
  
  /** Persisted project-scoped settings stored in project-level configuration. */
  data class ProjectState(
    var blenderPath: String = "",

    var installedStubRequirement: String = "",
    
    var addonSymlinkName: String = "",
    
    var sourceFolder: String = "src/",
    
    var runArguments: String = "",
    
    var blenderLogLevel: String = BlenderLogLevel.DEBUG.name,
    
    var reloadOnSave: Boolean = true,
    
    var justMyCode: Boolean = true,
    
    var extensionsRepository: String = "pycharm_blender",
    
    var environmentVariables: Map<String, String> = emptyMap(),
    
    var scriptDirectories: List<String>? = null
  )
  
  private var state: ProjectState = ProjectState()
  
  /**
   * Stores the Blender executable path.
   *
   * @param path Blender executable path.
   * @return `Unit`.
   */
  fun setBlenderPath(path: String) {
    state.blenderPath = path
  }
  /** Returns the configured Blender executable path for this project. */
  fun getBlenderPath(): String = state.blenderPath
  
  
  /**
   * Stores the exact linting-stub requirement installed for this project.
   *
   * @param requirement installed version-specific package requirement, or an empty value.
   */
  fun setInstalledStubRequirement(requirement: String) {
    state.installedStubRequirement = requirement
  }

  /** Returns the exact linting-stub requirement last installed by the plugin. */
  fun getInstalledStubRequirement(): String = state.installedStubRequirement
  /**
   * Stores the add-on symlink name.
   *
   * @param name add-on symlink name.
   */
  fun setAddonSymlinkName(name: String) {
    val normalized = normalizeAddonSymlinkName(name)
    if (!PythonModuleNameValidator.isValid(normalized)) {
      return
    }
    state.addonSymlinkName = normalized
  }
  /** Returns the configured add-on symlink name. */
  fun getAddonSymlinkName(): String = state.addonSymlinkName

  private fun normalizeAddonSymlinkName(name: String): String {
    return name.trim().replace(SYMLINK_NAME_SEPARATOR_REGEX, "_")
  }
  
  /**
   * Stores the source folder path.
   *
   * @param path source folder path.
   */
  fun setSourceFolder(path: String) {
    state.sourceFolder = path
  }
  /** Returns the configured source folder path used by project workflows. */
  fun getSourceFolder(): String = state.sourceFolder
  
  /**
   * Stores the run arguments used by the Blender run configuration.
   *
   * @param arguments run arguments string.
   */
  fun setRunArguments(arguments: String) {
    state.runArguments = arguments
  }
  /** Returns the stored Blender run arguments string. */
  fun getRunArguments(): String = state.runArguments
  
  /**
   * Stores the configured Blender log level used by launch workflows.
   *
   * @param logLevel selected log level.
   */
  fun setBlenderLogLevel(logLevel: BlenderLogLevel) {
    state.blenderLogLevel = logLevel.name
  }
  /** Returns the configured Blender log level. */
  fun getBlenderLogLevel(): BlenderLogLevel {
    return BlenderLogLevel.entries.firstOrNull { it.name == state.blenderLogLevel } ?: BlenderLogLevel.INFO
  }
  
  /**
   * Stores whether add-ons should reload automatically on save.
   *
   * @param reload true to reload on save, false otherwise.
   */
  fun setReloadOnSave(reload: Boolean) {
    state.reloadOnSave = reload
  }
  /** Returns whether add-ons should reload automatically on save. */
  fun getReloadOnSave(): Boolean = state.reloadOnSave
  
  /**
   * Stores whether debugger behavior should prioritize project code only.
   *
   * @param justMyCode true to focus on project code, false otherwise.
   */
  fun setJustMyCode(justMyCode: Boolean) {
    state.justMyCode = justMyCode
  }
  /** Returns whether debugger behavior is configured as just-my-code. */
  fun getJustMyCode(): Boolean = state.justMyCode
  
  /**
   * Stores the configured Blender extensions repository path or URL.
   *
   * @param repository extensions repository path or URL.
   */
  fun setExtensionsRepository(repository: String) {
    val normalizedRepository = repository.trim()
    if (!PythonModuleNameValidator.isValid(normalizedRepository)) {
      return
    }
    state.extensionsRepository = normalizedRepository
  }
  /** Returns the configured Blender extensions repository path or URL. */
  fun getExtensionsRepository(): String = state.extensionsRepository
  
  /**
   * Stores project-scoped environment variables for Blender runtime workflows.
   *
   * @param variables environment variables map.
   */
  fun setEnvironmentVariables(variables: Map<String, String>) {
    state.environmentVariables = variables
  }
  /** Returns project-scoped environment variables for Blender runtime workflows. */
  fun getEnvironmentVariables(): Map<String, String> = state.environmentVariables
  
  /**
   * Stores optional script directories used by runtime workflows.
   *
   * @param directories optional list of script directory paths.
   */
  fun setScriptDirectories(directories: List<String>?) {
    state.scriptDirectories = directories
  }
  /** Returns optional script directories used by runtime workflows. */
  fun getScriptDirectories(): List<String>? = state.scriptDirectories
  
  /**
   * Forces initialization of persisted workspace settings for this project.
   *
   * This should be called during project startup to ensure state from
   * the project-level workspace file is loaded before UI and run flows use it.
   *
   * @return currently loaded project state.
   */
  fun loadWorkspaceState(): ProjectState = state
  
  
  /**
   * Loads workspace state from persistent storage.
   *
   * @param p0 deserialized state payload.
   */
  override fun loadState(p0: ProjectState) {
    state = p0
  }
  /** Returns the current persisted workspace state payload. */
  override fun getState(): ProjectState = state
  
  
  companion object {
    private val SYMLINK_NAME_SEPARATOR_REGEX = Regex("[\\s-]+")

    /**
     * Returns this configuration service for the given project.
     *
     * @param project target project.
     * @return project-level [ProjectConfig] service.
     */
    fun getInstance(project: Project): ProjectConfig = project.service()
  }
}

// State managed by com.sakurasedaia.blenderdevelopment.ui.toolwindow.BlenderToolWindowFactory
