package com.sakurasedaia.blenderdevelopment.lib.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.core.BlenderInstallationScanner
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.currentProject
import java.time.Duration
import java.util.concurrent.CancellationException

/** Handles user-initiated Blender installation scanning from plugin settings. */
@Service(Service.Level.APP)
class SettingsInstallationScanService {

  internal class ScanDeadlineExceededException : RuntimeException("Blender Install scan exceeded its deadline, cancelling process")

  private class ScanDeadline(
    private val timeout: Duration?,
    private val nanoTime: () -> Long,
  ) {
    private val startedAtNanos = nanoTime()

    init {
      require(timeout == null || !timeout.isNegative) {
        "Scan timeout must be zero or greater"
      }
    }

    fun hasExpired(): Boolean {
      val activeTimeout = timeout?.takeUnless {it.isZero} ?: return false
      val elapsedNanos = Duration.ofNanos(nanoTime() - startedAtNanos)
      return elapsedNanos >= activeTimeout
    }
  }


  /**
   * Scans for Blender installations and returns a list of detected installations.
   *
   * @param projectOverride An optional project to override the default project for the scan. If null, the current project is used.
   * @param shouldCancel A lambda function that determines if the scan should be cancelled. Defaults to a function that always returns false.
   * @param overallTimeout An optional timeout duration for the scan. `null` or [Duration.ZERO] disables the deadline.
   * @param nanoTime A lambda function to provide the current nanotime. Defaults to `System::nanoTime`.
   * @return A list of detected Blender installation information.
   * @throws IllegalStateException If the project is disposed of when attempting to scan.
   * @throws CancellationException If the scan is explicitly canceled or interrupted.
   * @throws ScanDeadlineExceededException If the scan exceeds the specified deadline.
   */
  fun scanInstallations(
    projectOverride: Project? = null,
    shouldCancel: () -> Boolean = { false },
    overallTimeout: Duration? = DEFAULT_SCAN_TIMEOUT,
    nanoTime: () -> Long = System::nanoTime,
  ): List<PluginConfig.BlendInstallInfo> {
    val project = projectOverride?.takeIf { !it.isDisposed }
      ?: currentProject()
    check(!project.isDisposed) { "Cannot scan Blender installations for a disposed project" }

    val deadline = ScanDeadline(overallTimeout, nanoTime)
    var deadlineExpired = false
    val scanShouldCancel = {
      when {
        shouldCancel() -> true
        project.isDisposed -> true
        Thread.currentThread().isInterrupted -> true
        deadline.hasExpired() -> {
          deadlineExpired = true
          true
        }
        else -> false
      }
    }

    try {
      project
        .getService(BlenderInstallationScanner::class.java)
        .refreshInstalledVersionsCache(scanShouldCancel)
    } catch (cancelled: CancellationException) {
      if (deadlineExpired) throw ScanDeadlineExceededException()
      throw cancelled
    }

    return PluginConfig.getInstance().getDetectedBlenderInstalls()
  }

  /**
   * Triggers a scan for Blender installations and processes the results upon completion.
   *
   * @param projectOverride An optional project to override the default project used during the scan.
   *                        If null or the provided project is disposed, the current project will be used.
   * @param onComplete A callback function that will be invoked with the list of detected
   *                   Blender installation information once the scan is complete.
   */
  fun scanInstallations(
    projectOverride: Project? = null,
    onComplete: (List<PluginConfig.BlendInstallInfo>) -> Unit,
  ) {
    val project = projectOverride?.takeIf { !it.isDisposed }
      ?: currentProject()
    val application = ApplicationManager.getApplication()
    val logger = PluginLogger.getInstance(project)
    val notifications = NotificationModal.getInstance(project)
    val completionModalityState = ModalityState.defaultModalityState()
    application.executeOnPooledThread {
      if (project.isDisposed) return@executeOnPooledThread
      try {
        logger.log("Starting user-initiated Blender installation scan from settings.")
        val installs = scanInstallations(
          projectOverride = project,
          shouldCancel = { project.isDisposed || Thread.currentThread().isInterrupted },
        )
        logger.log("Blender installation scan completed with ${installs.size} result(s).")
        val messageKey = if (installs.isEmpty()) {
          "notification.settings.scan.completed.none"
        }
        else {
          "notification.settings.scan.completed.found"
        }
        notifications.sendInfo(MessageBundle.message(messageKey, installs.size.toString()))
        application.invokeLater({
          if (!project.isDisposed) onComplete(installs)
        }, completionModalityState)
      }
      catch (deadline: ScanDeadlineExceededException) {
        logger.warn(ErrorTypes.INSTALL_SCAN_DEADLINE_EXCEEDED.toString(), deadline)
        sendScanFailedNotif(project)
      }
      catch (cancellation: CancellationException) {
        // Fail Silently
      }
      catch (error: Exception) {
        logger.error(ErrorTypes.INSTALL_SCAN_FAIL_GENERIC, error)
        sendScanFailedNotif(project)
      }
    }
  }
  private fun sendScanFailedNotif(project: Project) {
    val notifications = NotificationModal.getInstance(project)
    notifications.sendError(MessageBundle.message("notification.settings.scan.failed"))
  }
  companion object {
    fun getInstance(): SettingsInstallationScanService =
      ApplicationManager.getApplication().getService(SettingsInstallationScanService::class.java)

    private val DEFAULT_SCAN_TIMEOUT = Duration.ofSeconds(30)
  }
}
