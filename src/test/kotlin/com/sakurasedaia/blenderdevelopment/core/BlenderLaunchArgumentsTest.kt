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

package com.sakurasedaia.blenderdevelopment.core

import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import java.nio.file.Path
import junit.framework.TestCase

class BlenderLaunchArgumentsTest : TestCase() {
  fun testRawLaunchRequestDefaultsToPlainBlenderArguments() {
    val request = BlenderLaunchRequest()

    assertTrue(request.blenderPath.isEmpty())
    assertTrue(request.arguments.isEmpty())
    assertTrue(request.environment.isEmpty())
    assertNull(request.workingDirectory)
  }

  fun testPythonArgumentsKeepTheScriptPathAsOneArgument() {
    val arguments =
        BlenderLaunchArguments.python(
            logLevel = BlenderLogLevel.DEBUG,
            workspaceArguments = listOf("--factory-startup"),
            scriptPath = Path.of("/project path/bootstrap script.py"),
            additionalArguments = listOf("--python-exit-code", "1"),
        )

    assertEquals(
        listOf(
            "--log-level",
            "debug",
            "--factory-startup",
            "--python",
            "/project path/bootstrap script.py",
            "--python-exit-code",
            "1",
        ),
        arguments,
    )
  }

  fun testCommandArgumentsFollowCommandBecauseBlenderConsumesTheRemainder() {
    val arguments =
        BlenderLaunchArguments.command(
            logLevel = BlenderLogLevel.INFO,
            commandArguments = listOf("extension", "build", "--source-dir", "/project path/extension"),
        )

    assertEquals(
        listOf(
            "--log-level",
            "info",
            "--command",
            "extension",
            "build",
            "--source-dir",
            "/project path/extension",
        ),
        arguments,
    )
  }

  fun testCommandArgumentsRejectAnEmptyCommand() {
    try {
      BlenderLaunchArguments.command(BlenderLogLevel.INFO, emptyList())
    } catch (_: IllegalArgumentException) {
      return
    }
    fail("Expected an empty Blender command to be rejected")
  }

  fun testCommandArgumentsRejectABlankCommand() {
    try {
      BlenderLaunchArguments.command(BlenderLogLevel.INFO, listOf(""))
    } catch (_: IllegalArgumentException) {
      return
    }
    fail("Expected a blank Blender command to be rejected")
  }

  fun testCommandArgumentsRejectTheLongCommandOption() {
    try {
      BlenderLaunchArguments.command(BlenderLogLevel.INFO, listOf("--command", "extension", "list"))
    } catch (_: IllegalArgumentException) {
      return
    }
    fail("Expected a duplicate --command option to be rejected")
  }

  fun testCommandArgumentsRejectTheShortCommandOption() {
    try {
      BlenderLaunchArguments.command(BlenderLogLevel.INFO, listOf("-c", "extension", "list"))
    } catch (_: IllegalArgumentException) {
      return
    }
    fail("Expected a duplicate -c option to be rejected")
  }
}
