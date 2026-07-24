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

package com.sakurasedaia.blenderdevelopment.state

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import java.nio.file.Path

class ProjectConfigTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  private lateinit var config: ProjectConfig

  override fun setUp() {
    super.setUp()
    config = ProjectConfig.getInstance(project)
    config.loadState(ProjectConfig.ProjectState())
  }

  fun testDefaultStateValuesAreLoaded() {
    assertEquals("", config.getBlenderPath())
    assertEquals("", config.getInstalledStubRequirement())
    assertEquals("src/", config.getSourceFolder())
    assertEquals("", config.getRunArguments())
    assertEquals(BlenderLogLevel.DEBUG, config.getBlenderLogLevel())
    assertTrue(config.getReloadOnSave())
    assertTrue(config.getJustMyCode())
    assertTrue(config.getEnvironmentVariables().isEmpty())
    assertNull(config.getScriptDirectories())
  }

  fun testConfigurationRoundTripPersistsAllMutableFields() {
    val envVars = mapOf("PYTHONPATH" to "/tmp/stubs", "BLENDER_USER_SCRIPTS" to "/tmp/scripts")
    val scriptDirectories = listOf("scripts/core", "scripts/extra")

    config.setBlenderPath("/Applications/Blender.app")
    config.setInstalledStubRequirement("fake-bpy-module-4.5")
    config.setAddonSymlinkName("dev_addon")
    config.setSourceFolder("addon/")
    config.setRunArguments("--factory-startup --python-exit-code 1")
    config.setBlenderLogLevel(BlenderLogLevel.TRACE)
    config.setReloadOnSave(false)
    config.setJustMyCode(false)
    config.setExtensionsRepository("extensions_example")
    config.setEnvironmentVariables(envVars)
    config.setScriptDirectories(scriptDirectories)

    assertEquals("/Applications/Blender.app", config.getBlenderPath())
    assertEquals("fake-bpy-module-4.5", config.getInstalledStubRequirement())
    assertEquals("dev_addon", config.getAddonSymlinkName())
    assertEquals("addon/", config.getSourceFolder())
    assertEquals("--factory-startup --python-exit-code 1", config.getRunArguments())
    assertEquals(BlenderLogLevel.TRACE, config.getBlenderLogLevel())
    assertFalse(config.getReloadOnSave())
    assertFalse(config.getJustMyCode())
    assertEquals("extensions_example", config.getExtensionsRepository())
    assertEquals(envVars, config.getEnvironmentVariables())
    assertEquals(
      scriptDirectories.map { Path.of(project.basePath!!).resolve(it).normalize().toString() },
      config.getScriptDirectories(),
    )
  }

  fun testRelativeBlenderPathResolvesAgainstProjectDirectory() {
    config.setBlenderPath("tools/blender")

    assertEquals(
      Path.of(project.basePath!!).resolve("tools/blender").normalize().toString(),
      config.getBlenderPath(),
    )
  }

  fun testLoadStateFallsBackToInfoForUnknownLogLevel() {
    config.loadState(ProjectConfig.ProjectState(blenderLogLevel = "NOT_A_LEVEL"))
    assertEquals(BlenderLogLevel.INFO, config.getBlenderLogLevel())
  }

  fun testAddonSymlinkNameNormalizesSpacesAndHyphensToUnderscoresOnSave() {
    config.setAddonSymlinkName("  Sakura-Rig Interfaces-Dev  ")
    assertEquals("Sakura_Rig_Interfaces_Dev", config.getAddonSymlinkName())
  }

  fun testAddonSymlinkAndExtensionsRepositoryRejectInvalidModuleNames() {
    val initialSymlink = config.getAddonSymlinkName()
    val initialRepository = config.getExtensionsRepository()

    config.setAddonSymlinkName("invalid.module")
    config.setExtensionsRepository("invalid-repository!")

    assertEquals(initialSymlink, config.getAddonSymlinkName())
    assertEquals(initialRepository, config.getExtensionsRepository())
  }

  fun testStateFlowPublishesIsolatedSnapshotsAfterUpdates() {
    val initialSnapshot = config.stateFlow.value
    val variables = mutableMapOf("MODE" to "development")

    config.setBlenderPath("/Applications/Blender.app")
    config.setEnvironmentVariables(variables)
    variables["MODE"] = "production"

    assertEquals("", initialSnapshot.blenderPath)
    assertEquals("/Applications/Blender.app", config.stateFlow.value.blenderPath)
    assertEquals(mapOf("MODE" to "development"), config.stateFlow.value.environmentVariables)
  }

  fun testLoadStatePublishesPersistedSnapshot() {
    config.loadState(
      ProjectConfig.ProjectState(
        blenderPath = "/Applications/Blender 4.5.app",
        addonSymlinkName = "loaded_addon",
      ),
    )

    assertEquals("/Applications/Blender 4.5.app", config.stateFlow.value.blenderPath)
    assertEquals("loaded_addon", config.stateFlow.value.addonSymlinkName)
  }
}
