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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemHelperTest {
  @Test
  fun invocationReturnsCachedSystemInfo() {
    assertSame(SystemInfo.getSysInfo, SystemInfo())
  }

  @Test
  fun mapsSupportedDownloadBundleTypes() {
    assertEquals("zip", SystemInfo.normalizeBundleFileType("windows"))
    assertEquals("dmg", SystemInfo.normalizeBundleFileType("macos"))
    assertEquals("tar.xz", SystemInfo.normalizeBundleFileType("linux"))
    assertTrue(SystemInfo.isBundleFileTypeSupported("windows", "msi"))
    assertTrue(SystemInfo.isBundleFileTypeSupported("windows", "msix"))
    assertFalse(SystemInfo.isBundleFileTypeSupported("windows", "exe"))
  }

  @Test
  fun unknownVersionIsAlwaysIncompatible() {
    assertFalse(SystemInfo.isOSCompatible("9.9"))
  }

  @Test
  fun compatibilityLookupMatchesCurrentHostWhenVersionExists() {
    val hostInfo = SystemInfo.getSysInfo
    if (hostInfo.osName == "unknown" || hostInfo.osArch == "unknown") {
      assertFalse(SystemInfo.isOSCompatible("4.5"))
      return
    }

    val compatibleVersion = BlenderVersions.LIST.firstOrNull { entry ->
      entry.compatWithOs[hostInfo.osName]?.contains(hostInfo.osArch) == true
    }

    if (compatibleVersion != null) {
      assertTrue(SystemInfo.isOSCompatible(compatibleVersion.blMajorMinor))
    } else {
      BlenderVersions.LIST.forEach { entry ->
        assertFalse(SystemInfo.isOSCompatible(entry.blMajorMinor))
      }
    }
  }
}
