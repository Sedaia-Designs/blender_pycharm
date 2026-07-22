package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.BlenderVersionCache
import com.sakurasedaia.blenderdevelopment.state.PluginConfig

class BlenderSettingsContentTest : BasePlatformTestCase() {
  override fun tearDown() {
    try {
      BlenderVersionCache.getInstance().clear()
    } finally {
      super.tearDown()
    }
  }

  fun testVersionRowsShowDetectedInstallAndMappedPythonVersion() {
    val rows = BlenderSettingsContent.buildVersionSettingsRows(
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

    val notInstalled = rows.first { it.version.blMajorMinor == "4.2" }
    assertEquals("—", notInstalled.pythonVersion)
    assertEquals("Not detected", notInstalled.installStatus)
  }
}
