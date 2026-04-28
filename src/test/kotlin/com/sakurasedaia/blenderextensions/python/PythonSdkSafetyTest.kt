package com.sakurasedaia.blenderextensions.python
import com.sakurasedaia.blenderextensions.common.BlenderProjectPaths

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile

class PythonSdkSafetyTest : BasePlatformTestCase() {

    @Test
    fun testIsSafeToDelete() {
        val service = PythonSdkService(project)
        val projectRoot = Path.of(project.basePath!!)

        // 1. Test project root (Unsafe)
        assertFalse("Should not be allowed to delete project root", service.isSafeToDelete(projectRoot))

        // 2. Test system paths (Unsafe)
        val systemPath = if (com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper.isWindows()) {
            Path.of("C:\\Windows")
        } else {
            Path.of("/usr/bin")
        }
        assertFalse("Should not be allowed to delete system path: $systemPath", service.isSafeToDelete(systemPath))

        // 3. Test non-venv directory (Unsafe)
        val randomDir = Files.createTempDirectory("random_dir")
        try {
            assertFalse("Should not be allowed to delete directory without pyvenv.cfg", service.isSafeToDelete(randomDir))
        } finally {
            randomDir.toFile().delete()
        }

        // 4. Test legitimate venv (Safe)
        val venvDir = projectRoot.resolve(".venv_test")
        Files.createDirectories(venvDir)
        try {
            venvDir.resolve("pyvenv.cfg").createFile()
            assertTrue("Should be allowed to delete venv with pyvenv.cfg inside project", service.isSafeToDelete(venvDir))
        } finally {
            venvDir.toFile().deleteRecursively()
        }

        // 5. Test sandbox directory (Safe)
        val sandboxDir = BlenderProjectPaths.getSandboxDir(project)
        Files.createDirectories(sandboxDir)
        try {
            assertTrue("Should be allowed to delete ${BlenderProjectPaths.SANDBOX_NAME} inside project", service.isSafeToDelete(sandboxDir))
        } finally {
            sandboxDir.toFile().deleteRecursively()
        }
        
        // 6. Test common user directories (Unsafe)
        val userHome = System.getProperty("user.home")?.let { Path.of(it) }
        if (userHome != null) {
            val documents = userHome.resolve("Documents")
            assertFalse("Should not be allowed to delete Documents", service.isSafeToDelete(documents))
        }
    }
}
