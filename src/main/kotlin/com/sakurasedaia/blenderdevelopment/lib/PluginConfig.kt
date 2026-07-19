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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager

/** Application-level persisted configuration for global Blender plugin settings. */
@Service(Service.Level.APP)
@State(name = "PluginConfig", storages = [Storage("blender_pycharm.config.xml")])
class PluginConfig : PersistentStateComponent<PluginConfig.PluginState> {
	/** Descriptor for an installed Blender instance discovered on disk. */
	data class BlendInstallInfo(val name: String = "", val version: String = "", val path: String = "")
	
	/** Persisted application-scoped settings for the plugin. */
	data class PluginState(
		// Temporary example setting will be filled out later with proper settings
		var blenderInstallPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/Applications/", // Portable Blender application bundles
		
		var bpyApiInstallPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/CodeCompletion/", // Installs for Fake-Bpy-Module
		
		var downloadPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/Downloads/",
		var clearDownloadsAfterInstall: Boolean = true,
		
		var downloadCacheMaxSize: Int = 2,
		
		var logPath: String = "${PathManager.getLogPath()}/BlenderExtensions/", // TODO: Change the default to the same path as the Intellij `idea.log` file, but save alongside in a `blender-development.log` file.
		
		var detectedBlender: List<BlendInstallInfo> = mutableListOf()
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
	/** Returns the configured folder used to store Blender application bundles. */
	fun getBlenderInstallPath(): String = state.blenderInstallPath
	
	/**
	 * Sets the path to the Fake-Bpy-Module installation.
	 *
	 * @param path path to the Fake-Bpy-Module installation.
	 */
	fun setCodeCompletionPath(path: String) {
		state.bpyApiInstallPath = path
	}
	/** Returns the configured installation folder for Fake-Bpy-Module stubs. */
	fun getCodeCompletionPath(): String = state.bpyApiInstallPath
	
	/**
	 * Sets the path to the log file.
	 *
	 * @param path path to the log file.
	 */
	fun setLogPath(path: String) {
		state.logPath = path
	}
	/** Returns the configured directory for plugin log files. */
	fun getLogPath(): String = state.logPath
	
	/**
	 * Sets the path to the download folder.
	 *
	 * @param path path to the download folder.
	 */
	fun setDownloadPath(path: String) {
		state.downloadPath = path
	}
	/** Returns the configured directory used for downloaded artifacts. */
	fun getDownloadPath(): String = state.downloadPath
	
	/**
	 * Sets whether to clear the download folder after installation.
	 *
	 * @param clear true to clear the download folder after installation, false otherwise.
	 */
	fun setClearDownloadsAfterInstall(clear: Boolean) {
		state.clearDownloadsAfterInstall = clear
	}
	/** Returns whether downloads are deleted automatically after installation. */
	fun getClearDownloadsAfterInstall(): Boolean = state.clearDownloadsAfterInstall
	
	/**
	 * Sets the maximum download cache size in gigabytes.
	 *
	 * @param size maximum cache size in gigabytes.
	 */
	fun setDownloadCacheSize(size: Int) {
		state.downloadCacheMaxSize = size
	}
	/** Returns the configured maximum download cache size in gigabytes. */
	fun getDownloadCacheSize(): Int = state.downloadCacheMaxSize

	/**
	 * Stores the most recent discovered Blender installations.
	 *
	 * @param installs discovered Blender installations.
	 */
	fun setDetectedBlenderInstalls(installs: List<BlendInstallInfo>) {
		state.detectedBlender = installs
	}
	/** Returns the cached list of discovered Blender installations. */
	fun getDetectedBlenderInstalls(): List<BlendInstallInfo> = state.detectedBlender

	/**
	 * Forces initialization of persisted plugin settings.
	 *
	 * This is used at startup so application-level state is loaded before
	 * project UI and workflows read plugin configuration.
	 *
	 * @return currently loaded plugin state.
	 */
	fun loadPluginState(): PluginState = state
	
	/** Returns the current persisted state payload. */
	override fun getState(): PluginState = state

	/**
	 * Replaces the current persisted state with deserialized storage data.
	 *
	 * @param state deserialized plugin state from persistent storage.
	 */
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
