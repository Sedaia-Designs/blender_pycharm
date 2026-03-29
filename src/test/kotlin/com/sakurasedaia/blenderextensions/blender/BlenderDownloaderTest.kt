package com.sakurasedaia.blenderextensions.blender

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.name

class BlenderDownloaderTest : BasePlatformTestCase() {

    fun testGetVersionDirectory() {
        val downloader = BlenderDownloader.getInstance(project)
        val dir = downloader.getVersionDirectory("4.2")
        assertTrue(dir.toString().contains("blender_downloads"))
        assertTrue(dir.toString().contains("app"))
        assertTrue(dir.toString().contains("4.2"))
    }

    fun testGetAppDirectory() {
        val downloader = BlenderDownloader.getInstance(project)
        val dir = downloader.getAppDirectory()
        assertTrue(dir.toString().contains("blender_downloads"))
        assertTrue(dir.toString().contains("app"))
    }

    fun testGetBaseDownloadDirectory() {
        val downloader = BlenderDownloader.getInstance(project)
        val dir = downloader.getBaseDownloadDirectory()
        assertTrue(dir.toString().contains("blender_downloads"))
        assertFalse(dir.toString().contains("4.2"))
    }

    fun testFindBlenderExecutable() {
        val downloader = BlenderDownloader.getInstance(project)
        val tempDir = Files.createTempDirectory("blender_test")
        
        try {
            // Test with empty directory
            assertNull(downloader.invokePrivate("findBlenderExecutable", tempDir))
            
            // Create a mock executable
            val osName = System.getProperty("os.name").lowercase()
            val isWindows = osName.contains("win")
            
            val executablePath = if (isWindows) {
                tempDir.resolve("blender.exe").createFile()
            } else {
                val linuxPath = tempDir.resolve("blender").createFile()
                // Set executable permission for Linux to ensure discovery
                try {
                    Files.setPosixFilePermissions(
                        linuxPath,
                        setOf(
                            PosixFilePermission.OWNER_EXECUTE,
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_EXECUTE
                        )
                    )
                } catch (_: Exception) { /* ignore on non-posix FS */ }
                linuxPath
            }
            
            val found = downloader.invokePrivate("findBlenderExecutable", tempDir) as Path?
            assertNotNull(found)
            val normalized = found!!.name.replace(".exe", "").lowercase()
            assertEquals("blender", normalized)
            
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    fun testDownloadProgressInitialState() {
        val downloader = BlenderDownloader.getInstance(project)
        val progress = downloader.downloadProgress.value
        assertFalse(progress.isDownloading)
        assertEquals(0.0, progress.progress)
        assertEquals("", progress.statusText)
        assertEquals(BlenderDownloader.ProgressType.NONE, progress.type)
    }

    private fun Any.invokePrivate(methodName: String, vararg args: Any?): Any? {
        val method = this.javaClass.getDeclaredMethods().find { it.name == methodName }
            ?: throw NoSuchMethodException("Method $methodName not found")
        method.isAccessible = true
        return method.invoke(this, *args)
    }
}
