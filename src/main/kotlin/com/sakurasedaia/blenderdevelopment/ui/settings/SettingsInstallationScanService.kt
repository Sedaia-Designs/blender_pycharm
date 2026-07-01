package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.ProjectManager
import com.sakurasedaia.blenderdevelopment.blender.InstallationScanner
import com.sakurasedaia.blenderdevelopment.lib.PluginConfig
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger

/** Handles user-initiated Blender installation scanning from plugin settings. */
@Service(Service.Level.APP)
class SettingsInstallationScanService {
    fun scanInstallations() {
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.openProjects.firstOrNull { it.isOpen && !it.isDisposed } ?: projectManager.defaultProject
        val logger = PluginLogger.getInstance(project)
        val notifications = NotificationModal.getInstance(project)

        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            try {
                logger.log("Starting user-initiated Blender installation scan from settings.")
                val pluginConfig = PluginConfig.getInstance()
                project.getService(InstallationScanner::class.java).refreshInstalledVersionsCache()
                val installs = pluginConfig.getDetectedBlenderInstalls()
                logger.log("Blender installation scan completed with ${installs.size} result(s).")

                val messageKey = if (installs.isEmpty()) {
                    "notification.settings.scan.completed.none"
                } else {
                    "notification.settings.scan.completed.found"
                }
                notifications.sendInfo(MessageBundle.message(messageKey, installs.size.toString()))
            } catch (e: Exception) {
                logger.warn("User-initiated Blender installation scan failed.", e)
                notifications.sendError(MessageBundle.message("notification.settings.scan.failed"))
            }
        }
    }

    companion object {
        fun getInstance(): SettingsInstallationScanService =
            ApplicationManager.getApplication().getService(SettingsInstallationScanService::class.java)
    }
}
