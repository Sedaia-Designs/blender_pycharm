package com.sakurasedaia.blenderextensions.project

import org.junit.Assert.assertEquals
import org.junit.Test

class BlenderProjectIdFormattingTest {

    @Test
    fun testFormatToIdWithSpacesForProjectName() {
        val input = "My Awesome Extension"
        val formatted = formatToId(input, allowCapitals = true, allowSpaces = true)
        assertEquals("My Awesome Extension", formatted)
    }

    @Test
    fun testFormatToIdReplacesSpacesForAddonId() {
        val input = "My Awesome Extension"
        val formatted = formatToId(input, allowCapitals = false, allowSpaces = false)
        assertEquals("my_awesome_extension", formatted)
    }

    @Test
    fun testFormatToIdRemovesInvalidChars() {
        val input = "My Awesome! Extension @2026"
        val projectName = formatToId(input, allowCapitals = true, allowSpaces = true)
        assertEquals("My Awesome Extension 2026", projectName)

        val addonId = formatToId(input, allowCapitals = false, allowSpaces = false)
        assertEquals("my_awesome_extension_2026", addonId)
    }

    @Test
    fun testFormatToIdTrimming() {
        val input = "  My Extension  "
        val projectNameDefault = formatToId(input, allowCapitals = true, allowSpaces = true)
        assertEquals("My Extension", projectNameDefault)

        val projectNameNoTrim = formatToId(input, allowCapitals = true, allowSpaces = true, trim = false)
        assertEquals("  My Extension  ", projectNameNoTrim)

        val addonId = formatToId(input, allowCapitals = false, allowSpaces = false)
        assertEquals("my_extension", addonId)
    }

    @Test
    fun testDirectoryNameFormatting() {
        val input = "My Awesome Extension"
        // This simulates what updateLocationFromProjectName does
        val directoryName = formatToId(input, allowCapitals = true, allowSpaces = false)
        assertEquals("My_Awesome_Extension", directoryName)
    }
}
