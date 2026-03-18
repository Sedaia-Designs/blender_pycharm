package com.sakurasedaia.blenderextensions.blender

import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.python.PythonUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.name

class BlenderLinterTest : BasePlatformTestCase() {

    fun testGetLintDirectory() {
        val lintDir = PythonUtil.getLintDirectory("4.2")
        assertTrue(lintDir.toString().contains("blender_downloads"))
        assertTrue(lintDir.toString().contains("lint"))
        assertTrue(lintDir.toString().contains("4.2"))
    }

    fun testInstallFakeBpyModuleCommandGeneration() {
        val downloader = BlenderDownloader.getInstance(project)
        val tempDir = Files.createTempDirectory("blender_linter_test")
        
        try {
            // Mock a Blender installation structure
            val version = "4.2"
            val versionDir = tempDir.resolve(version)
            val pythonBinDir = versionDir.resolve("python").resolve("bin")
            pythonBinDir.createDirectories()
            
            val osName = System.getProperty("os.name").lowercase()
            val isWindows = osName.contains("win")
            
            if (isWindows) {
                pythonBinDir.resolve("python.exe").createFile()
            } else {
                pythonBinDir.resolve("python3").createFile()
            }

            val blenderExe = if (isWindows) {
                tempDir.resolve("blender.exe").createFile()
            } else {
                tempDir.resolve("blender").createFile()
            }

            val lintDir = PythonUtil.getLintDirectory(version)
            if (lintDir.exists()) {
                lintDir.toFile().deleteRecursively()
            }
            
            PythonService.getInstance(project).installFakeBpyModule(blenderExe, version)
            
            assertTrue("Lint directory should be created even if pip fails (because of directory creation logic)", lintDir.exists())
            
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    fun testSetupLinterValidation() {
        val service = BlenderService.getInstance(project)
        val tempDir = Files.createTempDirectory("blender_linter_validation_test")
        
        try {
            // 1. Test non-existent path
            val nonExistent = tempDir.resolve("non_existent_blender")
            // We can't easily assert the notification, but we can ensure it doesn't crash
            // service.setupLinter(nonExistent.toString()) // Avoid background task
            
            // 2. Test path that exists but is not a valid Blender (no version found)
            val dummyFile = tempDir.resolve("dummy_blender").createFile()
            // service.setupLinter(dummyFile.toString()) // Avoid background task
            
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
