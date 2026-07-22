package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.BlenderVersionCache
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import javax.swing.ScrollPaneConstants

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

  fun testSettingsContentUsesVerticalOnlyScrollPane() {
    val content = BlenderSettingsContent(
      onScanInstallations = {},
      onRefreshVersions = {},
    )

    val scrollPane = content.component() as JBScrollPane
    assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, scrollPane.verticalScrollBarPolicy)
    assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)
  }
}
