package com.sakurasedaia.blenderdevelopment.lib.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.core.BlenderInstallationScanner
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.currentProject

/** Handles user-initiated Blender installation scanning from plugin settings. */
@Service(Service.Level.APP)
class SettingsInstallationScanService {
  /**
   * Scans for Blender installations in the target project's environment.
   *
   * This method performs work synchronously. Callers own background execution and user feedback.
   *
   * @param projectOverride project whose scanner should perform discovery, or the current project when omitted.
   * @return the detected Blender installations stored by the scanner.
   */
  fun scanInstallations(projectOverride: Project? = null): List<PluginConfig.BlendInstallInfo> {
    val project = projectOverride?.takeIf { !it.isDisposed }
      ?: currentProject()
    check(!project.isDisposed) { "Cannot scan Blender installations for a disposed project" }
    project.getService(BlenderInstallationScanner::class.java).refreshInstalledVersionsCache()
    return PluginConfig.getInstance().getDetectedBlenderInstalls()
  }

  /**
   * Runs an installation scan for legacy callers that need the service-owned background and feedback workflow.
   *
   * @param projectOverride project whose scanner should perform discovery, or the current project when omitted.
   * @param onComplete callback invoked on the UI thread after a successful scan.
   */
  fun scanInstallations(
    projectOverride: Project? = null,
    onComplete: (List<PluginConfig.BlendInstallInfo>) -> Unit,
  ) {
    val project = projectOverride?.takeIf { !it.isDisposed }
      ?: currentProject()
    val application = ApplicationManager.getApplication()
    val logger = PluginLogger.getInstance(project)
    val notifications = NotificationModal.getInstance(project)
    val completionModalityState = ModalityState.defaultModalityState()
    application.executeOnPooledThread {
      if (project.isDisposed) return@executeOnPooledThread
      try {
        logger.log("Starting user-initiated Blender installation scan from settings.")
        val installs = scanInstallations(project)
        logger.log("Blender installation scan completed with ${installs.size} result(s).")
        val messageKey = if (installs.isEmpty()) {
          "notification.settings.scan.completed.none"
        }
        else {
          "notification.settings.scan.completed.found"
        }
        notifications.sendInfo(MessageBundle.message(messageKey, installs.size.toString()))
        application.invokeLater({
          if (!project.isDisposed) onComplete(installs)
        }, completionModalityState)
      }
      catch (error: Exception) {
        logger.warn("User-initiated Blender installation scan failed.", error)
        notifications.sendError(MessageBundle.message("notification.settings.scan.failed"))
      }
    }
  }

  companion object {
    fun getInstance(): SettingsInstallationScanService =
      ApplicationManager.getApplication().getService(SettingsInstallationScanService::class.java)
  }
}
