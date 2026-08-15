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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SimpleColoredComponent
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import org.jdom.Element
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTextField

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
        ignoredOutputDirectory,
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

  fun testSettingsEditorRendersLocalizedOperationNames() {
    val editor = createConfiguration().configurationEditor
    val operationField = operationField(editor.component)
    val operationList = JList(BlenderExtensionBuildOperation.entries.toTypedArray())

    val renderedNames = BlenderExtensionBuildOperation.entries.mapIndexed { index, operation ->
      val renderedComponent = operationField.renderer.getListCellRendererComponent(
        operationList,
        operation,
        index,
        false,
        false,
      )
      renderedComponent.descendants()
        .filterIsInstance<SimpleColoredComponent>()
        .single()
        .getCharSequence(false)
        .toString()
    }

    assertEquals(
      listOf(
        MessageBundle.message("run.configuration.blender.extension.build.operation.build"),
        MessageBundle.message("run.configuration.blender.extension.build.operation.validate"),
      ),
      renderedNames,
    )
  }

  fun testSettingsEditorResetsFromRunConfiguration() {
    val configuration = createConfiguration().apply {
      operation = BlenderExtensionBuildOperation.VALIDATE
      sourcePath = "/project source"
      outputDirectory = "/package output"
    }
    val editor = configuration.configurationEditor

    editor.resetFrom(configuration)

    assertEquals(BlenderExtensionBuildOperation.VALIDATE, operationField(editor.component).selectedItem)
    assertEquals("/project source", labeledTextField(editor.component, "run.configuration.blender.extension.build.source.label").text)
    assertEquals("/package output", labeledTextField(editor.component, "run.configuration.blender.extension.build.output.label").text)
  }

  fun testSettingsEditorAppliesTrimmedValuesToRunConfiguration() {
    val configuration = createConfiguration()
    val editor = configuration.configurationEditor
    val component = editor.component
    operationField(component).selectedItem = BlenderExtensionBuildOperation.VALIDATE
    labeledTextField(component, "run.configuration.blender.extension.build.source.label").text = "  /project source  "
    labeledTextField(component, "run.configuration.blender.extension.build.output.label").text = "  /package output  "

    editor.applyTo(configuration)

    assertEquals(BlenderExtensionBuildOperation.VALIDATE, configuration.operation)
    assertEquals("/project source", configuration.sourcePath)
    assertEquals("/package output", configuration.outputDirectory)
  }

  fun testValidateSelectionHidesOutputDirectory() {
    val editor = createConfiguration().configurationEditor
    val component = editor.component
    val outputLabel = labeledComponent(component, "run.configuration.blender.extension.build.output.label")
    val outputField = labeledTextField(component, "run.configuration.blender.extension.build.output.label")
      .ancestor<TextFieldWithBrowseButton>()

    operationField(component).selectedItem = BlenderExtensionBuildOperation.VALIDATE

    assertFalse(outputLabel.isVisible)
    assertFalse(outputField.isVisible)
  }

  private fun createConfiguration(): BlenderExtensionBuildRunConfiguration {
    val factory = BlenderConfigurationType().configurationFactories[2]
    return factory.createTemplateConfiguration(project) as BlenderExtensionBuildRunConfiguration
  }

  private fun configureBlenderPath() {
    ProjectConfig.getInstance(project).setBlenderPath("/path/to/blender")
  }

  private val ignoredOutputDirectory: String = "ignored"

  @Suppress("UNCHECKED_CAST")
  private fun operationField(component: Component): ComboBox<BlenderExtensionBuildOperation> {
    return component.descendants().filterIsInstance<ComboBox<*>>().single() as ComboBox<BlenderExtensionBuildOperation>
  }

  private fun labeledTextField(component: Component, messageKey: String): JTextField {
    return labeledComponent(component, messageKey).labelFor as JTextField
  }

  private fun labeledComponent(component: Component, messageKey: String): JLabel {
    val labelText = MessageBundle.message(messageKey)
    return component.descendants()
      .filterIsInstance<JLabel>()
      .single { it.text == labelText }
  }

  private inline fun <reified T : Component> Component.ancestor(): T {
    return generateSequence(parent) { it.parent }
      .filterIsInstance<T>()
      .first()
  }

  private fun Component.descendants(): Sequence<Component> = sequence {
    yield(this@descendants)
    if (this@descendants is Container) {
      this@descendants.components.forEach { child ->
        yieldAll(child.descendants())
      }
    }
  }
}
