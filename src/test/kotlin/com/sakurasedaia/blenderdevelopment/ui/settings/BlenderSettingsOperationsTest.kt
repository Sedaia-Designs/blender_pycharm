package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

internal class BlenderSettingsOperationsTest : BasePlatformTestCase() {
  fun testRefreshRunsInBackgroundCompletesOnUiAndReportsSuccess() {
    val versions = BlenderVersions.LIST.take(2)
    val events = mutableListOf<String>()
    val operations = operations(
      refreshVersionCache = {
        events += "refresh"
        versions
      },
      markVersionUpdateChecked = { events += "checked" },
      log = { events += "log:$it" },
      sendInfo = { events += "info:$it" },
      executeInBackground = {
        events += "background"
        it()
      },
      invokeLater = { _, action ->
        events += "ui"
        action()
      },
    )
    var completion: Result<List<BlenderVersion>>? = null

    operations.refreshVersions {
      events += "complete"
      completion = it
    }

    assertEquals(versions, completion!!.getOrThrow())
    assertTrue(events.indexOf("background") < events.indexOf("refresh"))
    assertTrue(events.indexOf("refresh") < events.indexOf("ui"))
    assertTrue(events.indexOf("ui") < events.indexOf("complete"))
    assertTrue(events.contains("checked"))
    assertTrue(
      events.contains(
        "info:${MessageBundle.message("notification.settings.versions.refresh.succeeded", versions.size.toString())}",
      ),
    )
  }

  fun testScanFailureCompletesWithFailureAndReportsError() {
    val failure = IllegalStateException("scan failed")
    val errors = mutableListOf<Pair<String, Throwable?>>()
    val operations = operations(
      scanInstallations = { throw failure },
      sendError = { message, error -> errors += message to error },
    )
    var completion: Result<List<PluginConfig.BlendInstallInfo>>? = null

    operations.scanInstallations { completion = it }

    assertSame(failure, completion!!.exceptionOrNull())
    assertEquals(
      MessageBundle.message("notification.settings.scan.failed"),
      errors.single().first,
    )
    assertSame(failure, errors.single().second)
  }

  fun testCancellationCompletesWithoutUserVisibleError() {
    val cancellation = CancellationException("cancelled")
    val errors = mutableListOf<Throwable?>()
    val operations = operations(
      refreshVersionCache = { throw cancellation },
      sendError = { _, error -> errors += error },
    )
    var completion: Result<List<BlenderVersion>>? = null

    operations.refreshVersions { completion = it }

    assertSame(cancellation, completion!!.exceptionOrNull())
    assertEmpty(errors)
  }

  fun testInstallUnwrapsCompletionFailureAndCompletesOnUi() {
    val version = BlenderVersions.LIST.first()
    val failure = IllegalStateException("install failed")
    val events = mutableListOf<String>()
    val errors = mutableListOf<Throwable?>()
    val operations = operations(
      installVersion = {
        CompletableFuture.failedFuture(CompletionException(failure))
      },
      sendError = { _, error -> errors += error },
      invokeLater = { _, action ->
        events += "ui"
        action()
      },
    )
    var completion: Result<Path>? = null

    operations.installVersion(version) {
      events += "complete"
      completion = it
    }

    assertSame(failure, completion!!.exceptionOrNull())
    assertSame(failure, errors.single())
    assertEquals(listOf("ui", "complete"), events)
  }

  fun testDeleteNotFoundIsSuccessfulAndUsesNotFoundNotification() {
    val version = BlenderVersions.LIST.first()
    val notifications = mutableListOf<String>()
    val operations = operations(
      deleteVersion = { CompletableFuture.completedFuture(false) },
      sendInfo = { notifications += it },
    )
    var completion: Result<Boolean>? = null

    operations.deleteVersion(version) { completion = it }

    assertFalse(completion!!.getOrThrow())
    assertEquals(
      MessageBundle.message("notification.settings.versions.delete.not-found", version.blVersion),
      notifications.single(),
    )
  }

  fun testClearCacheReportsSuccessAndFailure() {
    var failure: Throwable? = null
    val notifications = mutableListOf<String>()
    val errors = mutableListOf<Throwable?>()
    val operations = operations(
      clearVersionCache = { failure?.let { throw it } },
      sendInfo = { notifications += it },
      sendError = { _, error -> errors += error },
    )

    assertTrue(operations.clearVersionCache().isSuccess)
    assertEquals(
      MessageBundle.message("notification.settings.versions.cache.cleared"),
      notifications.single(),
    )

    failure = IllegalStateException("clear failed")
    val failedResult = operations.clearVersionCache()

    assertSame(failure, failedResult.exceptionOrNull())
    assertSame(failure, errors.single())
  }

  private fun operations(
    refreshVersionCache: () -> List<BlenderVersion> = { BlenderVersions.LIST },
    scanInstallations: () -> List<PluginConfig.BlendInstallInfo> = { emptyList() },
    clearVersionCache: () -> Unit = {},
    installVersion: (String) -> CompletableFuture<Path> = {
      CompletableFuture.completedFuture(Path.of("/managed/blender"))
    },
    deleteVersion: (String) -> CompletableFuture<Boolean> = { CompletableFuture.completedFuture(true) },
    markVersionUpdateChecked: () -> Unit = {},
    log: (String) -> Unit = {},
    sendInfo: (String) -> Unit = {},
    sendError: (String, Throwable?) -> Unit = { _, _ -> },
    executeInBackground: (() -> Unit) -> Unit = { it() },
    invokeLater: (ModalityState, () -> Unit) -> Unit = { _, action -> action() },
  ): BlenderSettingsOperations = BlenderSettingsOperations(
    BlenderSettingsOperations.Dependencies(
      refreshVersionCache = refreshVersionCache,
      scanInstallations = scanInstallations,
      clearVersionCache = clearVersionCache,
      installVersion = installVersion,
      deleteVersion = deleteVersion,
      markVersionUpdateChecked = markVersionUpdateChecked,
      log = log,
      sendInfo = sendInfo,
      sendError = sendError,
      executeInBackground = executeInBackground,
      invokeLater = invokeLater,
      currentModalityState = ModalityState::any,
    ),
  )
}
