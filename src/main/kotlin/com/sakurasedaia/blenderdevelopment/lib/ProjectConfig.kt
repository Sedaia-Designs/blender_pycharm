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

package com.sakurasedaia.blenderdevelopment.lib

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

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
		var blenderVersion: List<Int> = listOf(5, 1),
		var addonSymlinkName: String = "",
		var sourceFolder: String = "src/",
		var runArguments: String = "",
		var blenderLogLevel: String = BlenderLogLevel.DEBUG.name,
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
	 * Stores the Blender Version desired
	 *
	 * @param majorMinor Blender Major Minor version desired.
	 */
	fun setBlenderVersion(majorMinor: String) {
		val input = majorMinor.split(".")
		state.blenderVersion = listOf(input[0].toInt(), input[1].toInt())
	}
	
	fun getBlenderVersion(): List<Int> = state.blenderVersion
	/**
	 * Stores the add-on symlink name.
	 *
	 * @param name add-on symlink name.
	 */
	fun setAddonSymlinkName(name: String) {
		state.addonSymlinkName = name
	}
	/** Returns the configured add-on symlink name. */
	fun getAddonSymlinkName(): String = state.addonSymlinkName
	
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
