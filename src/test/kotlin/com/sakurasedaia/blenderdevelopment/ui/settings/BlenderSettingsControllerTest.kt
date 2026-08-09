package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.settings.versions.BlenderVersionManagementView

internal class BlenderSettingsControllerTest : BasePlatformTestCase() {
  fun testResetModifiedDetectionAndApplyCoverEveryFormField() {
    val config = PluginConfig.getInstance().apply {
      setBlenderInstallPath("/original/blender")
      setCodeCompletionPath("/original/completion")
      setLogPath("/original/logs")
      setDownloadPath("/original/downloads")
      setClearDownloadsAfterInstall(true)
      setMinimumBlenderVersion("4.2")
      setGlobalEnvironmentVariables(mapOf("ORIGINAL" to "value"))
    }
    val view = BlenderSettingsView(BlenderVersionManagementView())
    val controller = BlenderSettingsController(view, config)

    controller.reset()

    assertFalse(controller.isModified())
    val original = view.readForm()
    listOf(
      original.copy(blenderInstallPath = "/modified/blender"),
      original.copy(codeCompletionPath = "/modified/completion"),
      original.copy(logPath = "/modified/logs"),
      original.copy(downloadPath = "/modified/downloads"),
      original.copy(clearDownloadsAfterInstall = false),
      original.copy(minimumBlenderVersion = "4.5"),
      original.copy(globalEnvironmentVariables = mapOf("MODIFIED" to "value")),
    ).forEach { modified ->
      view.renderForm(modified)
      assertTrue(controller.isModified())
    }

    val applied = BlenderSettingsForm(
      blenderInstallPath = "/applied/blender",
      codeCompletionPath = "/applied/completion",
      logPath = "/applied/logs",
      downloadPath = "/applied/downloads",
      clearDownloadsAfterInstall = false,
      minimumBlenderVersion = "4.5",
      globalEnvironmentVariables = mapOf("APPLIED" to "final value"),
    )
    view.renderForm(applied)

    controller.apply()

    assertEquals("/applied/blender", config.getBlenderInstallPath())
    assertEquals("/applied/completion", config.getCodeCompletionPath())
    assertEquals("/applied/logs", config.getLogPath())
    assertEquals("/applied/downloads", config.getDownloadPath())
    assertFalse(config.getClearDownloadsAfterInstall())
    assertEquals("4.5", config.getMinimumBlenderVersion())
    assertEquals(mapOf("APPLIED" to "final value"), config.getGlobalEnvironmentVariables())
    assertFalse(controller.isModified())
  }
}
