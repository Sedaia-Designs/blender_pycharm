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

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import org.jdom.Element

class BlenderExtensionBuildRunConfigurationTest : BasePlatformTestCase() {
  fun testBuildArgumentsMapSourceAndOutputDirectories() {
    assertEquals(
      listOf(
        "extension",
        "build",
        "--source-dir",
        "/project source",
        "--output-dir",
        "/package output",
      ),
      BlenderExtensionBuildCommand.arguments(
        BlenderExtensionBuildOperation.BUILD,
        "/project source",
        "/package output",
      ),
    )
  }

  fun testValidateArgumentsUsePositionalSourcePath() {
    assertEquals(
      listOf("extension", "validate", "/project source"),
      BlenderExtensionBuildCommand.arguments(
        BlenderExtensionBuildOperation.VALIDATE,
        "/project source",
        ignoredOutputDirectory(),
      ),
    )
  }

  fun testBuildArgumentsPreserveRelativePaths() {
    assertEquals(
      listOf(
        "extension",
        "build",
        "--source-dir",
        "Extension/src",
        "--output-dir",
        "dist",
      ),
      BlenderExtensionBuildCommand.arguments(
        BlenderExtensionBuildOperation.BUILD,
        "Extension/src",
        "dist",
      ),
    )
  }

  fun testSettingsPersistWithRunConfiguration() {
    val configuration = createConfiguration()
    configuration.operation = BlenderExtensionBuildOperation.VALIDATE
    configuration.sourcePath = "/project source"
    configuration.outputDirectory = "/package output"
    val serializedConfiguration = Element("configuration")

    configuration.writeExternal(serializedConfiguration)
    val restoredConfiguration = createConfiguration()
    restoredConfiguration.readExternal(serializedConfiguration)

    assertEquals(BlenderExtensionBuildOperation.VALIDATE, restoredConfiguration.operation)
    assertEquals("/project source", restoredConfiguration.sourcePath)
    assertEquals("/package output", restoredConfiguration.outputDirectory)
  }

  fun testTemplateUsesPortableProjectRelativeDefaults() {
    ProjectConfig.getInstance(project).setSourceFolder("Extension/src")

    val configuration = createConfiguration()

    assertEquals("Extension/src", configuration.sourcePath)
    assertEquals(".", configuration.outputDirectory)
  }

  fun testSourcePathIsRequiredForBothOperations() {
    configureBlenderPath()
    val configuration = createConfiguration()
    configuration.operation = BlenderExtensionBuildOperation.VALIDATE
    configuration.sourcePath = ""

    try {
      configuration.checkConfiguration()
    }
    catch (_: RuntimeConfigurationError) {
      return
    }
    fail("Expected validation without a source path to fail configuration validation")
  }

  fun testBuildRequiresOutputDirectory() {
    configureBlenderPath()
    val configuration = createConfiguration()
    configuration.operation = BlenderExtensionBuildOperation.BUILD
    configuration.sourcePath = "/project source"
    configuration.outputDirectory = ""

    try {
      configuration.checkConfiguration()
    }
    catch (_: RuntimeConfigurationError) {
      return
    }
    fail("Expected a build without an output directory to fail validation")
  }

  fun testValidateDoesNotRequireOutputDirectory() {
    configureBlenderPath()
    val configuration = createConfiguration()
    configuration.operation = BlenderExtensionBuildOperation.VALIDATE
    configuration.sourcePath = "/project source"
    configuration.outputDirectory = ""

    configuration.checkConfiguration()
  }

  private fun createConfiguration(): BlenderExtensionBuildRunConfiguration {
    val factory = BlenderConfigurationType().configurationFactories[2]
    return factory.createTemplateConfiguration(project) as BlenderExtensionBuildRunConfiguration
  }

  private fun configureBlenderPath() {
    ProjectConfig.getInstance(project).setBlenderPath("/path/to/blender")
  }

  private fun ignoredOutputDirectory(): String = "ignored"
}
