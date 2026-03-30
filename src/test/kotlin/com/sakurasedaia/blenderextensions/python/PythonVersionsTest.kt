package com.sakurasedaia.blenderextensions.python

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PythonVersionsTest : BasePlatformTestCase() {

    fun testPythonVersionMapping() {
        assertEquals("3.11.7", PythonVersions.getPythonVersionForBlender("4.2"))
        assertEquals("3.11.9", PythonVersions.getPythonVersionForBlender("4.3"))
        assertEquals("3.11.11", PythonVersions.getPythonVersionForBlender("4.4"))
        assertEquals("3.11.11", PythonVersions.getPythonVersionForBlender("4.5"))
        assertEquals("3.11.13", PythonVersions.getPythonVersionForBlender("5.0"))
        assertEquals("3.13.9", PythonVersions.getPythonVersionForBlender("5.1"))
        assertNull(PythonVersions.getPythonVersionForBlender("3.6"))
    }
}
