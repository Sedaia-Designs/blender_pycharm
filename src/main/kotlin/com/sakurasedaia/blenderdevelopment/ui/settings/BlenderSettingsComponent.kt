package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.Disposable
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.settings.versions.BlenderVersionManagementController
import com.sakurasedaia.blenderdevelopment.ui.settings.versions.BlenderVersionManagementView
import javax.swing.JComponent

internal class BlenderSettingsComponent(
  private val config: PluginConfig = PluginConfig.getInstance(),
  private val operations: BlenderSettingsOperations = BlenderSettingsOperations(),
) : Disposable {
  private val versionView = BlenderVersionManagementView(
    isValidMinorVersion = PluginConfig::isValidMinorVersion,
  )
  private val versionController = BlenderVersionManagementController(
    view = versionView,
    refreshVersions = operations::refreshVersions,
    scanInstallations = operations::scanInstallations,
    clearVersionCache = operations::clearVersionCache,
    installVersion = operations::installVersion,
    deleteVersion = operations::deleteVersion,
    lastRefreshedEpochMillis = { config.getBlenderUpdateCheck().lastCheckedEpochMillis },
  )
  private val view = BlenderSettingsView(
    versionManagementView = versionView,
  )
  private val controller = BlenderSettingsController(view, config)
  private var isDisposed = false

  fun component(): JComponent = view.component()

  fun reset() {
    if (isDisposed) return
    controller.reset()
    versionController.reset(
      versions = BlenderVersions.LIST,
      installs = config.getDetectedBlenderInstalls(),
      lastRefreshedEpochMillis = config.getBlenderUpdateCheck().lastCheckedEpochMillis,
    )
  }

  fun isModified(): Boolean = !isDisposed && controller.isModified()

  fun apply() {
    if (isDisposed) return
    controller.apply()
  }

  override fun dispose() {
    if (isDisposed) return
    isDisposed = true
    versionController.dispose()
    operations.dispose()
  }
}
