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

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger

@Service(Service.Level.PROJECT)
internal class NotificationModal(private val project: Project) {
    private val logger by lazy { PluginLogger.getInstance(project) }

    companion object {
        private const val GROUP_ID = "Blender Development Notifications"
        private const val DEFAULT_TITLE = "Blender Development"

        fun getInstance(project: Project): NotificationModal = project.service()
        
    }

    fun sendInfo(content: String, title: String = DEFAULT_TITLE) {
        send(title, content, NotificationType.INFORMATION)
    }

    fun sendWarning(content: String, title: String = DEFAULT_TITLE) {
        send(title, content, NotificationType.WARNING)
    }

    fun sendError(content: String, title: String = DEFAULT_TITLE, throwable: Throwable? = null) {
        send(title, content, NotificationType.ERROR)
        if (throwable != null) {
            logger.warn(content, throwable)
        } else {
            logger.warn(content)
        }
    }

    private fun send(title: String, content: String, type: NotificationType) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
    
}
