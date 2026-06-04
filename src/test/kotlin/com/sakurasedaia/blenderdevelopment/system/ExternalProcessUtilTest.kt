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

package com.sakurasedaia.blenderdevelopment.system

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/** Integration-style tests for [ExternalProcessUtil] command execution. */
class ExternalProcessUtilTest : BasePlatformTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun testJavaVersionCommandReturnsConsoleComponent() {
        val util = ExternalProcessUtil(project)
        val workingDir = System.getProperty("java.io.tmpdir")
        val disposable = Disposer.newDisposable()

        try {
            val result = runBlocking {
                util.runExternalToolAsync(
                    executable = bundledJavaExecutable(),
                    arguments = listOf("-version"),
                    workingDir = workingDir,
                    parentDisposable = disposable,
                )
            }

            assertEquals("java -version should exit cleanly", 0, result.exitCode)
            assertNotNull("Returned console component should not be null", result.component)
        } finally {
            ApplicationManager.getApplication().invokeAndWait { Disposer.dispose(disposable) }
        }
    }

    fun testPrepareCommandResolvesRelativeExecutableAgainstWorkingDirectory() {
        val util = ExternalProcessUtil(project)
        val tempDir = Files.createTempDirectory("external-process-util-test").toFile()
        val script = File(tempDir, "sample-tool")
        assertTrue(script.createNewFile())
        assertTrue(script.setExecutable(true))

        val prepared = util.prepareCommand(
            executable = "./sample-tool",
            arguments = listOf("--version"),
            workingDirectory = tempDir,
        )

        assertEquals(script.absolutePath, prepared.executable)
        assertEquals(listOf("--version"), prepared.arguments)
    }

    fun testPrepareCommandWrapsMacApplicationBundlesWithOpenArgs() {
        val util = ExternalProcessUtil(project)
        val bundlePath = "/Applications/Blender.app"

        val prepared = util.prepareCommand(
            executable = bundlePath,
            arguments = listOf("--debug"),
        )

        if (SystemInfo.isMac) {
            assertEquals("open", prepared.executable)
            assertEquals(listOf(bundlePath, "--args", "--debug"), prepared.arguments)
        } else {
            assertEquals(bundlePath, prepared.executable)
            assertEquals(listOf("--debug"), prepared.arguments)
        }
    }

    private fun bundledJavaExecutable(): String {
        val bin = File(System.getProperty("java.home"), "bin")
        return if (SystemInfo.isWindows) {
            File(bin, "java.exe").absolutePath
        } else {
            File(bin, "java").absolutePath
        }
    }
}
