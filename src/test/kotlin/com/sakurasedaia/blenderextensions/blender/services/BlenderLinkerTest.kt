package com.sakurasedaia.blenderextensions.blender.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.name

class BlenderLinkerTest : BasePlatformTestCase() {

    @Test
    fun testVersionComparison() {
        val versions = listOf("4.8", "4.9", "4.10")
        val latest = versions.maxWithOrNull { v1, v2 ->
            BlenderHelper.compareVersions(
                BlenderHelper.parseVersion(v1),
                BlenderHelper.parseVersion(v2)
            )
        }
        
        assertEquals("4.10", latest)
        
        // Test helper directly
        assertEquals(listOf(4, 10), BlenderHelper.parseVersion("4.10"))
        assertEquals(listOf(4, 9), BlenderHelper.parseVersion("4.9"))
        
        // Test edge cases
        assertEquals(1, BlenderHelper.compareVersions(listOf(4, 10), listOf(4, 9)))
        assertEquals(-1, BlenderHelper.compareVersions(listOf(4, 9), listOf(4, 10)))
        assertEquals(0, BlenderHelper.compareVersions(listOf(4, 10), listOf(4, 10)))
        assertEquals(1, BlenderHelper.compareVersions(listOf(4, 10, 1), listOf(4, 10)))
        assertEquals(-1, BlenderHelper.compareVersions(listOf(4, 10), listOf(4, 10, 1)))
    }

    @Test
    fun testGetExtensionsRepoDirSandboxed() {
        val linker = BlenderLinker.getInstance(project)
        val repoDir = linker.getExtensionsRepoDir(isSandboxed = true)
        
        assertNotNull(repoDir)
        val projectPath = project.basePath
        assertNotNull(projectPath)
        
        val expectedPath = Path.of(projectPath!!, ".venv", "blender_sandbox", "scripts", "extensions", "blender_pycharm")
        assertEquals(expectedPath.toAbsolutePath().toString(), repoDir!!.toAbsolutePath().toString())
    }
}
