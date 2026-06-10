package com.sakurasedaia.blenderdevelopment.logging

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlin.math.max
import kotlin.math.min

/**
 * Centralized helper for long-running Blender download/install progress tasks.
 * Uses IntelliJ's native background task API to avoid blocking the UI thread.
 */
@Service(Service.Level.PROJECT)
internal class InstallProgressMonitor(private val project: Project) {
    private val logger = PluginLogger.getInstance(project)
    private val notifications = NotificationModal.getInstance(project)

    internal class ProgressHandle(private val indicator: ProgressIndicator) {
        fun text(value: String) {
            indicator.text = value
        }

        fun details(value: String) {
            indicator.text2 = value
        }

        fun indeterminate(value: Boolean) {
            indicator.isIndeterminate = value
        }

        fun fraction(value: Double) {
            indicator.isIndeterminate = false
            indicator.fraction = min(1.0, max(0.0, value))
        }

        fun checkCanceled() {
            indicator.checkCanceled()
        }
    }

    fun runTask(
        title: String,
        cancellable: Boolean = true,
        action: (ProgressHandle) -> Unit,
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    logger.log("Started task: $title")
                    indicator.isIndeterminate = true
                    action(ProgressHandle(indicator))
                    logger.log("Finished task: $title")
                } catch (_: ProcessCanceledException) {
                    logger.warn("Canceled task: $title")
                    notifications.sendWarning("$title canceled.")
                } catch (t: Throwable) {
                    logger.warn("Failed task: $title", t)
                    notifications.sendError("Failed task '$title'. Check logs for details.", throwable = t)
                }
            }
        })
    }

    companion object {
        fun getInstance(project: Project): InstallProgressMonitor = project.service()
    }
}
