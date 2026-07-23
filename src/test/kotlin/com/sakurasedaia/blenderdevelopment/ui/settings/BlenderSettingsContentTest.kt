package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.BlenderVersionCache
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
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
    assertTrue(installed.isInstalled)

    val notInstalled = rows.first { it.version.blMajorMinor == "4.2" }
    assertEquals("—", notInstalled.pythonVersion)
    assertEquals("Not detected", notInstalled.installStatus)
    assertFalse(notInstalled.isInstalled)
  }

  fun testSettingsContentUsesVerticalOnlyScrollPane() {
    val content = BlenderSettingsContent(
      onScanInstallations = {},
      onRefreshVersions = {},
      onClearVersionCache = {},
    )

    val scrollPane = content.component() as JBScrollPane
    assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, scrollPane.verticalScrollBarPolicy)
    assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)
  }

  fun testDiscoveredVersionWithoutPythonMappingShowsPlaceholder() {
    val rows = BlenderSettingsContent.buildVersionSettingsRows(
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

  fun testClearVersionCacheInvokesHook() {
    var cacheCleared = false
    val content = BlenderSettingsContent(
      onScanInstallations = {},
      onRefreshVersions = {},
      onClearVersionCache = { cacheCleared = true },
    )

    content.clearVersionCache()

    assertTrue(cacheCleared)
  }

  fun testVersionCacheNotificationMessagesAreLocalized() {
    assertEquals(
      "Refreshed Blender versions. 4 release(s) are available.",
      MessageBundle.message("notification.settings.versions.refresh.succeeded", "4"),
    )
    assertEquals(
      "Failed to refresh Blender versions. Check plugin logs for details.",
      MessageBundle.message("notification.settings.versions.refresh.failed"),
    )
    assertEquals(
      "Cleared the online Blender version cache. The built-in compatibility table is now active.",
      MessageBundle.message("notification.settings.versions.cache.cleared"),
    )
    assertEquals(
      "Installed Blender 4.5.8.",
      MessageBundle.message("notification.settings.versions.install.succeeded", "4.5.8"),
    )
    assertEquals(
      "Deleted the managed Blender 4.5.8 installation.",
      MessageBundle.message("notification.settings.versions.delete.succeeded", "4.5.8"),
    )
  }
}
