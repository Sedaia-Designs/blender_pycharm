/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderBootstrapScriptCleanupTest {
    @Test
    fun newScriptFileNameHasExpectedPrefixAndExtension() {
        val first = BlenderBootstrapScriptCleanup.newScriptFileName()
        val second = BlenderBootstrapScriptCleanup.newScriptFileName()

        assertTrue(first.startsWith("blender_runtime_launch_"))
        assertTrue(first.endsWith(".py"))
        assertTrue(second.startsWith("blender_runtime_launch_"))
        assertTrue(second.endsWith(".py"))
        assertFalse(first == second)
    }

    @Test
    fun cleanupScriptDeletesManagedBootstrapFile() {
        val tempDir = Files.createTempDirectory("bootstrap-cleanup-test")
        try {
            val scriptPath = tempDir.resolve(BlenderBootstrapScriptCleanup.newScriptFileName())
            Files.writeString(scriptPath, "print('hello')")

            val debugLogs = mutableListOf<String>()
            val warnLogs = mutableListOf<String>()
            BlenderBootstrapScriptCleanup.cleanupScript(
                path = scriptPath,
                debugLog = { debugLogs.add(it) },
                warnLog = { message, _ -> warnLogs.add(message) },
            )

            assertFalse(Files.exists(scriptPath))
            assertEquals(1, debugLogs.size)
            assertTrue(warnLogs.isEmpty())
        } finally {
            deleteRecursively(tempDir)
        }
    }

    @Test
    fun cleanupStaleScriptsDeletesOnlyManagedStaleScripts() {
        val tempDir = Files.createTempDirectory("stale-bootstrap-cleanup-test")
        try {
            val staleManaged = tempDir.resolve(BlenderBootstrapScriptCleanup.newScriptFileName())
            val freshManaged = tempDir.resolve(BlenderBootstrapScriptCleanup.newScriptFileName())
            val staleNonManaged = tempDir.resolve("other_script.py")
            val staleEmptyManaged = tempDir.resolve("blender_runtime_launch_empty.py")

            Files.writeString(staleManaged, "print('stale')")
            Files.writeString(freshManaged, "print('fresh')")
            Files.writeString(staleNonManaged, "print('other')")
            Files.writeString(staleEmptyManaged, "")

            Files.setLastModifiedTime(staleManaged, FileTime.from(Instant.now().minusSeconds(60 * 60 * 30)))
            Files.setLastModifiedTime(freshManaged, FileTime.from(Instant.now().minusSeconds(60 * 5)))
            Files.setLastModifiedTime(staleNonManaged, FileTime.from(Instant.now().minusSeconds(60 * 60 * 30)))
            Files.setLastModifiedTime(staleEmptyManaged, FileTime.from(Instant.now().minusSeconds(60 * 60 * 30)))

            val debugLogs = mutableListOf<String>()
            val warnLogs = mutableListOf<String>()
            BlenderBootstrapScriptCleanup.cleanupStaleScripts(
                directory = tempDir,
                debugLog = { debugLogs.add(it) },
                warnLog = { message, _ -> warnLogs.add(message) },
            )

            assertFalse(Files.exists(staleManaged))
            assertTrue(Files.exists(freshManaged))
            assertTrue(Files.exists(staleNonManaged))
            assertTrue(Files.exists(staleEmptyManaged))
            assertEquals(1, debugLogs.size)
            assertTrue(warnLogs.isEmpty())
        } finally {
            deleteRecursively(tempDir)
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
