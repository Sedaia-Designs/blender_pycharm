package com.sakurasedaia.blenderextensions.project

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderFileTemplateTest {
    @Test
    fun testAddonFileTemplateExists() {
        val resource = javaClass.getResource("/fileTemplates/internal/Blender Component.py.ft")
        assertNotNull("Add-on file template should exist in resources", resource)
        val content = resource!!.readText()
        assertTrue("Add-on template should contain bl_info", content.contains("bl_info = {"))
        assertTrue("Add-on template should contain register", content.contains("def register():"))
        assertTrue("Add-on template should contain unregister", content.contains("def unregister():"))
    }

    @Test
    fun testModuleFileTemplateExists() {
        val resource = javaClass.getResource("/fileTemplates/internal/Module.py.ft")
        assertNotNull("Module file template should exist in resources", resource)
        val content = resource!!.readText()
        assertTrue("Module template should contain register", content.contains("def register():"))
        assertTrue("Module template should contain unregister", content.contains("def unregister():"))
        assertTrue("Module template should contain imports", content.contains("import bpy.types as T"))
        assertTrue("Module template should contain main check", content.contains("if __name__ == \"__main__\":"))
    }
}
