package com.sakurasedaia.blenderdevelopment.lib.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.core.BlenderInstallationScanner
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.util.currentProject


/** Handles user-initiated Blender installation scanning from plugin settings. */
@Service(Service.Level.APP)
class SettingsInstallationScanService {
    fun scanInstallations() {
        scanInstallations(projectOverride = null, onComplete = null)
    }

    fun scanInstallations(
        projectOverride: Project? = null,
        onComplete: ((List<PluginConfig.BlendInstallInfo>) -> Unit)? = null,
    ) {
        val project = projectOverride?.takeIf { !it.isDisposed }
            ?: currentProject()
        val logger = PluginLogger.Companion.getInstance(project)
        val notifications = NotificationModal.Companion.getInstance(project)
        val completionModalityState = ModalityState.defaultModalityState()

        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            try {
                logger.log("Starting user-initiated Blender installation scan from settings.")
                val pluginConfig = PluginConfig.Companion.getInstance()
                project.getService(BlenderInstallationScanner::class.java).refreshInstalledVersionsCache()
                val installs = pluginConfig.getDetectedBlenderInstalls()
                logger.log("Blender installation scan completed with ${installs.size} result(s).")

                val messageKey = if (installs.isEmpty()) {
                    "notification.settings.scan.completed.none"
                } else {
                    "notification.settings.scan.completed.found"
                }
                notifications.sendInfo(MessageBundle.message(messageKey, installs.size.toString()))
                if (onComplete != null) {
                    ApplicationManager.getApplication().invokeLater({
                        if (!project.isDisposed) {
                            onComplete(installs)
                        }
                    }, completionModalityState)
                }
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
