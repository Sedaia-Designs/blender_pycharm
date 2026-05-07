package com.sakurasedaia.blenderdevelopment.alerts

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

class NotificationModal (private val project: Project) {
    companion object {
        private const val GROUP_ID = "Blender Development"
        
        private fun getGroup(): NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
    }
    
    fun sendError(title: String, content: String) {
        getGroup().createNotification(title, content, NotificationType.ERROR).notify(project)
    }
    
    fun sendWarning(title: String, content: String) {
        getGroup().createNotification(title, content, NotificationType.WARNING).notify(project)
    }
    
    fun sendInfo(title: String, content: String) {
        getGroup().createNotification(title, content, NotificationType.INFORMATION).notify(project)
    }
}