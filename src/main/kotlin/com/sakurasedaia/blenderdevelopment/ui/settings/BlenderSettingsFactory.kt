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

package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.services.ScrapeBlenderVersionLists
import com.sakurasedaia.blenderdevelopment.lib.services.SettingsInstallationScanService
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import kotlinx.coroutines.runBlocking
import javax.swing.JComponent

/** Global plugin settings configurable for Blender plugin state. */
class BlenderSettingsFactory : SearchableConfigurable, Configurable.NoScroll {
    private val logger = Logger.getInstance(BlenderSettingsFactory::class.java)
    private var content: BlenderSettingsContent? = null

    override fun getId(): String = "com.sakurasedaia.blenderdevelopment.settings.plugin"

    override fun getDisplayName(): String = MessageBundle.message("ui.settings.title")

    override fun createComponent(): JComponent {
        val ui = content ?: BlenderSettingsContent(
            onScanInstallations = { onComplete ->
                SettingsInstallationScanService.getInstance().scanInstallations(onComplete = onComplete)
            },
            onRefreshVersions = ::refreshVersions,
        ).also { content = it }
        ui.reset(PluginConfig.getInstance())
        return ui.component()
    }

    override fun isModified(): Boolean {
        val ui = content ?: return false
        return ui.isModified(PluginConfig.getInstance())
    }

    override fun apply() {
        val ui = content ?: return
        ui.apply(PluginConfig.getInstance())
    }

    override fun reset() {
        val ui = content ?: return
        ui.reset(PluginConfig.getInstance())
    }

    override fun disposeUIResources() {
        content = null
    }

    private fun refreshVersions(onComplete: (Result<List<BlenderVersion>>) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching {
                runBlocking { ScrapeBlenderVersionLists.getInstance().refreshVersionCache() }
            }
            result.onSuccess { PluginConfig.getInstance().markBlenderUpdateChecked() }
                .onFailure { logger.warn("Failed to refresh Blender versions from settings.", it) }
            ApplicationManager.getApplication().invokeLater { onComplete(result) }
        }
    }
}
