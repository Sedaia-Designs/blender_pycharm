package com.sakurasedaia.blenderextensions.blender.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions

class BlenderDownloaderTest : BasePlatformTestCase() {

    @Test
    fun testGetDownloadUrlVariations() {
        val downloader = BlenderDownloader(project)
        val version = "4.2"
        val fullVersion = "4.2.18"

        // Windows x64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-windows-x64.zip",
            downloader.getDownloadUrl(version, isWindows = true, isLinux = false, isMac = false, isArm = false)
        )

        // Windows arm64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-windows-arm64.zip",
            downloader.getDownloadUrl(version, isWindows = true, isLinux = false, isMac = false, isArm = true)
        )

        // Linux x64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-linux-x64.tar.xz",
            downloader.getDownloadUrl(version, isWindows = false, isLinux = true, isMac = false, isArm = false)
        )

        // Linux arm64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-linux-arm64.tar.xz",
            downloader.getDownloadUrl(version, isWindows = false, isLinux = true, isMac = false, isArm = true)
        )

        // Mac arm64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-macos-arm64.dmg",
            downloader.getDownloadUrl(version, isWindows = false, isLinux = false, isMac = true, isArm = true)
        )

        // Mac x64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-macos-x64.dmg",
            downloader.getDownloadUrl(version, isWindows = false, isLinux = false, isMac = true, isArm = false)
        )

        // Non-supported version (fallback to .0)
        assertEquals(
            "https://download.blender.org/release/Blender3.6/blender-3.6.0-windows-x64.zip",
            downloader.getDownloadUrl("3.6", isWindows = true, isLinux = false, isMac = false, isArm = false)
        )

        // Unknown OS
        try {
            downloader.getDownloadUrl(version, isWindows = false, isLinux = false, isMac = false, isArm = false)
            fail("Should have thrown IllegalArgumentException for unknown OS")
        } catch (e: IllegalArgumentException) {
            assertEquals("OS is not supported", e.message)
        }
    }
}
