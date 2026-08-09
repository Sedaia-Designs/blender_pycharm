package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.core.InstallBlender
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.services.ScrapeBlenderVersionLists
import com.sakurasedaia.blenderdevelopment.lib.services.SettingsInstallationScanService
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.BlenderVersionCache
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.currentProject
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

internal class BlenderSettingsOperations(
  private val dependencies: Dependencies = Dependencies.production(),
) {
  constructor(project: Project) : this(Dependencies.production(project))

  fun refreshVersions(onComplete: (Result<List<BlenderVersion>>) -> Unit) {
    runInBackground(onComplete) {
      dependencies.log("Starting user-initiated Blender version refresh from settings.")
      runCatching(dependencies.refreshVersionCache).onSuccess { versions ->
        dependencies.markVersionUpdateChecked()
        dependencies.log("Blender version refresh completed with ${versions.size} release(s).")
        dependencies.sendInfo(
          MessageBundle.message("notification.settings.versions.refresh.succeeded", versions.size.toString()),
        )
      }.onFailure { error ->
        notifyFailureUnlessCancelled(
          error = error,
          message = MessageBundle.message("notification.settings.versions.refresh.failed"),
        )
      }
    }
  }

  fun scanInstallations(onComplete: (Result<List<PluginConfig.BlendInstallInfo>>) -> Unit) {
    runInBackground(onComplete) {
      dependencies.log("Starting user-initiated Blender installation scan from settings.")
      runCatching(dependencies.scanInstallations).onSuccess { installs ->
        dependencies.log("Blender installation scan completed with ${installs.size} result(s).")
        val messageKey = if (installs.isEmpty()) {
          "notification.settings.scan.completed.none"
        }
        else {
          "notification.settings.scan.completed.found"
        }
        dependencies.sendInfo(MessageBundle.message(messageKey, installs.size.toString()))
      }.onFailure { error ->
        notifyFailureUnlessCancelled(
          error = error,
          message = MessageBundle.message("notification.settings.scan.failed"),
        )
      }
    }
  }

  fun clearVersionCache(): Result<Unit> = runCatching {
    dependencies.clearVersionCache()
    dependencies.log("Cleared the online Blender version cache from settings.")
    dependencies.sendInfo(MessageBundle.message("notification.settings.versions.cache.cleared"))
  }.onFailure { error ->
    if (error !is CancellationException) {
      dependencies.sendError(MessageBundle.message("notification.settings.versions.cache.clear.failed"), error)
    }
  }

  fun installVersion(version: BlenderVersion, onComplete: (Result<Path>) -> Unit) {
    val modalityState = dependencies.currentModalityState()
    dependencies.installVersion(version.blMajorMinor).whenComplete { installedPath, completionError ->
      dependencies.invokeLater(modalityState) {
        val result = completionResult(installedPath, completionError)
        result.onSuccess {
          dependencies.sendInfo(
            MessageBundle.message("notification.settings.versions.install.succeeded", version.blVersion),
          )
        }.onFailure { error ->
          notifyFailureUnlessCancelled(
            error = error,
            message = MessageBundle.message("notification.settings.versions.install.failed", version.blVersion),
          )
        }
        onComplete(result)
      }
    }
  }

  fun deleteVersion(version: BlenderVersion, onComplete: (Result<Boolean>) -> Unit) {
    val modalityState = dependencies.currentModalityState()
    dependencies.deleteVersion(version.blMajorMinor).whenComplete { deleted, completionError ->
      dependencies.invokeLater(modalityState) {
        val result = completionResult(deleted == true, completionError)
        result.onSuccess { wasDeleted ->
          val messageKey = if (wasDeleted) {
            "notification.settings.versions.delete.succeeded"
          }
          else {
            "notification.settings.versions.delete.not-found"
          }
          dependencies.sendInfo(MessageBundle.message(messageKey, version.blVersion))
        }.onFailure { error ->
          notifyFailureUnlessCancelled(
            error = error,
            message = MessageBundle.message("notification.settings.versions.delete.failed", version.blVersion),
          )
        }
        onComplete(result)
      }
    }
  }

  private fun <T> runInBackground(
    onComplete: (Result<T>) -> Unit,
    operation: () -> Result<T>,
  ) {
    val modalityState = dependencies.currentModalityState()
    dependencies.executeInBackground {
      val result = operation()
      dependencies.invokeLater(modalityState) {
        onComplete(result)
      }
    }
  }

  private fun notifyFailureUnlessCancelled(error: Throwable, message: String) {
    if (error !is CancellationException) {
      dependencies.sendError(message, error)
    }
  }

  private fun <T> completionResult(value: T, completionError: Throwable?): Result<T> =
    completionError?.let { Result.failure(unwrapCompletionError(it)) } ?: Result.success(value)

  private fun unwrapCompletionError(error: Throwable): Throwable = when (error) {
    is CompletionException, is ExecutionException -> error.cause?.let(::unwrapCompletionError) ?: error
    else -> error
  }

  internal data class Dependencies(
    val refreshVersionCache: () -> List<BlenderVersion>,
    val scanInstallations: () -> List<PluginConfig.BlendInstallInfo>,
    val clearVersionCache: () -> Unit,
    val installVersion: (String) -> CompletableFuture<Path>,
    val deleteVersion: (String) -> CompletableFuture<Boolean>,
    val markVersionUpdateChecked: () -> Unit,
    val log: (String) -> Unit,
    val sendInfo: (String) -> Unit,
    val sendError: (String, Throwable?) -> Unit,
    val executeInBackground: (() -> Unit) -> Unit,
    val invokeLater: (ModalityState, () -> Unit) -> Unit,
    val currentModalityState: () -> ModalityState,
  ) {
    companion object {
      fun production(project: Project = currentProject()): Dependencies {
        val application = ApplicationManager.getApplication()
        val config = PluginConfig.getInstance()
        val installer = InstallBlender.getInstance()
        val logger = PluginLogger.getInstance(project)
        val notifications = NotificationModal.getInstance(project)
        return Dependencies(
          refreshVersionCache = {
            runBlocking { ScrapeBlenderVersionLists.getInstance().refreshVersionCache() }
          },
          scanInstallations = {
            SettingsInstallationScanService.getInstance().scanInstallations(project)
          },
          clearVersionCache = { BlenderVersionCache.getInstance().clear() },
          installVersion = installer::extractBlender,
          deleteVersion = installer::deleteVersion,
          markVersionUpdateChecked = config::markBlenderUpdateChecked,
          log = { message -> logger.log(message) },
          sendInfo = { message -> notifications.sendInfo(message) },
          sendError = { message, error -> notifications.sendError(message, throwable = error) },
          executeInBackground = { action -> application.executeOnPooledThread(action) },
          invokeLater = { modalityState, action -> application.invokeLater(action, modalityState) },
          currentModalityState = ModalityState::current,
        )
      }
    }
  }
}
