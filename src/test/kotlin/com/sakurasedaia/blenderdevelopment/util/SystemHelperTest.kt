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

package com.sakurasedaia.blenderdevelopment.util

import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemHelperTest {
  @Test
  fun mapsSupportedDownloadBundleTypes() {
    assertEquals("zip", SystemHelper.normalizeBundleFileType("windows"))
    assertEquals("dmg", SystemHelper.normalizeBundleFileType("macos"))
    assertEquals("tar.xz", SystemHelper.normalizeBundleFileType("linux"))
    assertTrue(SystemHelper.isBundleFileTypeSupported("windows", "msi"))
    assertTrue(SystemHelper.isBundleFileTypeSupported("windows", "msix"))
    assertFalse(SystemHelper.isBundleFileTypeSupported("windows", "exe"))
  }

  @Test
  fun unknownVersionIsAlwaysIncompatible() {
    assertFalse(SystemHelper.isOSCompatible("9.9"))
  }

  @Test
  fun compatibilityLookupMatchesCurrentHostWhenVersionExists() {
    val hostInfo = SystemHelper.getSysInfo
    if (hostInfo.osName == "unknown" || hostInfo.osArch == "unknown") {
      assertFalse(SystemHelper.isOSCompatible("4.5"))
      return
    }

    val compatibleVersion = BlenderVersions.LIST.firstOrNull { entry ->
      entry.compatWithOs[hostInfo.osName]?.contains(hostInfo.osArch) == true
    }

    if (compatibleVersion != null) {
      assertTrue(SystemHelper.isOSCompatible(compatibleVersion.blMajorMinor))
    } else {
      BlenderVersions.LIST.forEach { entry ->
        assertFalse(SystemHelper.isOSCompatible(entry.blMajorMinor))
      }
    }
  }
}
