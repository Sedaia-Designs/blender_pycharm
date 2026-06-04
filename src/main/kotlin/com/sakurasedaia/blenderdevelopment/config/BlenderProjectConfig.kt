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
@State(name = "BlenderProjectConfig", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class BlenderProjectConfig(private val project: Project): PersistentStateComponent<BlenderProjectConfig.ProjectState> {
	/** Persistent state stored in the workspace file. */
	data class ProjectState(
		var blenderPath: String = "",
		var addonSymlinkName: String = "",
		var sandbox: Boolean = true,
		var sourceFolder: String = "src/",
	)
	
	private var state: ProjectState = ProjectState()
	
	/**
	 * Returns the configured Blender executable path.
	 *
	 * @return Blender executable path.
	 */
	fun getBlenderPath(): String = state.blenderPath
	
	/**
	 * Stores the Blender executable path.
	 *
	 * @param path Blender executable path.
	 * @return `Unit`.
	 */
	fun setBlenderPath(path: String) {
		state.blenderPath = path
	}
	
	/**
	 * Returns the configured add-on symlink name.
	 *
	 * @return add-on symlink name.
	 */
	fun getAddonSymlinkName(): String = state.addonSymlinkName
	
	
	/**
	 * Stores the add-on symlink name.
	 *
	 * @param name add-on symlink name.
	 * @return `Unit`.
	 */
	fun setAddonSymlinkName(name: String) {
		state.addonSymlinkName = name
	}
	
	
	/**
	 * Returns whether sandbox mode is enabled.
	 *
	 * @return `true` when sandbox mode is enabled.
	 */
	fun getSandbox(): Boolean = state.sandbox
	
	
	
	/**
	 * Stores sandbox mode preference.
	 *
	 * @param sandbox sandbox enabled flag.
	 * @return `Unit`.
	 */
	fun setSandbox(sandbox: Boolean) {
		state.sandbox = sandbox
	}
	
	/**
	 * Returns the configured project source folder path.
	 *
	 * @return source folder path relative to project root.
	 */
	fun getSourceFolder(): String = state.sourceFolder
	
	
	/**
	 * Stores the source folder path.
	 *
	 * @param path source folder path.
	 * @return `Unit`.
	 */
	fun setSourceFolder(path: String) {
		state.sourceFolder = path
	}
	
	
	/**
	 * Returns persisted workspace state for serialization.
	 *
	 * @return current persistent state payload.
	 */
	override fun getState(): ProjectState {
		return state
	}
	
	
	/**
	 * Loads workspace state from persistent storage.
	 *
	 * @param p0 deserialized state payload.
	 * @return `Unit`.
	 */
	override fun loadState(p0: ProjectState) {
		state = p0
	}
	
	
	companion object {
		/**
		 * Returns this configuration service for the given project.
		 *
		 * @param project target project.
		 * @return project-level [BlenderProjectConfig] service.
		 */
		fun getInstance(project: Project): BlenderProjectConfig = project.service()
	}
}

// State managed by com.sakurasedaia.blenderdevelopment.ui.toolwindow.BlenderToolWindowFactory