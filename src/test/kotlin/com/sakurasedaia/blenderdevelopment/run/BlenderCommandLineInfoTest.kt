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

import com.intellij.openapi.util.ModificationTracker
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking

class BlenderCommandLineInfoTest : TestCase() {
  fun testExtensionCompletionReadsSelectedCommandWithoutRecursion() = runBlocking {
    val commandLineInfo =
        BlenderCommandLineInfo(
            selectedCommand = { "extension" },
            modificationTracker = ModificationTracker.NEVER_CHANGED,
        )

    val completions = commandLineInfo.tablesInfo.flatMap { it.collectCompletionInfo() }

    assertTrue(completions.any { it.text == "build" })
    assertTrue(completions.any { it.text == "--source-dir" })
  }

  fun testNonExtensionCommandDoesNotOfferExtensionArguments() = runBlocking {
    val commandLineInfo =
        BlenderCommandLineInfo(
            selectedCommand = { "sysinfo" },
            modificationTracker = ModificationTracker.NEVER_CHANGED,
        )

    val completions = commandLineInfo.tablesInfo.flatMap { it.collectCompletionInfo() }

    assertTrue(completions.isEmpty())
  }
}
