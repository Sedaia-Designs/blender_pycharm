/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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