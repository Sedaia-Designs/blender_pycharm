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
@State(name = "PluginConfig", storages = [Storage("blender_pycharm.config.xml")])
internal class PluginConfig : PersistentStateComponent<PluginConfig.PluginState> {
	data class PluginState(
		// Temporary example setting will be filled out later with proper settings
		var blenderInstallPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/Applications/", // Portable Blender application bundles
		
		var bpyApiInstallPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/CodeCompletion/", // Installs for Fake-Bpy-Module
		
		var downloadPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/Downloads/",
		var clearDownloadsAfterInstall: Boolean = true,
		
		var downloadCacheMaxSize: Int = 2,
		
		var logPath: String = "${PathManager.getLogPath()}/BlenderExtensions/", // TODO: Change the default to the same path as the Intellij `idea.log` file, but save alongside in a `blender-development.log` file.
	)

	private var state: PluginState = PluginState()
	
	/**
	 * Sets the path to the Blender application bundle.
	 *
	 * @param path path to the Blender application bundle.
	 */
	fun setBlenderInstallPath(path: String) {
		state.blenderInstallPath = path
	}
	fun getBlenderInstallPath(): String = state.blenderInstallPath
	
	/**
	 * Sets the path to the Fake-Bpy-Module installation.
	 *
	 * @param path path to the Fake-Bpy-Module installation.
	 */
	fun setCodeCompletionPath(path: String) {
		state.bpyApiInstallPath = path
	}
	fun getCodeCompletionPath(): String = state.bpyApiInstallPath
	
	/**
	 * Sets the path to the log file.
	 *
	 * @param path path to the log file.
	 */
	fun setLogPath(path: String) {
		state.logPath = path
	}
	fun getLogPath(): String = state.logPath
	
	/**
	 * Sets the path to the download folder.
	 *
	 * @param path path to the download folder.
	 */
	fun setDownloadPath(path: String) {
		state.downloadPath = path
	}
	fun getDownloadPath(): String = state.downloadPath
	
	/**
	 * Sets whether to clear the download folder after installation.
	 *
	 * @param clear true to clear the download folder after installation, false otherwise.
	 */
	fun setClearDownloadsAfterInstall(clear: Boolean) {
		state.clearDownloadsAfterInstall = clear
	}
	fun getClearDownloadsAfterInstall(): Boolean = state.clearDownloadsAfterInstall
	
	/**
	 * The maxumum size of the download cache (in Gigabytes)
	 *
	 * @param size The maxumum size of the download cache (in Gigabytes)
	 */
	fun setDownloadCacheSize(size: Int) {
		state.downloadCacheMaxSize = size
	}
	fun getDownloadCacheSize(): Int = state.downloadCacheMaxSize
	
	override fun getState(): PluginState = state

	override fun loadState(state: PluginState) {
		this.state = state
	}

	companion object {
		/**
		 * Returns the global plugin configuration service.
		 *
		 * @return application-level [PluginConfig] service.
		 */
		fun getInstance(): PluginConfig = ApplicationManager.getApplication().getService(PluginConfig::class.java)
	}
}

// State managed by com.sakurasedaia.blenderdevelopment.ui.settings.BlenderSettingsFactory