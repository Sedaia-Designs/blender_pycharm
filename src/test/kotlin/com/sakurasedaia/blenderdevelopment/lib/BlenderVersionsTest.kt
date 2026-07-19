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

package com.sakurasedaia.blenderdevelopment.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderVersionsTest {
  @Test
  fun normalizeVersionHandlesMajorMinorAndPatchSelectors() {
    assertEquals("4.5", BlenderVersions.normalizeVersion("4.5"))
    assertEquals("4.5", BlenderVersions.normalizeVersion("4.5.8"))
    assertEquals("4", BlenderVersions.normalizeVersion("4"))
  }

  @Test
  fun normalizeVersionFromListHandlesShortAndLongLists() {
    assertEquals("5.2", BlenderVersions.normalizeVersionFromList(listOf(5, 2, 9)))
    assertEquals("5", BlenderVersions.normalizeVersionFromList(listOf(5)))
    assertEquals("", BlenderVersions.normalizeVersionFromList(emptyList()))
  }

  @Test
  fun lookupsResolveKnownVersionsFromMajorMinorAndFullSelectors() {
    assertEquals("4.5.8", BlenderVersions.getBlenderVersion("4.5"))
    assertEquals("4.5.8", BlenderVersions.getBlenderVersion("4.5.12"))
    assertEquals("3.11.9", BlenderVersions.getPythonVersion("4.5"))
    assertEquals("3.11.9", BlenderVersions.getPythonVersion("4.5.1"))
    assertNotNull(BlenderVersions.getCompatibleArch("4.5"))
    assertNotNull(BlenderVersions.getCompatibleArch("4.5.3"))
  }

  @Test
  fun lookupsReturnNullForUnknownVersions() {
    assertNull(BlenderVersions.getBlenderVersion("9.9"))
    assertNull(BlenderVersions.getPythonVersion("9.9"))
    assertNull(BlenderVersions.getCompatibleArch("9.9"))
  }

  @Test
  fun tableExposesExpectedStaticEntries() {
    val table = BlenderVersions.LIST
    assertEquals(3, table.size)
    assertTrue(table.any { it.blVersion == "4.2.19" && it.pyVersion == "3.11.7" })
    assertTrue(table.any { it.blVersion == "4.5.8" && it.pyVersion == "3.11.9" })
    assertTrue(table.any { it.blVersion == "5.2.0" && it.pyVersion == "3.13.13" })
  }
}
