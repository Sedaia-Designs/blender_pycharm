package com.sakurasedaia.blenderdevelopment.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderVersionsTest {
    @Test
    fun testIsOSCompatibleDefault() {
        // Since we are running on Mac OS X (likely), this should be true for 4.2
        // because 4.2 supports mac x64 and arm64.
        assertTrue("Should be compatible with current OS", BlenderVersions.isOSCompatible())
    }

    @Test
    fun testIsOSCompatibleSpecific() {
        assertTrue("Should be compatible with 4.5", BlenderVersions.isOSCompatible("4.5"))
        assertTrue("Should be compatible with 5.1", BlenderVersions.isOSCompatible("5.1"))
    }
    
    @Test
    fun testIsOSCompatibleUnknownVersion() {
        assertFalse("Should not be compatible with unknown version", BlenderVersions.isOSCompatible("9.9"))
    }

    @Test
    fun testIsOSCompatibleFullVersion() {
        assertTrue("Should be compatible with full version string 4.2.19", BlenderVersions.isOSCompatible("4.2.19"))
    }
}
