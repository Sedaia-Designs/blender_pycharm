package com.sakurasedaia.blenderextensions.blender

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import junit.framework.Assert.assertNull

class BlenderDownloaderReproductionTest : BasePlatformTestCase() {

    fun testExtractionCommandsListExecution() {
        val downloader = BlenderDownloader.getInstance(project)
        val tempDir = createTempDirectory("extraction_test")
        val testFile = tempDir.resolve("test.txt")
        java.nio.file.Files.writeString(testFile, "test content")

        val targetFile = tempDir.resolve("test_moved.txt")
        
        // We use 'sh' for Linux/Mac and 'cmd' or 'powershell' for Windows in the actual code.
        // For a platform-independent test of the 'list execution' logic, we can try to use simple commands.
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val commands = if (isWindows) {
            listOf(
                com.intellij.execution.configurations.GeneralCommandLine("cmd", "/c", "move", testFile.toString(), targetFile.toString()),
                com.intellij.execution.configurations.GeneralCommandLine("cmd", "/c", "echo", "done")
            )
        } else {
            listOf(
                com.intellij.execution.configurations.GeneralCommandLine("mv", testFile.toString(), targetFile.toString()),
                com.intellij.execution.configurations.GeneralCommandLine("echo", "done")
            )
        }

        val method = BlenderDownloader::class.java.getDeclaredMethods().find { it.name == "executeExtractionCommands" }
        method?.let { m ->
            m.isAccessible = true
            m.invoke(downloader, commands, false)
        }

        try {
            assertTrue("File should have been moved", java.nio.file.Files.exists(targetFile))
            assertFalse("Original file should not exist", java.nio.file.Files.exists(testFile))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
