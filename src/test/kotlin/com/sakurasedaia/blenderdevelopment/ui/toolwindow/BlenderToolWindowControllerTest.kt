package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class BlenderToolWindowControllerTest : BasePlatformTestCase() {
  private lateinit var scope: CoroutineScope
  private lateinit var projectConfig: ProjectConfig
  private lateinit var pluginConfig: PluginConfig
  private lateinit var view: BlenderToolWindowView

  override fun setUp() {
    super.setUp()
    scope = CoroutineScope(SupervisorJob())
    pluginConfig = PluginConfig.getInstance()
    pluginConfig.loadState(PluginConfig.PluginState())
    projectConfig = ProjectConfig.getInstance(project)
    projectConfig.loadState(ProjectConfig.ProjectState())
    view = BlenderToolWindowView(project)
  }

  override fun tearDown() {
    try {
      scope.cancel()
    } finally {
      super.tearDown()
    }
  }

  fun testExternalProjectConfigUpdateRendersThroughStateFlow() {
    createController()

    projectConfig.setBlenderPath("/Applications/Custom Blender.app")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals("/Applications/Custom Blender.app", view.blenderPath)
  }

  fun testInitialRenderUsesPersistedDetectedInstallation() {
    val persistedInstall = install("Blender 4.5", "/Applications/Blender 4.5.app")
    pluginConfig.loadState(PluginConfig.PluginState(detectedBlender = listOf(persistedInstall)))

    createController()

    assertEquals(persistedInstall.path, view.blenderPath)
  }

  fun testScanReplacesRemovedSelectedInstallation() {
    val originalInstall = install("Blender 4.5", "/Applications/Blender 4.5.app")
    val replacementInstall = install("Blender 4.2", "/Applications/Blender 4.2.app")
    var detectedInstallations = listOf(originalInstall)
    projectConfig.setBlenderPath(originalInstall.path)
    val controller = createController(
      initialInstallations = detectedInstallations,
      detectedInstallations = { detectedInstallations },
    )

    detectedInstallations = listOf(replacementInstall)
    controller.scanForInstallations()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals(replacementInstall.path, projectConfig.getBlenderPath())
    assertEquals(replacementInstall.path, view.blenderPath)
  }

  fun testScanPreservesCustomProjectPath() {
    val customPath = "/opt/blender-custom"
    projectConfig.setBlenderPath(customPath)
    val controller = createController(
      initialInstallations = listOf(install("Blender 4.5", "/Applications/Blender 4.5.app")),
      detectedInstallations = { emptyList() },
    )

    controller.scanForInstallations()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals(customPath, projectConfig.getBlenderPath())
    assertEquals(customPath, view.blenderPath)
  }

  private fun createController(
    initialInstallations: List<PluginConfig.BlendInstallInfo> = pluginConfig.stateFlow.value.detectedBlenderInstalls,
    detectedInstallations: () -> List<PluginConfig.BlendInstallInfo> = { initialInstallations },
  ): BlenderToolWindowController {
    pluginConfig.setDetectedBlenderInstalls(initialInstallations)
    return BlenderToolWindowController(
      scope = scope,
      view = view,
      projectConfig = projectConfig,
      pluginConfig = pluginConfig,
      scanInstallations = { onCompleted ->
        pluginConfig.setDetectedBlenderInstalls(detectedInstallations())
        onCompleted()
      },
      installStubs = {},
      reloadAddon = {},
      logAutosave = {},
    )
  }

  private fun install(name: String, path: String): PluginConfig.BlendInstallInfo {
    return PluginConfig.BlendInstallInfo(name = name, version = "4.5.0", path = path)
  }
}
