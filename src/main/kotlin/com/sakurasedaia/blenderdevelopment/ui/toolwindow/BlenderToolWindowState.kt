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

import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel

internal data class BlenderToolWindowState(
  val blenderPath: String,
  val detectedBlenderInstalls: List<PluginConfig.BlendInstallInfo>,
  val addonSymlinkName: String,
  val sourceFolder: String,
  val runArguments: String,
  val blenderLogLevel: BlenderLogLevel,
  val reloadOnSave: Boolean,
  val justMyCode: Boolean,
  val extensionsRepository: String,
  val environmentVariables: Map<String, String>,
  val scriptDirectories: List<String>,
)
