package com.sakurasedaia.blenderextensions.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

class BlenderNotification(private val project: Project) {

    private fun getGroup(group: String = "Blender Development"): NotificationGroup? =
        runCatching { NotificationGroupManager.getInstance().getNotificationGroup(group) }.getOrNull()

    fun sendError(title: String, content: String) {
        getGroup()?.createNotification(title, content, NotificationType.ERROR)?.notify(project)
    }

    fun sendWarning(title: String, content: String) {
        getGroup()?.createNotification(title, content, NotificationType.WARNING)?.notify(project)
    }

    fun sendInfo(title: String, content: String) {
        getGroup()?.createNotification(title, content, NotificationType.INFORMATION)?.notify(project)
    }
}
