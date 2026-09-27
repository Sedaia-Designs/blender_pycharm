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

import junit.framework.TestCase

class BlenderLaunchArgumentValidatorTest : TestCase() {
    fun testSafeWorkspaceOptionsDoNotConflict() {
        val conflict =
            BlenderLaunchArgumentValidator.findConflict(
                workspaceArguments = listOf("--background", "--factory-startup"),
                hasManagedBlendFile = true,
            )

        assertNull(conflict)
    }

    fun testWorkspaceBlendFileIsAllowedWithoutManagedBlendFile() {
        val conflict =
            BlenderLaunchArgumentValidator.findConflict(
                workspaceArguments = listOf("workspace.blend"),
                hasManagedBlendFile = false,
            )

        assertNull(conflict)
    }

    fun testWorkspaceBlendFileConflictsWithManagedBlendFile() {
        val conflict =
            BlenderLaunchArgumentValidator.findConflict(
                workspaceArguments = listOf("workspace.BLEND"),
                hasManagedBlendFile = true,
            )

        assertEquals(BlenderLaunchArgumentConflict.BLEND_FILE, conflict)
    }

    fun testPythonExecutionOptionsConflictWithManagedBootstrap() {
        listOf("--python", "-P", "--python=script.py", "--python-expr", "--python-console").forEach { option ->
            assertEquals(
                BlenderLaunchArgumentConflict.PYTHON_EXECUTION,
                BlenderLaunchArgumentValidator.findConflict(listOf(option), hasManagedBlendFile = false),
            )
        }
    }

    fun testCommandModesConflictWithManagedLaunch() {
        listOf("--command", "-c").forEach { option ->
            assertEquals(
                BlenderLaunchArgumentConflict.COMMAND_MODE,
                BlenderLaunchArgumentValidator.findConflict(listOf(option), hasManagedBlendFile = false),
            )
        }
    }

    fun testOptionTerminatorConflictsWithManagedArguments() {
        val conflict = BlenderLaunchArgumentValidator.findConflict(listOf("--"), hasManagedBlendFile = false)

        assertEquals(BlenderLaunchArgumentConflict.OPTION_TERMINATOR, conflict)
    }
}
