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
  private lateinit var config: ProjectConfig
  private lateinit var view: BlenderToolWindowView

  override fun setUp() {
    super.setUp()
    scope = CoroutineScope(SupervisorJob())
    config = ProjectConfig.getInstance(project)
    config.loadState(ProjectConfig.ProjectState())
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

    config.setBlenderPath("/Applications/Custom Blender.app")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals("/Applications/Custom Blender.app", view.blenderPath)
  }

  fun testScanReplacesRemovedSelectedInstallation() {
    val originalInstall = install("Blender 4.5", "/Applications/Blender 4.5.app")
    val replacementInstall = install("Blender 4.2", "/Applications/Blender 4.2.app")
    var detectedInstallations = listOf(originalInstall)
    config.setBlenderPath(originalInstall.path)
    val controller = createController(
      initialInstallations = detectedInstallations,
      detectedInstallations = { detectedInstallations },
    )

    detectedInstallations = listOf(replacementInstall)
    controller.scanForInstallations()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals(replacementInstall.path, config.getBlenderPath())
    assertEquals(replacementInstall.path, view.blenderPath)
  }

  fun testScanPreservesCustomProjectPath() {
    val customPath = "/opt/blender-custom"
    config.setBlenderPath(customPath)
    val controller = createController(
      initialInstallations = listOf(install("Blender 4.5", "/Applications/Blender 4.5.app")),
      detectedInstallations = { emptyList() },
    )

    controller.scanForInstallations()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals(customPath, config.getBlenderPath())
    assertEquals(customPath, view.blenderPath)
  }

  private fun createController(
    initialInstallations: List<PluginConfig.BlendInstallInfo> = emptyList(),
    detectedInstallations: () -> List<PluginConfig.BlendInstallInfo> = { initialInstallations },
  ): BlenderToolWindowController {
    return BlenderToolWindowController(
      scope = scope,
      view = view,
      config = config,
      initialInstallations = initialInstallations,
      scanInstallations = { onCompleted -> onCompleted() },
      detectedInstallations = detectedInstallations,
      reloadAddon = {},
      logAutosave = {},
    )
  }

  private fun install(name: String, path: String): PluginConfig.BlendInstallInfo {
    return PluginConfig.BlendInstallInfo(name = name, version = "4.5.0", path = path)
  }
}
