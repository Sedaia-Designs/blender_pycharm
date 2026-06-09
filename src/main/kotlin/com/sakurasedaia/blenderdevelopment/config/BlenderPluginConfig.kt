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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager

/** Application-level persisted configuration for global Blender plugin settings. */
@Service(Service.Level.APP)
@State(name = "BlenderPluginConfig", storages = [Storage("blender_pycharm.config.xml")])
internal class BlenderPluginConfig : PersistentStateComponent<BlenderPluginConfig.PluginState> {
	data class PluginState(
		// Temporary example setting will be filled out later with proper settings
		var blenderInstallPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/Applications/", // Portable Blender application bundles
		
		var bpyApiInstallPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/CodeCompletion/", // Installs for Fake-Bpy-Module
		
		var logPath: String = "${PathManager.getLogPath()}/BlenderExtensions/", // TODO: Change the default to the same path as the Intellij `idea.log` file, but save alongside in a `blender-development.log` file.
	)

	private var state: PluginState = PluginState()
	
	/**
	 * Sets the path to the Blender application bundle.
	 *
	 * @param path path to the Blender application bundle.
	 * @return `Unit`.
	 */
	fun setBlenderInstallPath(path: String) {
		state.blenderInstallPath = path
	}
	fun getBlenderInstallPath(): String = state.blenderInstallPath
	
	/**
	 * Sets the path to the Fake-Bpy-Module installation.
	 *
	 * @param path path to the Fake-Bpy-Module installation.
	 * @return `Unit`.
	 */
	fun setCodeCompletionPath(path: String) {
		state.bpyApiInstallPath = path
	}
	fun getCodeCompletionPath(): String = state.bpyApiInstallPath
	
	/**
	 * Sets the path to the log file.
	 *
	 * @param path path to the log file.
	 * @return `Unit`.
	 */
	fun setLogPath(path: String) {
		state.logPath = path
	}
	fun getLogPath(): String = state.logPath
	
	override fun getState(): PluginState = state

	override fun loadState(state: PluginState) {
		this.state = state
	}

	companion object {
		/**
		 * Returns the global plugin configuration service.
		 *
		 * @return application-level [BlenderPluginConfig] service.
		 */
		fun getInstance(): BlenderPluginConfig = ApplicationManager.getApplication().getService(BlenderPluginConfig::class.java)
	}
}

// State managed by com.sakurasedaia.blenderdevelopment.ui.settings.BlenderSettingsFactory