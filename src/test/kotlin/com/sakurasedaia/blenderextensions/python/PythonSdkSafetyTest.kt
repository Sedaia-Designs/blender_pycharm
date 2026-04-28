package com.sakurasedaia.blenderextensions.python
import com.sakurasedaia.blenderextensions.common.utils.PathUtils

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile

class PythonSdkSafetyTest : BasePlatformTestCase() {

    @Test
    fun testIsSafeToDelete() {
        val projectRoot = Path.of(project.basePath!!)

        // 1. Test project root (Unsafe)
        assertFalse("Should not be allowed to delete project root", PathUtils.isSafeToDelete(projectRoot, project))

        // 2. Test system paths (Unsafe)
        val systemPath = if (com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper.isWindows()) {
            Path.of("C:\\Windows")
        } else {
            Path.of("/usr/bin")
        }
        assertFalse("Should not be allowed to delete system path: $systemPath", PathUtils.isSafeToDelete(systemPath, project))

        // 3. Test non-venv directory (Unsafe)
        val randomDir = Files.createTempDirectory("random_dir")
        try {
            assertFalse("Should not be allowed to delete directory without pyvenv.cfg", PathUtils.isSafeToDelete(randomDir, project))
        } finally {
            randomDir.toFile().delete()
        }

        // 4. Test legitimate venv (Safe)
        val venvDir = projectRoot.resolve(".venv_test")
        Files.createDirectories(venvDir)
        try {
            venvDir.resolve("pyvenv.cfg").createFile()
            assertTrue("Should be allowed to delete venv with pyvenv.cfg inside project", PathUtils.isSafeToDelete(venvDir, project))
        } finally {
            venvDir.toFile().deleteRecursively()
        }

        // 5. Test sandbox directory (Safe)
        val sandboxDir = projectRoot.resolve(PathUtils.SANDBOX_NAME)
        Files.createDirectories(sandboxDir)
        try {
            assertTrue("Should be allowed to delete ${PathUtils.SANDBOX_NAME} inside project", PathUtils.isSafeToDelete(sandboxDir, project))
        } finally {
            sandboxDir.toFile().deleteRecursively()
        }
        
        // 6. Test common user directories (Unsafe)
        val userHome = System.getProperty("user.home")?.let { Path.of(it) }
        if (userHome != null) {
            val documents = userHome.resolve("Documents")
            assertFalse("Should not be allowed to delete Documents", PathUtils.isSafeToDelete(documents, project))
        }
    }
}
