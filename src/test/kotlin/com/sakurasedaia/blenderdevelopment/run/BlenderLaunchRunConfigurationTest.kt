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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class BlenderLaunchRunConfigurationTest : BasePlatformTestCase() {
    private lateinit var config: ProjectConfig
    private lateinit var projectPath: Path

    override fun setUp() {
        super.setUp()
        projectPath = Path.of(project.basePath!!)
        projectPath.createDirectories()
        config = ProjectConfig.getInstance(project)
        config.loadState(ProjectConfig.ProjectState())
        config.setBlenderPath(projectPath.resolve("blender").toString())
    }

    fun testBlankBlendFilePassesValidation() {
        createConfiguration().checkConfiguration()
    }

    fun testExistingBlendFilePassesValidation() {
        val blendFile = projectPath.resolve("scene.blend").createFile()
        config.setBlendFileToOpen(blendFile.toString())

        createConfiguration().checkConfiguration()
    }

    fun testUppercaseBlendExtensionPassesValidation() {
        val blendFile = projectPath.resolve("scene.BLEND").createFile()
        config.setBlendFileToOpen(blendFile.toString())

        createConfiguration().checkConfiguration()
    }

    fun testWrongBlendFileExtensionFailsValidation() {
        val file = projectPath.resolve("scene.txt").createFile()
        config.setBlendFileToOpen(file.toString())

        assertValidationFails()
    }

    fun testMissingBlendFileFailsValidation() {
        config.setBlendFileToOpen(projectPath.resolve("missing.blend").toString())

        assertValidationFails()
    }

    fun testMalformedBlendFilePathFailsValidation() {
        config.loadState(
            ProjectConfig.ProjectState(
                blenderPath = projectPath.resolve("blender").toString(),
                blendFileToOpen = "\u0000",
            )
        )

        assertValidationFails()
    }

    private fun assertValidationFails() {
        try {
            createConfiguration().checkConfiguration()
        } catch (_: RuntimeConfigurationError) {
            return
        }
        fail("Expected the configured blend file to fail validation")
    }

    private fun createConfiguration(): BlenderLaunchRunConfiguration {
        val factory = BlenderConfigurationType().configurationFactories[0]
        return factory.createTemplateConfiguration(project) as BlenderLaunchRunConfiguration
    }
}
