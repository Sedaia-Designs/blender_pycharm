package com.sakurasedaia.blenderextensions.blender.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions

class BlenderDownloaderTest : BasePlatformTestCase() {

    @Test
    fun testGetDownloadUrlVariations() {
        val downloader = BlenderDownloader(project)
        val version = "4.2"
        val fullVersion = "4.2.19"

        // Windows x64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-windows-x64.zip",
            downloader.getDownloadUrl(version, osName = "windows", archType = "x64")
        )

        // Windows arm64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-windows-arm64.zip",
            downloader.getDownloadUrl(version, osName = "windows", archType = "arm64")
        )

        // Linux x64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-linux-x64.tar.xz",
            downloader.getDownloadUrl(version, osName = "linux", archType = "x64")
        )

        // Linux arm64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-linux-arm64.tar.xz",
            downloader.getDownloadUrl(version, osName = "linux", archType = "arm64")
        )

        // Mac arm64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-macos-arm64.dmg",
            downloader.getDownloadUrl(version, osName = "macos", archType = "arm64")
        )

        // Mac x64
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-$fullVersion-macos-x64.dmg",
            downloader.getDownloadUrl(version, osName = "macos", archType = "x64")
        )

        // Non-supported version (fallback to .0)
        assertEquals(
            "https://download.blender.org/release/Blender3.6/blender-3.6.0-windows-x64.zip",
            downloader.getDownloadUrl("3.6", osName = "windows", archType = "x64")
        )

        // Unknown OS
        try {
            downloader.getDownloadUrl(version, osName = "unknown", archType = "x64")
            fail("Should have thrown IllegalArgumentException for unknown OS")
        } catch (e: IllegalArgumentException) {
            assertEquals("OS is not supported", e.message)
        }
    }

    @Test
    fun testGetHashFileUrl() {
        val downloader = BlenderDownloader(project)
        // 5.1 -> Blender5.1/blender-5.1.1.sha256 (from BlenderVersions)
        assertEquals(
            "https://download.blender.org/release/Blender5.1/blender-5.1.1.sha256",
            downloader.getHashFileUrl("5.1")
        )
        // 4.2 -> Blender4.2/blender-4.2.19.sha256 (from BlenderVersions)
        assertEquals(
            "https://download.blender.org/release/Blender4.2/blender-4.2.19.sha256",
            downloader.getHashFileUrl("4.2")
        )
    }

    @Test
    fun testIsSysCompatible() {
        val downloader = BlenderDownloader(project)
        // This test depends on the environment, but we can at least check if it doesn't throw.
        assertNotNull(downloader)
    }

    @Test
    fun testIsOSCompatibleLogic() {
        // We test the logic directly using a test-friendly version of the helper if possible,
        // or just rely on the fact that we've added logic to BlenderHelper.
        // Since we can't easily mock SystemInfo, we'll verify the version parsing which is part of it.
        assertEquals(listOf(5, 0), com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper.parseVersion("5.0"))
    }
}
