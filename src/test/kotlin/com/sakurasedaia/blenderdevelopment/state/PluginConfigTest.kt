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

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer

class PluginConfigTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  private lateinit var config: PluginConfig

  override fun setUp() {
    super.setUp()
    config = PluginConfig.getInstance()
    config.loadState(PluginConfig.PluginState())
  }

  fun testDefaultStateIsInitialized() {
    assertTrue(config.getBlenderInstallPath().isNotBlank())
    assertTrue(config.getCodeCompletionPath().isNotBlank())
    assertTrue(config.getDownloadPath().isNotBlank())
    assertTrue(config.getLogPath().isNotBlank())
    assertTrue(config.getClearDownloadsAfterInstall())
    assertEquals(2, config.getDownloadCacheSize())
    assertTrue(config.getDetectedBlenderInstalls().isEmpty())
    assertEquals("4.2", config.getMinimumBlenderVersion())
    assertTrue(config.getGlobalEnvironmentVariables().isEmpty())
    assertEquals(1, config.getBlenderUpdateCheck().interval)
    assertEquals(PluginConfig.TimeUnits.WEEK, config.getBlenderUpdateCheck().intervalType)
    assertEquals(0L, config.getBlenderUpdateCheck().lastCheckedEpochMillis)
  }

  fun testMutableFieldsRoundTrip() {
    val installs = listOf(
      PluginConfig.BlendInstallInfo(name = "Blender 4.5.8", version = "4.5.8", path = "/Applications/Blender.app"),
    )
    val env = mapOf("BLENDER_SYSTEM_SCRIPTS" to "/tmp/scripts")

    config.setBlenderInstallPath("/tmp/blender")
    config.setCodeCompletionPath("/tmp/fake-bpy")
    config.setDownloadPath("/tmp/downloads")
    config.setLogPath("/tmp/logs")
    config.setClearDownloadsAfterInstall(false)
    config.setDownloadCacheSize(8)
    config.setDetectedBlenderInstalls(installs)
    config.setMinimumBlenderVersion("4.5")
    config.setGlobalEnvironmentVariables(env)

    assertEquals("/tmp/blender", config.getBlenderInstallPath())
    assertEquals("/tmp/fake-bpy", config.getCodeCompletionPath())
    assertEquals("/tmp/downloads", config.getDownloadPath())
    assertEquals("/tmp/logs", config.getLogPath())
    assertFalse(config.getClearDownloadsAfterInstall())
    assertEquals(8, config.getDownloadCacheSize())
    assertEquals(installs, config.getDetectedBlenderInstalls())
    assertEquals("4.5", config.getMinimumBlenderVersion())
    assertEquals(env, config.getGlobalEnvironmentVariables())
  }

  fun testDetectedBlenderInstallsRoundTripThroughXml() {
    val installs = listOf(
      PluginConfig.BlendInstallInfo(
        name = "Blender 4.5.8 (User)",
        version = "4.5.8",
        path = "/Applications/Blender.app",
      ),
      PluginConfig.BlendInstallInfo(
        name = "Blender 4.2.12 (Custom)",
        version = "4.2.12",
        path = "/opt/blender 4.2",
      ),
    )
    val original = PluginConfig.PluginState(detectedBlender = installs)

    val serialized = XmlSerializer.serialize(original)
    val xml = JDOMUtil.writeElement(serialized)
    val restored = XmlSerializer.deserialize(serialized, PluginConfig.PluginState::class.java)

    assertTrue(
      "Installed Blender details must be persisted as XML attributes; got: $xml",
      xml.contains("""<BlendInstallInfo name="Blender 4.5.8 (User)" version="4.5.8" path="/Applications/Blender.app" />"""),
    )
    assertEquals(installs, restored.detectedBlender)
  }

  fun testDetectedBlenderInstallCacheOwnsItsValues() {
    val install = PluginConfig.BlendInstallInfo(
      name = "Blender 4.5.8 (User)",
      version = "4.5.8",
      path = "/Applications/Blender.app",
    )

    config.setDetectedBlenderInstalls(listOf(install))
    install.path = "/changed/by/caller"
    val returnedInstall = config.getDetectedBlenderInstalls().single()
    returnedInstall.path = "/changed/after/read"

    assertEquals("/Applications/Blender.app", config.getDetectedBlenderInstalls().single().path)
  }

  fun testLoadStatePublishesDetectedBlenderInstalls() {
    val installs = listOf(
      PluginConfig.BlendInstallInfo(
        name = "Blender 4.5.8 (User)",
        version = "4.5.8",
        path = "/Applications/Blender.app",
      ),
    )

    config.loadState(PluginConfig.PluginState(detectedBlender = installs))

    assertEquals(installs, config.stateFlow.value.detectedBlenderInstalls)
  }

  fun testInvalidPersistedMinimumVersionFallsBackToDefault() {
    config.loadState(PluginConfig.PluginState(minimumBlenderVersion = "invalid"))

    assertEquals("4.2", config.getMinimumBlenderVersion())
  }

  fun testBlenderUpdateCheckTiming() {
    config.setBlenderUpdateCheck(
      PluginConfig.UpdateChecked(
        interval = 2,
        intervalType = PluginConfig.TimeUnits.HOUR,
        lastCheckedEpochMillis = 1_000L,
      ),
    )

    assertFalse(config.isBlenderUpdateCheckDue(1_000L + 7_199_999L))
    assertEquals(1L, config.millisUntilNextBlenderUpdateCheck(1_000L + 7_199_999L))
    assertTrue(config.isBlenderUpdateCheckDue(1_000L + 7_200_000L))
  }

  fun testNeverCheckedScheduleIsImmediatelyDue() {
    config.setBlenderUpdateCheck(PluginConfig.UpdateChecked())

    assertTrue(config.isBlenderUpdateCheckDue())
    assertEquals(0L, config.millisUntilNextBlenderUpdateCheck())
  }

  fun testMissingVersionCacheForcesImmediateRefresh() {
    val cache = BlenderVersionCache.getInstance()
    cache.clear()
    config.setBlenderUpdateCheck(
      PluginConfig.UpdateChecked(
        interval = 1,
        intervalType = PluginConfig.TimeUnits.WEEK,
        lastCheckedEpochMillis = 1_000L,
      ),
    )

    assertEquals(0L, config.millisUntilNextBlenderVersionRefresh(2_000L))

    cache.cacheDiscoveredVersions(listOf(listOf(4, 2, 21)))
    assertEquals(604_799_000L, config.millisUntilNextBlenderVersionRefresh(2_000L))
    cache.clear()
  }

  fun testMarkBlenderUpdateCheckedPersistsTimestamp() {
    config.markBlenderUpdateChecked(123_456L)

    assertEquals(123_456L, config.getBlenderUpdateCheck().lastCheckedEpochMillis)
  }

  fun testRejectsNonPositiveUpdateInterval() {
    assertThrows(IllegalArgumentException::class.java) {
      config.setBlenderUpdateCheck(PluginConfig.UpdateChecked(interval = 0))
    }
  }
}
