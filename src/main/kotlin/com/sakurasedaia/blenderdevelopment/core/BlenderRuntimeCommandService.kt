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
import com.sakurasedaia.blenderdevelopment.lib.BlenderManifest
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal enum class BlenderRuntimeMessageType(val wireValue: String) {
  STOP("stop"),
  SCRIPT("script"),
  RELOAD("reload"),
  SETUP("setup"),
  DEPENDENCY_FAILURE("dependencyFailure"),
  BOOTSTRAP_FAILURE("bootstrapFailure");

  val wireValueByteSize: Int
    get() = wireValue.toByteArray(StandardCharsets.UTF_8).size

  companion object {
    private val byWireValue = entries.associateBy(BlenderRuntimeMessageType::wireValue)

    fun fromWireValue(value: String): BlenderRuntimeMessageType? = byWireValue[value]
  }
}

/**
 * Service responsible for managing and dispatching runtime commands to the Blender application.
 *
 * This service provides functionality to interact with the Blender runtime environment, allowing commands to reload addons, execute Python
 * scripts, or stop the runtime session. It ensures proper notification handling, session validation, and error reporting during the
 * execution of commands.
 *
 * The commands dispatched by this service are typically targeted at a locally hosted Blender runtime, as configured by the project
 * settings.
 *
 * @param project The IntelliJ project instance associated with the service.
 * @constructor Initializes the service with the associated project instance.
 */
@Service(Service.Level.PROJECT)
internal class BlenderRuntimeCommandService(private val project: Project) {
  private data class AddonTarget(val directory: Path, val moduleName: String)

  private val logger = PluginLogger.getInstance(project)
  private val notifications = NotificationModal.getInstance(project)
  private val projectConfig = ProjectConfig.getInstance(project)
  private val editorServerService = BlenderEditorServerService.getInstance(project)
  private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
  private val objectMapper = ObjectMapper()

  /**
   * Sends a reload command to the configured addon targets for the Blender runtime. If no addon targets are found, a warning is logged and
   * a user notification is displayed.
   *
   * @param showSuccessNotification indicates whether to show a notification upon successful command execution. Defaults to `true`.
   */
  fun sendReloadCommand(showSuccessNotification: Boolean = true) {
    val addonTargets = resolveConfiguredAddonTargets()
    if (addonTargets.isEmpty()) {
      logger.warn(ErrorTypes.RUNTIME_RELOAD_MISSING_ADDON_DIRECTORIES.toString())
      notifications.sendWarning(MessageBundle.message("notification.blender.runtime.command.reload.source.missing"))
      return
    }
    val payload =
        mapOf(
            "names" to addonTargets.map { it.moduleName },
            "dirs" to addonTargets.map { it.directory.toString() },
        )
    sendCommand(
        type = BlenderRuntimeMessageType.RELOAD,
        payload = payload,
        onSuccessMessage = MessageBundle.message("notification.blender.runtime.command.reload.sent"),
        showSuccessNotification = showSuccessNotification,
    )
  }

  /**
   * Sends a command to execute a Python script in the Blender runtime.
   *
   * This method attempts to retrieve the currently selected Python script file. If no suitable file is selected, a warning notification is
   * displayed to the user indicating the missing script file.
   *
   * Upon locating a valid Python script, a command payload is constructed, containing the script's type and path. This payload is then
   * dispatched to the Blender runtime for execution. A success notification is displayed if the command is sent successfully.
   *
   * Notifications for errors or warnings are also managed internally:
   * - A warning is displayed when no Python script file is selected.
   * - Any issues during the command's sending process, such as session or connection problems, are logged and notified appropriately.
   */
  fun sendRunScriptCommand() {
    val scriptFile =
        selectedPythonFile()
            ?: run {
              notifications.sendWarning(MessageBundle.message("notification.blender.runtime.command.script.file.missing"))
              return
            }
    val payload = mapOf("path" to scriptFile.path)
    sendCommand(
        type = BlenderRuntimeMessageType.SCRIPT,
        payload = payload,
        onSuccessMessage = MessageBundle.message("notification.blender.runtime.command.script.sent", scriptFile.name),
    )
  }

  /**
   * Sends a "stop" command to the configured Blender runtime session.
   *
   * This method constructs a payload with the command type set to "stop" and dispatches it to the Blender runtime. The command notifies the
   * user upon successful delivery using a localized success message.
   *
   * If there are issues with the session, such as an inactive session or an invalid endpoint, the appropriate warning or error
   * notifications are sent to the user. The command execution process is logged for debugging purposes, and any failures during the sending
   * process are handled gracefully, including session termination if the Blender runtime becomes unreachable.
   */
  fun sendStopCommand() {
    sendCommand(
        type = BlenderRuntimeMessageType.STOP,
        onSuccessMessage = MessageBundle.message("notification.blender.runtime.command.stop.sent"),
    )
  }

  /**
   * Checks if there is an active Blender runtime session.
   *
   * This method verifies the existence of an active session by inspecting the latest session payload retrieved from the editor server
   * service.
   *
   * @return `true` if an active session payload exists, `false` otherwise.
   */
  fun hasActiveSession(): Boolean = editorServerService.findLatestActiveSessionPayload() != null

  private fun sendCommand(
      type: BlenderRuntimeMessageType,
      payload: Map<String, Any> = emptyMap(),
      onSuccessMessage: String,
      showSuccessNotification: Boolean = true,
  ) {
    val activeSession = editorServerService.findLatestActiveSessionPayload()
    if (activeSession == null) {
      logger.warn(ErrorTypes.RUNTIME_COMMAND_MISSING_SESSION.format(type.wireValue))
      notifications.sendWarning(MessageBundle.message("notification.blender.runtime.command.session.missing"))
      return
    }
    if (activeSession.blenderPort <= 0) {
      logger.warn(
          ErrorTypes.RUNTIME_COMMAND_INVALID_PORT.format(
              type.wireValue,
              activeSession.identifier,
              activeSession.blenderPort,
          )
      )
      notifications.sendError(
          MessageBundle.message(
              "notification.blender.runtime.command.port.invalid",
              activeSession.blenderPort.toString(),
          )
      )
      return
    }

    val endpoint = "http://127.0.0.1:${activeSession.blenderPort}/"
    val authKey = editorServerService.findSessionAuthKey(activeSession.identifier)

    if (authKey == null) {
      logger.error(ErrorTypes.RETRIEVE_AUTH_FAILURE)
      notifications.sendError(MessageBundle.message("notification.blender.runtime.command.session.inauthentic"))
      return
    }

    val processedRequest = payload + ("type" to type.wireValue)

    AppExecutorUtil.getAppExecutorService().submit {
      runCatching {
        val requestBody = objectMapper.writeValueAsString(processedRequest)
        val signature = BlenderAuthentication.notarizeMessage(authKey, requestBody)

        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("X-Blender-PyCharm-Signature", signature)
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
          .also {
            authKey.fill(0)
          }
          .onSuccess { response ->
            if (response.statusCode() in 200..299) {
              editorServerService.markSessionActivity(activeSession.identifier)
              if (showSuccessNotification) {
                notifications.sendInfo(onSuccessMessage)
              }
              logger.debug("Sent Blender runtime command `${type.wireValue}` to session `${activeSession.identifier}`.")
            } else {
              logger.warn(
                  ErrorTypes.RUNTIME_COMMAND_REJECTED.format(
                      type.wireValue,
                      activeSession.identifier,
                      response.statusCode(),
                      response.body(),
                  )
              )
              notifications.sendError(
                  MessageBundle.message(
                      "notification.blender.runtime.command.failed",
                      type.wireValue,
                      response.statusCode().toString(),
                  )
              )
            }
          }
          .onFailure { error ->
            logger.warn(ErrorTypes.RUNTIME_COMMAND_SEND_FAILED.format(type.wireValue), error)
            if (error is ConnectException) {
              editorServerService.unregisterSession(activeSession.identifier)
              notifications.sendWarning(MessageBundle.message("notification.blender.runtime.command.session.unreachable"))
              return@onFailure
            }
            notifications.sendError(
                MessageBundle.message(
                    "notification.blender.runtime.command.failed.exception",
                    type.wireValue,
                    error.message ?: "",
                )
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

    projectConfig
        .getScriptDirectories()
        .orEmpty()
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
    resolveExtensionManifestId(path)?.let {
      return it
    }
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
    return runCatching { BlenderManifest(manifestPath.toString()).id.trim() }.getOrNull()?.ifBlank { null }
  }

  companion object {
    fun getInstance(project: Project): BlenderRuntimeCommandService = project.service()
  }
}
