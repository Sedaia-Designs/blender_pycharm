package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig

internal class BlenderVersionSettingsRowMapperTest : BasePlatformTestCase() {
  fun testRowsShowDetectedInstallAndMappedPythonVersion() {
    val rows = BlenderVersionSettingsRowMapper.map(
      versions = BlenderVersions.LIST,
      installs = listOf(
        PluginConfig.BlendInstallInfo(
          name = "Blender 4.5.12",
          version = "4.5.12",
          path = "/Applications/Blender.app",
        ),
      ),
    )

    val installed = rows.first { it.version.blMajorMinor == "4.5" }
    assertEquals("3.11.9", installed.pythonVersion)
    assertEquals("Installed (4.5.12)", installed.installStatus)
    assertTrue(installed.isInstalled)

    val notInstalled = rows.first { it.version.blMajorMinor == "4.2" }
    assertEquals("—", notInstalled.pythonVersion)
    assertEquals("Not detected", notInstalled.installStatus)
    assertFalse(notInstalled.isInstalled)
  }

  fun testDiscoveredVersionWithoutPythonMappingShowsPlaceholder() {
    val rows = BlenderVersionSettingsRowMapper.map(
      versions = listOf(
        BlenderVersion(
          installName = "Blender 4.3.9",
          blVersionList = listOf(4, 3, 9),
          compatWithOs = emptyMap(),
        ),
      ),
      installs = listOf(
        PluginConfig.BlendInstallInfo(
          name = "Blender 4.3.9",
          version = "4.3.9",
          path = "/Applications/Blender.app",
        ),
      ),
    )

    assertEquals("—", rows.single().pythonVersion)
    assertEquals("Installed (4.3.9)", rows.single().installStatus)
  }

  fun testMarkInstalledRefreshesOnlyMatchingVersionRow() {
    val rows = BlenderVersionSettingsRowMapper.map(
      versions = BlenderVersions.LIST,
      installs = emptyList(),
    )
    val installedVersion = BlenderVersions.LIST.first { it.blMajorMinor == "4.5" }

    val refreshedRows = BlenderVersionSettingsRowMapper.markInstalled(
      rows = rows,
      version = installedVersion,
    )

    val refreshed = refreshedRows.first { it.version.blMajorMinor == "4.5" }
    assertTrue(refreshed.isInstalled)
    assertEquals("Installed (${installedVersion.blVersion})", refreshed.installStatus)
    assertEquals(installedVersion.pyVersion, refreshed.pythonVersion)

    val unchangedVersion = BlenderVersions.LIST.first { it.blMajorMinor != "4.5" }
    assertSame(
      rows.first { it.version.blMajorMinor == unchangedVersion.blMajorMinor },
      refreshedRows.first { it.version.blMajorMinor == unchangedVersion.blMajorMinor },
    )
  }
}
