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

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.sakurasedaia.blenderdevelopment.lib.PluginConfig
import com.sakurasedaia.blenderdevelopment.lib.services.SettingsInstallationScanService
import javax.swing.JComponent

/** Global plugin settings configurable for Blender plugin state. */
class BlenderSettingsFactory : SearchableConfigurable, Configurable.NoScroll {
    private var content: BlenderSettingsContent? = null

    override fun getId(): String = "com.sakurasedaia.blenderdevelopment.settings.plugin"

    override fun getDisplayName(): String = MessageBundle.message("ui.settings.title")

    override fun createComponent(): JComponent {
        val ui = content ?: BlenderSettingsContent(
            onScanInstallations = { SettingsInstallationScanService.getInstance().scanInstallations() },
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
}
