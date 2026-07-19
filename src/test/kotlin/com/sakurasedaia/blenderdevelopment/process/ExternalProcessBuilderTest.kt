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

package com.sakurasedaia.blenderdevelopment.process

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExternalProcessBuilderTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testLaunchAndCaptureOutputReturnsFailureForMissingExecutable() {
    val result = ExternalProcessBuilder(project).launchAndCaptureOutput(
      command = "/path/that/does/not/exist/blender-runtime",
    )

    assertNull(result.exitCode)
    assertFalse(result.cancelled)
    assertNotNull(result.failure)
    assertTrue(result.output.isEmpty())
    assertTrue(result.firstLine.isEmpty())
  }

  fun testFirstLineReturnsFirstLineFromOutput() {
    val result = ExternalProcessBuilder.ProcessExecutionResult(
      command = "blender",
      args = listOf("--version"),
      output = "Blender 4.5.8\nbuild hash: abc123",
      exitCode = 0,
      cancelled = false,
    )

    assertEquals("Blender 4.5.8", result.firstLine)
  }
}
