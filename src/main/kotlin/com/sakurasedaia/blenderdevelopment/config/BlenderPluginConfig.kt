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

/** Application-level persisted configuration for global Blender plugin settings. */
@Service(Service.Level.APP)
@State(name = "BlenderPluginConfig", storages = [Storage("blender_pycharm.config.xml")])
internal class BlenderPluginConfig : PersistentStateComponent<BlenderPluginConfig.PluginState> {
	data class PluginState(
		// Temporary example setting, will be filled out later with proper settings
		var useCustomBlenderInstallPath: Boolean = true,
		var blenderInstallPath: String = "",
	)

	private var state: PluginState = PluginState()
	
	fun getBlenderInstallPath(): String = state.blenderInstallPath
	fun setBlenderInstallPath(path: String) {
		state.blenderInstallPath = path
	}
	
	fun getUseCustomBlenderInstallPath(): Boolean = state.useCustomBlenderInstallPath
	fun setUseCustomBlenderInstallPath(value: Boolean) {
		state.useCustomBlenderInstallPath = value
	}

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