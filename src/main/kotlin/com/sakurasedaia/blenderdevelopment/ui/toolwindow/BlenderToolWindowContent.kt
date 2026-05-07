package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderdevelopment.common.MessageBundle
import javax.swing.JComponent

class BlenderToolWindowContent(private val project: Project) {
    fun getContent(): JComponent {
        return panel {
            row {
                label(MessageBundle.message("ui.toolwindow.text"))
            }
        }.apply { border = JBUI.Borders.empty(0, 10) }
    }
}