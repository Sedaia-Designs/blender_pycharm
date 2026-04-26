package com.sakurasedaia.blenderextensions.blender.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.Properties

class BlenderHelperCompatibilityTest {

    @Test
    fun testIsOSCompatibleLogic() {
        // Since we can't mock SystemInfo easily, we can at least test the version parsing and logic flow
        // by making sure parseVersion works as expected, which we already do in testParseVersion.
        
        // We could also test compareVersions
        assertTrue(BlenderHelper.compareVersions(listOf(4, 2), listOf(4, 1)) > 0)
        assertTrue(BlenderHelper.compareVersions(listOf(4, 2), listOf(4, 2, 1)) < 0)
        assertEquals(0, BlenderHelper.compareVersions(listOf(4, 2, 0), listOf(4, 2)))
    }

    @Test
    fun testParseVersion() {
        assertEquals(listOf(5, 0), BlenderHelper.parseVersion("5.0"))
        assertEquals(listOf(4, 2, 19), BlenderHelper.parseVersion("4.2.19"))
        assertEquals(listOf(3), BlenderHelper.parseVersion("3"))
        assertEquals(emptyList<Int>(), BlenderHelper.parseVersion("invalid"))
    }
}
