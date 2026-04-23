package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

@Service(Service.Level.APP)
class BlenderTaskManager {

    /**
     * Runs a task in the background with a progress indicator.
     */
    fun run(project: Project?, title: String, canBeCancelled: Boolean = true, task: (ProgressIndicator) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled) {
            override fun run(indicator: ProgressIndicator) {
                task(indicator)
            }
        })
    }

    /**
     * Executes a task on a pooled thread without a progress indicator.
     */
    fun execute(task: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            task()
        }
    }

    companion object {
        fun getInstance(): BlenderTaskManager = ApplicationManager.getApplication().getService(BlenderTaskManager::class.java)
    }
}
