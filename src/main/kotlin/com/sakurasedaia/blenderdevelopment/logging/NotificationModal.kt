package com.sakurasedaia.blenderdevelopment.logging

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.currentProject

/** Project service wrapper around IntelliJ notifications for plugin UI feedback. */
@Service(Service.Level.PROJECT)
class NotificationModal(private val project: Project) {
  companion object {
    private const val GROUP_ID = "Blender Development Notifications"
    private val DEFAULT_TITLE: String
      get() = MessageBundle.message("notification.default.title")

    /**
     * Returns the notification service for a project.
     *
     * @param project target project.
     * @return project-level [NotificationModal] service.
     */
    fun getInstance(project: Project = currentProject()): NotificationModal = project.service()
  }

  /**
   * Sends an informational balloon notification.
   *
   * @param content notification body text.
   * @param title notification title.
   * @return `Unit`.
   */
  fun sendInfo(content: String, title: String = DEFAULT_TITLE) {
    send(title, content, NotificationType.INFORMATION)
  }

  /**
   * Sends a warning balloon notification.
   *
   * @param content notification body text.
   * @param title notification title.
   * @return `Unit`.
   */
  fun sendWarning(content: String, title: String = DEFAULT_TITLE) {
    send(title, content, NotificationType.WARNING)
  }

  /**
   * Sends an error balloon notification.
   *
   * @param content notification body text.
   * @param title notification title.
   * @return `Unit`.
   */
  fun sendError(content: String, title: String = DEFAULT_TITLE) {
    send(title, content, NotificationType.ERROR)
  }

  /**
   * Internal helper that creates and dispatches an IntelliJ notification.
   *
   * @param title notification title.
   * @param content notification body text.
   * @param type IntelliJ notification severity.
   * @return `Unit`.
   */
  private fun send(title: String, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID).createNotification(title, content, type).notify(project)
  }
}
