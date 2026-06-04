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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import javax.swing.JComponent

/** Integration-style tests for [ExternalProcessUtil] command execution. */
class ExternalProcessUtilTest : BasePlatformTestCase() {
    
    /**
     * Verifies `echo` execution returns a non-null console component.
     *
     * @return `Unit`.
     */
    fun testEchoCommandReturnsConsoleComponent() {
        val util = ExternalProcessUtil(project)
        val workingDir = System.getProperty("java.io.tmpdir")
        
        val component: JComponent = runBlocking {
            util.runExternalToolAsync(
                executable = "echo",
                arguments = listOf("hello", "from", "ExternalProcessUtil"),
                workingDir = workingDir
            )
        }
        
        assertNotNull("Returned console component should not be null", component)
    }
    
    /**
     * Verifies listing directory contents returns a non-null console component.
     *
     * @return `Unit`.
     */
    fun testLsCommandReturnsConsoleComponent() {
        val util = ExternalProcessUtil(project)
        val workingDir = System.getProperty("java.io.tmpdir")
        
        val component: JComponent = runBlocking {
            util.runExternalToolAsync(
                executable = "ls",
                arguments = emptyList(),
                workingDir = workingDir
            )
        }
        
        assertNotNull("Returned console component should not be null", component)
    }
}
