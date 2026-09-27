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

package com.sakurasedaia.blenderdevelopment.core

/** Unsupported interactions between free-form workspace arguments and the plugin-managed launch mode. */
internal enum class BlenderLaunchArgumentConflict {
    BLEND_FILE,
    PYTHON_EXECUTION,
    COMMAND_MODE,
    OPTION_TERMINATOR,
}

/** Protects the plugin-managed Blender startup file and Python bootstrap arguments from free-form argument conflicts. */
internal object BlenderLaunchArgumentValidator {
    /** Returns the first workspace-argument conflict, or `null` when the arguments can be composed safely. */
    fun findConflict(workspaceArguments: List<String>, hasManagedBlendFile: Boolean): BlenderLaunchArgumentConflict? {
        workspaceArguments.forEach { argument ->
            when {
                argument == "--" -> return BlenderLaunchArgumentConflict.OPTION_TERMINATOR
                argument == "--command" || argument == "-c" -> return BlenderLaunchArgumentConflict.COMMAND_MODE
                argument in PYTHON_EXECUTION_OPTIONS || argument.startsWith("--python=") ->
                    return BlenderLaunchArgumentConflict.PYTHON_EXECUTION
                hasManagedBlendFile && argument.endsWith(".blend", ignoreCase = true) -> return BlenderLaunchArgumentConflict.BLEND_FILE
            }
        }
        return null
    }

    private val PYTHON_EXECUTION_OPTIONS = setOf("--python", "-P", "--python-expr", "--python-console")
}
