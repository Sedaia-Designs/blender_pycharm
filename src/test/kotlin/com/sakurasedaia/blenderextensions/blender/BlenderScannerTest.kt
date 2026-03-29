package com.sakurasedaia.blenderextensions.blender

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.system.BlenderFinder

class BlenderScannerTest : BasePlatformTestCase() {

    fun testScanSystemInstallationsDoesNotCrash() {
        val installations = BlenderScanner.scanSystemInstallations()
        // We can't guarantee anything is installed on the build machine, 
        // but we can check it doesn't crash and returns a list.
        assertNotNull(installations)
    }

    fun testTryGetVersionWithInvalidPath() {
        val version = BlenderFinder.tryGetVersion("/path/to/nonexistent/blender")
        assertNotNull(version)
        val unknown = LangManager.message("blender.version.unknown")
        assertEquals(unknown, version)
    }
}
