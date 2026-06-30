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

package com.sakurasedaia.blenderdevelopment.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/** Workspace-level persisted configuration for Blender project settings. */
@Service(Service.Level.PROJECT)
@State(name = "BlenderExtensionManifest", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class ProjectConfig(private val project: Project): PersistentStateComponent<ProjectConfig.ProjectState> {
	/** Persistent state stored in the workspace file. */
	data class ProjectState(
		var blenderPath: String = "",
		var addonSymlinkName: String = "",
		var sandbox: Boolean = true,
		var sourceFolder: String = "src/",
        var runArguments: String = "",
		var runArguments: String = "",
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
	fun getBlenderPath(): String = state.blenderPath
	
	
	/**
	 * Stores the add-on symlink name.
	 *
	 * @param name add-on symlink name.
	 * @return `Unit`.
	 */
	fun setAddonSymlinkName(name: String) {
		state.addonSymlinkName = name
	}
	fun getAddonSymlinkName(): String = state.addonSymlinkName
	
	/**
	 * Stores sandbox mode preference.
	 *
	 * @param sandbox sandbox enabled flag.
	 * @return `Unit`.
	 */
	fun setSandbox(sandbox: Boolean) {
		state.sandbox = sandbox
	}
	fun getSandbox(): Boolean = state.sandbox
	
	
	/**
	 * Stores the source folder path.
	 *
	 * @param path source folder path.
	 * @return `Unit`.
	 */
	fun setSourceFolder(path: String) {
		state.sourceFolder = path
	}
	fun getSourceFolder(): String = state.sourceFolder

    /**
     * Stores the run arguments used by the Blender run configuration.
     *
     * @param arguments run arguments string.
     * @return `Unit`.
     */
    fun setRunArguments(arguments: String) {
        state.runArguments = arguments
    }
    fun getRunArguments(): String = state.runArguments
	
	
	/**
	 * Loads workspace state from persistent storage.
	 *
	 * @param p0 deserialized state payload.
	 * @return `Unit`.
	 */
	override fun loadState(p0: ProjectState) {
		state = p0
	}
	override fun getState(): ProjectState = state
	
	
	companion object {
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
