package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger

class ArchiveUtilTest : BasePlatformTestCase() {

    @Test
    fun testMoveStrippedContentWithSingleDir() {
        val tempDir = Files.createTempDirectory("test-extract-")
        val targetDir = Files.createTempDirectory("test-target-")
        try {
            // Setup: tempDir/blender-4.2/file.txt
            val singleDir = tempDir.resolve("blender-4.2")
            Files.createDirectory(singleDir)
            val file = singleDir.resolve("file.txt")
            file.writeText("test content")

            // Test
            val targetPath = targetDir.resolve("4.2")
            
            val result = ArchiveUtil.moveStrippedContent(tempDir, targetPath, null)

            assertEquals(0, result)
            assertTrue(targetPath.exists())
            assertTrue(targetPath.isDirectory())
            assertTrue(targetPath.resolve("file.txt").exists())
            assertEquals("test content", targetPath.resolve("file.txt").readText())
            assertFalse(singleDir.exists())
        } finally {
            tempDir.toFile().deleteRecursively()
            targetDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testMoveStrippedContentWithMultipleItems() {
        val tempDir = Files.createTempDirectory("test-extract-multi-")
        val targetDir = Files.createTempDirectory("test-target-multi-")
        try {
            // Setup: tempDir/file1.txt, tempDir/dir2/file2.txt
            Files.createFile(tempDir.resolve("file1.txt")).writeText("content 1")
            val dir2 = tempDir.resolve("dir2")
            Files.createDirectory(dir2)
            Files.createFile(dir2.resolve("file2.txt")).writeText("content 2")

            // Test
            val targetPath = targetDir.resolve("4.2")
            
            val result = ArchiveUtil.moveStrippedContent(tempDir, targetPath, null)

            assertEquals(0, result)
            assertTrue(targetPath.exists())
            assertTrue(targetPath.resolve("file1.txt").exists())
            assertTrue(targetPath.resolve("dir2").exists())
            assertTrue(targetPath.resolve("dir2/file2.txt").exists())
        } finally {
            tempDir.toFile().deleteRecursively()
            targetDir.toFile().deleteRecursively()
        }
    }
}
