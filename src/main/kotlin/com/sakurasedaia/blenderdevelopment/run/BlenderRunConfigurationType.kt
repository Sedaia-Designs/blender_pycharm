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

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.util.NotNullLazyValue
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.sakurasedaia.blenderdevelopment.lib.IconBundle

const val BLENDER_RUN_CONFIGURATION_ID: String = "BlenderRunConfigurationType"

/** Base run configuration type registration for Blender run targets. */
class BlenderRunConfigurationType : ConfigurationTypeBase(
    BLENDER_RUN_CONFIGURATION_ID,
    MessageBundle.message("run.configuration.type.display.name"),
    MessageBundle.message("run.configuration.type.description"),
    NotNullLazyValue.createValue { IconBundle.BlenderColor },
) {
    init {
        addFactory(BlenderRunConfigurationFactory(this))
    }
}

