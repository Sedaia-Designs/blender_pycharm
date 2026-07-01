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

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.sakurasedaia.blenderdevelopment.lib.PluginConfig
import com.sakurasedaia.blenderdevelopment.lib.ProjectConfig
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger

/** Eagerly loads project workspace configuration when the IDE opens a project. */
internal class ProjectConfigStartupLoader : ProjectActivity {
    override suspend fun execute(project: Project) {
        PluginConfig.getInstance().loadPluginState()
        val config = ProjectConfig.getInstance(project)
        config.loadWorkspaceState()
        PluginLogger.getInstance(project).debug("Loaded plugin and project workspace configuration on startup.")
    }
}
