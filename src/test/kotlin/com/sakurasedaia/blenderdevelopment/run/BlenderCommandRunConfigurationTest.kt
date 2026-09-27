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

@file:Suppress("KotlinMisorderedAssertEqualsArguments")

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import org.jdom.Element

class BlenderCommandRunConfigurationTest : BasePlatformTestCase() {
    fun testBlenderConfigurationTypeRegistersLaunchCommandAndExtensionBuildFactories() {
        val factories = BlenderConfigurationType().configurationFactories

        assertEquals(3, factories.size)
        assertEquals("BlenderLaunchConfigurationFactory", factories[0].id)
        assertEquals("BlenderCommandConfigurationFactory", factories[1].id)
        assertEquals("BlenderExtensionBuildConfigurationFactory", factories[2].id)
    }

    fun testCommandArgumentsPersistWithTheRunConfiguration() {
        val configuration = createConfiguration()
        configuration.commandArguments = "extension build --source-dir src"
        val serializedConfiguration = Element("configuration")

        configuration.writeExternal(serializedConfiguration)
        val restoredConfiguration = createConfiguration()
        restoredConfiguration.readExternal(serializedConfiguration)

        assertEquals("extension build --source-dir src", restoredConfiguration.commandArguments)
    }

    fun testBlankCommandFailsValidation() {
        configureBlenderPath()
        val configuration = createConfiguration()

        try {
            configuration.checkConfiguration()
        } catch (_: RuntimeConfigurationError) {
            return
        }
        fail("Expected a blank Blender command to fail validation")
    }

    fun testCommandOptionFailsValidationWhenTheConfigurationAddsIt() {
        configureBlenderPath()
        val configuration = createConfiguration()
        configuration.commandArguments = "--command extension list"

        try {
            configuration.checkConfiguration()
        } catch (_: RuntimeConfigurationError) {
            return
        }
        fail("Expected a duplicate --command option to fail validation")
    }

    fun testKnownCommandOptionWithoutValueFailsValidation() {
        configureBlenderPath()
        val configuration = createConfiguration()
        configuration.commandArguments = "extension build --source-dir"

        try {
            configuration.checkConfiguration()
        } catch (_: RuntimeConfigurationError) {
            return
        }
        fail("Expected a known option without its value to fail validation")
    }

    fun testInstallationDefinedCommandPassesValidation() {
        configureBlenderPath()
        val configuration = createConfiguration()
        configuration.commandArguments = "custom_command --installation-defined-option"

        configuration.checkConfiguration()
    }

    fun testCommandEditorStateSeparatesCommandFromArguments() {
        assertEquals(
            BlenderCommandEditorState(
                command = "extension",
                arguments = "build --source-dir \"Extension Source\"",
            ),
            BlenderCommandEditorState.fromCommandLine("extension build --source-dir \"Extension Source\""),
        )
    }

    fun testCommandEditorStatePreservesCustomCommand() {
        val state = BlenderCommandEditorState.fromCommandLine("custom_command --custom-option value")

        assertEquals("custom_command", state.command)
        assertEquals("--custom-option value", state.arguments)
        assertEquals("custom_command --custom-option value", state.toCommandLine())
    }

    fun testCommandEditorStateHandlesCommandWithoutArguments() {
        val state = BlenderCommandEditorState.fromCommandLine("sysinfo")

        assertEquals("sysinfo", state.command)
        assertTrue(state.arguments.isEmpty())
        assertEquals("sysinfo", state.toCommandLine())
    }

    private fun createConfiguration(): BlenderCommandRunConfiguration {
        val factory = BlenderConfigurationType().configurationFactories[1]
        return factory.createTemplateConfiguration(project) as BlenderCommandRunConfiguration
    }

    private fun configureBlenderPath() {
        ProjectConfig.getInstance(project).setBlenderPath("/path/to/blender")
    }
}
