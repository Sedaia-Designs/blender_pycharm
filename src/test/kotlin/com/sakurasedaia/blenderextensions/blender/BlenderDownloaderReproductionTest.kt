package com.sakurasedaia.blenderextensions.blender

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import kotlin.io.path.createTempDirectory

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

        val logger = BlenderLogger.getInstance(project)
        com.sakurasedaia.blenderextensions.system.ExternalProcessUtil.executeGroup(commands, false, logger)

        try {
            assertTrue("File should have been moved", java.nio.file.Files.exists(targetFile))
            assertFalse("Original file should not exist", java.nio.file.Files.exists(testFile))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
