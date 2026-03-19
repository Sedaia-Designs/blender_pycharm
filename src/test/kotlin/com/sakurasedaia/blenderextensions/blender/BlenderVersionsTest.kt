package com.sakurasedaia.blenderextensions.blender

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class BlenderVersionsTest : BasePlatformTestCase() {

    @Test
    fun testSupportedVersions() {
        // Since we are in a test environment, dynamic versions might fail to fetch and return empty
        // but static versions should still be there.
        assertTrue(BlenderVersions.SUPPORTED_VERSIONS.size >= 2)
        assertTrue(BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == "4.2" })
        assertTrue(BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == "4.3" })
    }

    @Test
    fun testFallbackPatches() {
        assertEquals("18", BlenderVersions.SUPPORTED_VERSIONS.find { it.majorMinor == "4.2" }?.fallbackPatch)
        assertEquals("2", BlenderVersions.SUPPORTED_VERSIONS.find { it.majorMinor == "4.3" }?.fallbackPatch)
    }

    @Test
    fun testGetSupportedVersions() {
        val versions = BlenderVersions.getSupportedVersionsWithCustom()
        assertTrue(versions.contains("4.2"))
        assertTrue(versions.contains("4.3"))
        assertTrue(versions.size >= 2)
    }

    @Test
    fun testGetAllSelectableVersions() {
        val versions = BlenderVersions.getAllSelectableVersions()
        assertTrue(versions.contains("4.2"))
        assertTrue(versions.contains("4.3"))
    }
}
