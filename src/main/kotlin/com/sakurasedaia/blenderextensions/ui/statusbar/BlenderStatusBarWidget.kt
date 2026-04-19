package com.sakurasedaia.blenderextensions.ui.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Alarm
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderCommunicationService
import com.sakurasedaia.blenderextensions.blender.services.BlenderService
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import javax.swing.Icon

class BlenderStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {
    private var statusBar: StatusBar? = null
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun ID(): String = "BlenderStatus"

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        scheduleUpdate()
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getSelectedValue(): String? {
        val service = BlenderService.getInstance(project)
        val commService = BlenderCommunicationService.getInstance(project)
        
        return when {
            commService.isConnected() -> LangManager.message("blender.status.connected")
            service.isRunning() -> LangManager.message("blender.status.disconnected")
            service.hasError() -> LangManager.message("blender.status.error")
            else -> LangManager.message("blender.status.not.running")
        }
    }

    override fun getPopupStep(): com.intellij.openapi.ui.popup.ListPopup? = null

    override fun getIcon(): Icon? {
        val service = BlenderService.getInstance(project)
        val commService = BlenderCommunicationService.getInstance(project)
        
        return when {
            commService.isConnected() -> BlenderIcons.Checkmark
            service.isRunning() -> BlenderIcons.BlenderColor
            service.hasError() -> com.intellij.icons.AllIcons.General.Error
            else -> BlenderIcons.Blender
        }
    }

    override fun getTooltipText(): String? = LangManager.message("settings.display.name")

    private fun scheduleUpdate() {
        if (statusBar == null) return
        
        alarm.addRequest({
            statusBar?.updateWidget(ID())
            scheduleUpdate()
        }, 1000)
    }
}
