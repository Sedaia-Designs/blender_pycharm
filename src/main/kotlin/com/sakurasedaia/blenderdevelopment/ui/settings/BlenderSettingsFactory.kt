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
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import javax.swing.JComponent

/** Global plugin settings configurable for Blender plugin state. */
class BlenderSettingsFactory : SearchableConfigurable, Configurable.NoScroll {
  private var settingsComponent: BlenderSettingsComponent? = null

  override fun getId(): String = "com.sakurasedaia.blenderdevelopment.settings.plugin"

  override fun getDisplayName(): String = MessageBundle.message("ui.settings.title")

  override fun createComponent(): JComponent {
    val component = settingsComponent ?: BlenderSettingsComponent().also {
      settingsComponent = it
    }
    component.reset()
    return component.component()
  }

  override fun isModified(): Boolean = settingsComponent?.isModified() == true

  override fun apply() {
    settingsComponent?.apply()
  }

  override fun reset() {
    settingsComponent?.reset()
  }

  override fun disposeUIResources() {
    settingsComponent?.dispose()
    settingsComponent = null
  }
}
