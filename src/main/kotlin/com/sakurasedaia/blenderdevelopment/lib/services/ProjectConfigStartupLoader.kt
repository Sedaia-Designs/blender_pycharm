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

package com.sakurasedaia.blenderdevelopment.lib.services

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.util.BlenderBootstrapScriptCleanup
import com.sakurasedaia.blenderdevelopment.util.BlenderRuntimeResources

/** Eagerly loads project workspace configuration when the IDE opens a project. */
internal class ProjectConfigStartupLoader : ProjectActivity {
    // TODO: Add a new step which performs a load from cache.
    override suspend fun execute(project: Project) {
        val pluginConfig = PluginConfig.getInstance()
        pluginConfig.loadPluginState()
        pluginConfig.startBlenderUpdateTimer()
        val config = ProjectConfig.getInstance(project)
        config.loadWorkspaceState()
        val logger = PluginLogger.getInstance(project)
        runCatching {
            BlenderRuntimeResources.ensureRuntimeExtracted()
        }
            .onFailure { error ->
                logger.error(ErrorTypes.BLENDER_LAUNCH_ERROR, error)
            }
        runCatching {
            BlenderBootstrapScriptCleanup.cleanupStaleScripts(
                directory = PathManager.getScratchDir(),
                debugLog = logger::debug,
                warnLog = logger::warn,
            )
        }
            .onFailure { error ->
                logger.warn(ErrorTypes.BOOTSTRAP_CLEANUP_FAILED.toString(), error)
            }
        logger.debug("Loaded plugin and project workspace configuration on startup.")
    }
}
