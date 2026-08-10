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

package com.sakurasedaia.blenderdevelopment.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.lib.BlenderManifest
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.net.URI
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Service(Service.Level.PROJECT)
internal class BlenderRuntimeCommandService(private val project: Project) {
  private data class AddonTarget(val directory: Path, val moduleName: String)

  private val logger = PluginLogger.getInstance(project)
  private val notifications = NotificationModal.getInstance(project)
  private val projectConfig = ProjectConfig.getInstance(project)
  private val editorServerService = BlenderEditorServerService.getInstance(project)
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(4))
    .build()
  private val objectMapper = ObjectMapper()

  fun sendReloadCommand(showSuccessNotification: Boolean = true) {
    val addonTargets = resolveConfiguredAddonTargets()
    if (addonTargets.isEmpty()) {
      logger.warn(ErrorTypes.RUNTIME_RELOAD_MISSING_ADDON_DIRECTORIES.toString())
      notifications.sendWarning(MessageBundle.message("notification.blender.runtime.command.reload.source.missing"))
      return
    }
    val payload = mapOf(
      "type" to "reload",
      "names" to addonTargets.map { it.moduleName },
      "dirs" to addonTargets.map { it.directory.toString() },
    )
    sendCommand(
      payload = payload,
      onSuccessMessage = MessageBundle.message("notification.blender.runtime.command.reload.sent"),
      showSuccessNotification = showSuccessNotification,
    )
  }

  fun sendRunScriptCommand() {
    val scriptFile = selectedPythonFile() ?: run {
      notifications.sendWarning(MessageBundle.message("notification.blender.runtime.command.script.file.missing"))
      return
    }
    val payload = mapOf(
      "type" to "script",
      "path" to scriptFile.path,
    )
    sendCommand(
      payload = payload,
      onSuccessMessage = MessageBundle.message("notification.blender.runtime.command.script.sent", scriptFile.name),
    )
  }

  fun sendStopCommand() {
    sendCommand(
      payload = mapOf("type" to "stop"),
      onSuccessMessage = MessageBundle.message("notification.blender.runtime.command.stop.sent"),
    )
  }

  fun hasActiveSession(): Boolean = editorServerService.findLatestActiveSessionPayload() != null

  private fun sendCommand(
    payload: Map<String, Any>,
    onSuccessMessage: String,
    showSuccessNotification: Boolean = true,
  ) {
    val activeSession = editorServerService.findLatestActiveSessionPayload()
    if (activeSession == null) {
      logger.warn(ErrorTypes.RUNTIME_COMMAND_MISSING_SESSION.format(payload["type"]))
      notifications.sendWarning(MessageBundle.message("notification.blender.runtime.command.session.missing"))
      return
    }
    if (activeSession.blenderPort <= 0) {
      logger.warn(ErrorTypes.RUNTIME_COMMAND_INVALID_PORT.format(payload["type"], activeSession.identifier, activeSession.blenderPort))
      notifications.sendError(
        MessageBundle.message(
          "notification.blender.runtime.command.port.invalid",
          activeSession.blenderPort.toString(),
        ),
      )
      return
    }

    val endpoint = "http://127.0.0.1:${activeSession.blenderPort}/"
    AppExecutorUtil.getAppExecutorService().submit {
      runCatching {
        val requestBody = objectMapper.writeValueAsString(payload)
        val request = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(8))
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build()
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }.onSuccess { response ->
        if (response.statusCode() in 200..299) {
          editorServerService.markSessionActivity(activeSession.identifier)
          if (showSuccessNotification) {
            notifications.sendInfo(onSuccessMessage)
          }
          logger.debug("Sent Blender runtime command `${payload["type"]}` to session `${activeSession.identifier}`.")
        } else {
          logger.warn(
            ErrorTypes.RUNTIME_COMMAND_REJECTED.format(
              payload["type"],
              activeSession.identifier,
              response.statusCode(),
              response.body(),
            ),
          )
          notifications.sendError(
            MessageBundle.message(
              "notification.blender.runtime.command.failed",
              payload["type"]?.toString() ?: "",
              response.statusCode().toString(),
            ),
          )
        }
      }.onFailure { error ->
        logger.warn(ErrorTypes.RUNTIME_COMMAND_SEND_FAILED.format(payload["type"]), error)
        if (error is ConnectException) {
          editorServerService.unregisterSession(activeSession.identifier)
          notifications.sendWarning(
            MessageBundle.message("notification.blender.runtime.command.session.unreachable"),
          )
          return@onFailure
        }
        notifications.sendError(
          MessageBundle.message(
            "notification.blender.runtime.command.failed.exception",
            payload["type"]?.toString() ?: "",
            error.message ?: "",
          ),
        )
      }
    }
  }

  private fun selectedPythonFile(): VirtualFile? {
    val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null
    return selectedFile.takeIf { it.extension.equals("py", ignoreCase = true) }
  }

  private fun resolveSourcePath(): Path? {
    val sourceFolder = projectConfig.getSourceFolder().trim()
    if (sourceFolder.isBlank()) {
      return null
    }

    val configuredPath = Path.of(sourceFolder)
    return if (configuredPath.isAbsolute) {
      Path.of(sourceFolder).normalize()
    } else {
      val basePath = project.basePath ?: return null
      Path.of(basePath).resolve(sourceFolder).normalize()
    }
  }

  private fun resolveConfiguredAddonTargets(): List<AddonTarget> {
    val targets = linkedMapOf<Path, AddonTarget>()

    resolveSourcePath()?.let { sourcePath ->
      val moduleName = resolveSourceModuleName(sourcePath)
      if (moduleName.isNotBlank()) {
        targets[sourcePath.normalize()] = AddonTarget(sourcePath.normalize(), moduleName)
      }
    }

    projectConfig.getScriptDirectories().orEmpty()
      .asSequence()
      .mapNotNull { resolveConfiguredPath(it) }
      .forEach { scriptPath ->
        val normalizedPath = scriptPath.normalize()
        if (!targets.containsKey(normalizedPath)) {
          val moduleName = resolveModuleNameForPath(normalizedPath, preferredName = null)
          if (moduleName.isNotBlank()) {
            targets[normalizedPath] = AddonTarget(normalizedPath, moduleName)
          }
        }
      }

    return targets.values.filter { it.directory.toFile().isDirectory }
  }

  private fun resolveConfiguredPath(rawPath: String): Path? {
    val trimmed = rawPath.trim()
    if (trimmed.isBlank()) {
      return null
    }
    val path = Path.of(trimmed)
    if (path.isAbsolute) {
      return path
    }
    val basePath = project.basePath ?: return null
    return Path.of(basePath).resolve(path).normalize()
  }

  private fun resolveSourceModuleName(sourcePath: Path): String {
    val configuredName = projectConfig.getAddonSymlinkName().trim()
    return resolveModuleNameForPath(sourcePath, preferredName = configuredName)
  }

  private fun resolveModuleNameForPath(path: Path, preferredName: String?): String {
    resolveExtensionManifestId(path)?.let { return it }
    if (!preferredName.isNullOrBlank()) {
      return preferredName
    }
    return path.fileName?.toString().orEmpty().ifBlank { "addon" }
  }

  private fun resolveExtensionManifestId(path: Path): String? {
    val manifestPath = path.resolve("blender_manifest.toml")
    if (!Files.isRegularFile(manifestPath)) {
      return null
    }
    return runCatching { BlenderManifest(manifestPath.toString()).id.trim() }
      .getOrNull()
      ?.ifBlank { null }
  }

  companion object {
    fun getInstance(project: Project): BlenderRuntimeCommandService = project.service()
  }
}
