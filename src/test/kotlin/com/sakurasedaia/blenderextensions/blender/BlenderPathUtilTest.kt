package com.sakurasedaia.blenderextensions.blender

import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderPathUtilTest {

    @Test
    fun testGetBlenderExecutableName() {
        val exe = BlenderPathUtil.getBlenderExecutableName()
        when {
            SystemInfo.isWindows -> assertTrue(exe.endsWith(".exe"))
            else -> assertTrue(exe == "blender")
        }
    }

    @Test
    fun testGetBlenderRootConfigDir() {
        val root = BlenderPathUtil.getBlenderRootConfigDir()
        assertNotNull(root)
    }

    @Test
    fun testGetSystemBlenderConfigDir() {
        val config = BlenderPathUtil.getSystemBlenderConfigDir("5.0")
        assertNotNull(config)
        assertTrue(config.toString().contains("5.0"))
    }
}
