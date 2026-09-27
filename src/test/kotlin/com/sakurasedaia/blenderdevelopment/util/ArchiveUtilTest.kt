package com.sakurasedaia.blenderdevelopment.util

import com.intellij.openapi.util.io.NioFiles
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import junit.framework.TestCase

/** Verifies DMG application selection without mounting a real disk image. */
class ArchiveUtilTest : TestCase() {
    private lateinit var testDirectory: Path

    override fun setUp() {
        super.setUp()
        testDirectory = Files.createTempDirectory("archive-util-test")
    }

    override fun tearDown() {
        try {
            NioFiles.deleteRecursively(testDirectory)
        } finally {
            super.tearDown()
        }
    }

    fun testCopyDmgApplicationIgnoresFinderPresentationEntries() {
        val mountPoint = Files.createDirectory(testDirectory.resolve("mounted"))
        val application = Files.createDirectories(mountPoint.resolve("Blender.app/Contents/MacOS"))
        Files.writeString(application.resolve("Blender"), "binary")
        Files.writeString(mountPoint.resolve(" "), "decorative entry")
        Files.writeString(mountPoint.resolve(".background"), "background")
        val destination = Files.createDirectory(testDirectory.resolve("destination"))

        ArchiveUtil.copyDmgApplication(mountPoint, destination)

        assertTrue(Files.isRegularFile(destination.resolve("Blender.app/Contents/MacOS/Blender")))
        assertFalse(Files.exists(destination.resolve(" ")))
        assertFalse(Files.exists(destination.resolve(".background")))
    }

    fun testCopyDmgApplicationRejectsImageWithoutBlenderApplication() {
        val mountPoint = Files.createDirectory(testDirectory.resolve("mounted"))
        val destination = Files.createDirectory(testDirectory.resolve("destination"))

        val failure = runCatching {
            ArchiveUtil.copyDmgApplication(mountPoint, destination)
        }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("Mounted DMG does not contain Blender.app", failure?.message)
    }

    fun testDiskutilAttachArgumentsUseSeparateSupportedTokens() {
        assertEquals(
            listOf(
                "image",
                "attach",
                "--readOnly",
                "--nobrowse",
                "--mountPoint",
                "/tmp/mount",
                "/downloads/blender.dmg",
            ),
            ArchiveUtil.diskutilAttachArguments(
                    archive = Path.of("/downloads/blender.dmg"),
                    mountPoint = Path.of("/tmp/mount"),
                )
                .toList(),
        )
    }

    fun testFindDiskIdentifierReadsDeviceNodeFromAttachOutput() {
        val output =
            """
            /dev/disk12	GUID_partition_scheme
            /dev/disk12s1	Apple_HFS	/tmp/mount
            """
                .trimIndent()

        assertEquals("/dev/disk12", ArchiveUtil.findDiskIdentifier(output))
    }

    fun testFindDiskIdentifierAllowsMountPointFallback() {
        assertNull(ArchiveUtil.findDiskIdentifier("Attached successfully"))
    }
}
