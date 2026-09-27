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

import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import org.junit.Assert.assertEquals
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
        val knownVersion = BlenderVersions.LIST.first { it.pyVersion.isNotBlank() }
        val fullSelector = "${knownVersion.blMajorMinor}.999"

        assertEquals(knownVersion.blVersion, BlenderVersions.getBlenderVersion(knownVersion.blMajorMinor))
        assertEquals(knownVersion.blVersion, BlenderVersions.getBlenderVersion(fullSelector))
        assertEquals(knownVersion.pyVersion, BlenderVersions.getPythonVersion(knownVersion.blMajorMinor))
        assertEquals(knownVersion.pyVersion, BlenderVersions.getPythonVersion(fullSelector))
        assertEquals(knownVersion.fakeBpyPackage, BlenderVersions.getFakeBpyPackageName(fullSelector))
        assertEquals(knownVersion.compatWithOs, BlenderVersions.getCompatibleArch(knownVersion.blMajorMinor))
        assertEquals(knownVersion.compatWithOs, BlenderVersions.getCompatibleArch(fullSelector))
    }

    @Test
    fun lookupsReturnNullForUnknownVersions() {
        assertNull(BlenderVersions.getBlenderVersion("9.9"))
        assertNull(BlenderVersions.getPythonVersion("9.9"))
        assertNull(BlenderVersions.getFakeBpyPackageName("9.9"))
        assertNull(BlenderVersions.getCompatibleArch("9.9"))
    }

    @Test
    fun tableExposesCoherentVersionMetadata() {
        val table = BlenderVersions.LIST
        assertTrue(table.isNotEmpty())
        assertEquals(table.size, table.map(BlenderVersion::blMajorMinor).distinct().size)
        table.forEach { version ->
            assertEquals(3, version.blVersionList.size)
            assertTrue(version.pyVersionList.isEmpty() || version.pyVersionList.size == 3)
            assertTrue(version.compatWithOs.keys.all(SUPPORTED_PLATFORMS::contains))
            assertTrue(version.compatWithOs.values.all(List<String>::isNotEmpty))
        }
    }

    @Test
    fun downloadUrlMatchesCurrentHostArtifactName() {
        val systemInfo = SystemInfo.getSysInfo
        val compatibleVersion =
            BlenderVersion(
                installName = "Blender 9.8.7",
                blVersionList = listOf(9, 8, 7),
                compatWithOs = mapOf(systemInfo.osName to listOf(systemInfo.osArch)),
            )

        assertEquals(
            "https://download.blender.org/release/Blender9.8/" +
                "blender-9.8.7-${systemInfo.osName}-${systemInfo.osArch}.${systemInfo.bundleFileType}",
            compatibleVersion.getDownloadURL(),
        )
    }

    @Test
    fun archiveNameRejectsUnsupportedArtifacts() {
        val blenderVersion = BlenderVersions.LIST.first()

        assertEquals("", blenderVersion.getArchiveName("unsupported"))
    }

    companion object {
        private val SUPPORTED_PLATFORMS = setOf("windows", "macos", "linux")
    }
}
