package com.sakurasedaia.blenderextensions.blender.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import com.sakurasedaia.blenderextensions.common.utils.LangManager

class BlenderFinderTest : BasePlatformTestCase() {

    @Test
    fun testParseVersionOutput() {
        val unknown = LangManager.message("blender.version.unknown")
        
        // Standard output
        assertEquals("4.2", BlenderFinder.parseVersionOutput("Blender 4.2.19\nbuild date: ..."))
        
        // Output with different casing
        assertEquals("4.3", BlenderFinder.parseVersionOutput("blender 4.3.2"))
        
        // Output with extra spaces
        assertEquals("3.6", BlenderFinder.parseVersionOutput("  Blender   3.6.0  "))
        
        // Output with prefix in middle
        assertEquals("4.2", BlenderFinder.parseVersionOutput("Some other text\nBlender 4.2.19"))
        
        // Invalid output
        assertEquals(unknown, BlenderFinder.parseVersionOutput("No blender version here"))
        
        // Partial version
        assertEquals(unknown, BlenderFinder.parseVersionOutput("Blender 4"))
    }
}
