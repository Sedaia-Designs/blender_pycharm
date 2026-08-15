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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class BlenderRuntimeLaunchSession(
  val identifier: String,
  val editorPort: Int,
  val encodedAuthKey: String,
)

internal data class BlenderPathMapping(
  val src: String,
  val load: String,
)

internal enum class BlenderDebugProtocol {
  DEBUGPY_DAP,
  PYDEVD,
}

private const val SIGNATURE_HEADER = "X-Blender-PyCharm-Signature"

internal data class BlenderSetupPayload(
  val identifier: String,
  val blenderPort: Int,
  val debugpyPort: Int,
  val scriptsFolder: String,
  val pathMappings: List<BlenderPathMapping>,
  val debugProtocol: BlenderDebugProtocol,
)

@Service(Service.Level.PROJECT)
internal class BlenderEditorServerService(project: Project) : Disposable {
  private val logger = PluginLogger.getInstance(project)
  private val notifications = NotificationModal.getInstance(project)
  private val objectMapper = ObjectMapper()
  private val pendingSessionIdentifiers = ConcurrentHashMap.newKeySet<String>()
  private val pendingSessionCreatedAtMs = ConcurrentHashMap<String, Long>()
  private val setupPayloadsByIdentifier = ConcurrentHashMap<String, BlenderSetupPayload>()
  private val activeSessionPayloads = ConcurrentHashMap<String, BlenderSetupPayload>()
  private val activeSessionUpdatedAtMs = ConcurrentHashMap<String, Long>()
  private val serverLock = Any()
  private val sessionLock = Any()
  private val sessionAuthKeys = ConcurrentHashMap<String, ByteArray>()

  @Volatile
  private var server: HttpServer? = null

  @Volatile
  private var serverPort: Int = -1

  @Volatile
  private var latestActiveSessionIdentifier: String? = null

  /**
   * Prepares and initializes a new launch session for the Blender editor runtime.
   *
   * This method ensures that the server is running, generates a unique session identifier,
   * creates a cryptographic authentication key, and stores necessary session metadata
   * to manage the session lifecycle. The session metadata is synchronized using the session
   * lock to maintain thread safety.
   *
   * @return A new instance of BlenderRuntimeLaunchSession containing the unique session
   * identifier, the port on which the editor server is running, and the Base64 URL-safe encoded
   * authentication key.
   */
  fun prepareLaunchSession(): BlenderRuntimeLaunchSession {
    val port = ensureServerStarted()
    val identifier = UUID.randomUUID().toString()
    val authKey: ByteArray = BlenderAuthentication.create()

    synchronized(sessionLock) {
      sessionAuthKeys[identifier] = authKey
      pendingSessionIdentifiers.add(identifier)
      pendingSessionCreatedAtMs[identifier] = System.currentTimeMillis()
    }

    return BlenderRuntimeLaunchSession(
      identifier = identifier,
      editorPort = port,
      encodedAuthKey=BlenderAuthentication.encode(authKey)
    )
  }

  /**
   * Unregisters a session associated with the given identifier.
   *
   * This method removes all metadata and payloads related to the specified session.
   * If the session was the latest active session, it updates the latest active session
   * identifier to the next available session (if any). Thread safety is ensured using
   * the session lock.
   *
   * @param identifier The unique identifier of the session to be unregistered.
   */
  fun unregisterSession(identifier: String) {
    synchronized(sessionLock) {
      sessionAuthKeys.remove(identifier)?.fill(0)

      pendingSessionIdentifiers.remove(identifier)
      pendingSessionCreatedAtMs.remove(identifier)
      setupPayloadsByIdentifier.remove(identifier)
      activeSessionPayloads.remove(identifier)
      activeSessionUpdatedAtMs.remove(identifier)

      if (latestActiveSessionIdentifier == identifier) {
        latestActiveSessionIdentifier = activeSessionPayloads.keys.firstOrNull()
      }
    }
  }

  /**
   * Retrieves the authentication key associated with the given session identifier.
   * If the session is expired or the identifier is not found, it returns null.
   * Automatically cleans up expired sessions before performing the lookup.
   *
   * @param identifier The unique identifier of the session whose authentication key is to be retrieved.
   * @return A copy of the authentication key as a ByteArray if the session is active, or null if the session is expired or not found.
   */
  fun findSessionAuthKey(identifier: String): ByteArray? {
    cleanupExpiredSessions()
    return sessionAuthKeys[identifier]?.copyOf()
  }

  /**
   * Removes the setup payload associated with the specified identifier.
   *
   * This method cleans up expired sessions before attempting to remove the setup payload
   * from the internal storage. If the identifier exists, its corresponding setup payload
   * is removed and returned.
   *
   * @param identifier The unique identifier of the setup payload to be removed.
   * @return The removed setup payload associated with the identifier, or null if no payload was found.
   */
  fun removeSetupPayload(identifier: String): BlenderSetupPayload? {
    cleanupExpiredSessions()
    return setupPayloadsByIdentifier.remove(identifier)
  }

  /**
   * Retrieves the setup payload of the latest active Blender runtime session.
   *
   * This method first clears any expired sessions to ensure the session state is up-to-date.
   * It then attempts to locate the payload corresponding to the latest active session
   * identifier. If no active session exists or the identifier is invalid, it returns null.
   *
   * On successfully finding the payload, the session's "last updated" timestamp is refreshed
   * to indicate recent activity.
   *
   * @return The setup payload of the latest active session as a [BlenderSetupPayload], or null
   * if no active session exists or the payload cannot be found.
   */
  fun findLatestActiveSessionPayload(): BlenderSetupPayload? {
    cleanupExpiredSessions()
    val identifier = latestActiveSessionIdentifier ?: return null
    val payload = activeSessionPayloads[identifier] ?: return null
    activeSessionUpdatedAtMs[identifier] = System.currentTimeMillis()
    return payload
  }

  /**
   * Updates the "last updated" timestamp for an active session identified by the given session identifier.
   *
   * This method checks if the session corresponding to the identifier exists in the active session payloads.
   * If it does, the session's last activity timestamp is updated to the current system time in milliseconds.
   *
   * @param identifier The unique identifier of the session whose activity timestamp is to be updated.
   */
  fun markSessionActivity(identifier: String) {
    if (activeSessionPayloads.containsKey(identifier)) {
      activeSessionUpdatedAtMs[identifier] = System.currentTimeMillis()
    }
  }

  /**
   * Releases all resources and clears internal data associated with the Blender editor server service.
   *
   * This method ensures a clean shutdown of the server and resets internal state variables to their
   * default values. It performs the following actions:
   * - Stops the server if it is running and sets the server reference to null.
   * - Resets the server port to an invalid state (`-1`).
   * - Clears all session-related data, including pending identifiers, setup payloads, active
   *   session payloads, and associated timestamps.
   * - Nullifies the latest active session identifier.
   * - Clears and securely disposes of all cryptographic authentication keys.
   *
   * Thread safety is maintained through synchronization on appropriate locks (`serverLock` and `sessionLock`).
   */
  override fun dispose() {
    synchronized(serverLock) {
      server?.stop(0)
      server = null
      serverPort = -1
      synchronized(sessionLock) {
        pendingSessionIdentifiers.clear()
        pendingSessionCreatedAtMs.clear()
        setupPayloadsByIdentifier.clear()
        activeSessionPayloads.clear()
        activeSessionUpdatedAtMs.clear()
        latestActiveSessionIdentifier = null
        sessionAuthKeys.values.forEach { it.fill(0) }
        sessionAuthKeys.clear()
      }
    }
  }

  private fun ensureServerStarted(): Int {
    server?.let { return serverPort }
    synchronized(serverLock) {
      server?.let { return serverPort }
      val localServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      localServer.executor = AppExecutorUtil.getAppExecutorService()
      localServer.createContext("/") { exchange -> handlePost(exchange) }
      localServer.start()
      server = localServer
      serverPort = localServer.address.port
      logger.debug("Blender editor server listening on port $serverPort")
      return serverPort
    }
  }

  private fun handlePost(exchange: HttpExchange) {
    runCatching {
      if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
        exchange.sendResponseHeaders(405, -1)
        return
      }

      val payloadText = exchange.requestBody.bufferedReader().use { it.readText() }
      val payloadNode = objectMapper.readTree(payloadText)
      val type = payloadNode.path("type").asText("")

      when (type) {
        "setup" -> registerSetupPayload(
          payloadNode,
          payloadText,
          exchange.requestHeaders.getFirst(SIGNATURE_HEADER).orEmpty(),
        )
        "dependencyFailure",
        "bootstrapFailure" -> handleRuntimeFailurePayload(payloadNode)
      }

      exchange.sendResponseHeaders(200, 0)
      exchange.responseBody.use { it.write("OK".toByteArray()) }
    }.onFailure { error ->
      logger.warn(ErrorTypes.RUNTIME_PAYLOAD_HANDLING_FAILED.toString(), error)
      runCatching {
        exchange.sendResponseHeaders(400, -1)
      }
    }.also {
      exchange.close()
    }
  }

  private fun registerSetupPayload(payloadNode: JsonNode, payloadText: String, hmacSignature: String) {
    val identifier = payloadNode.readFirstTextValue("identifier")
    if (identifier.isBlank()) {
      throw ErrorTypes.SETUP_PAYLOAD_MISSING_IDENTIFIER.createException(payloadNode)
    }

    if (hmacSignature.isBlank()) {
      throw ErrorTypes.MISSING_AUTH_SIGNATURE.createException()
    }

    val setupPayload = synchronized(sessionLock) {
      if (!pendingSessionIdentifiers.contains(identifier)) {
        throw ErrorTypes.SETUP_PAYLOAD_UNKNOWN_SESSION.createException(identifier)
      }

      val currentAuthKey = sessionAuthKeys[identifier]
        ?: throw ErrorTypes.SETUP_PAYLOAD_UNKNOWN_SESSION.createException(identifier)
      if (!BlenderAuthentication.isPostAuthentic(hmacSignature, payloadText, currentAuthKey)) {
        throw ErrorTypes.INVALID_AUTH_RECEIVED.createException()
      }

      BlenderSetupPayload(
        identifier = identifier,
        blenderPort = payloadNode.path("blenderPort").asInt(-1),
        debugpyPort = payloadNode.path("debugpyPort").asInt(-1),
        scriptsFolder = payloadNode.path("scriptsFolder").asText(""),
        pathMappings = parsePathMappings(payloadNode.path("pathMappings").takeIf { !it.isMissingNode }
          ?: payloadNode.path("addonPathMappings")),
        debugProtocol = parseDebugProtocol(payloadNode.path("debugProtocol").asText("")),
      ).also { payload ->
        setupPayloadsByIdentifier[identifier] = payload
        activeSessionPayloads[identifier] = payload
        activeSessionUpdatedAtMs[identifier] = System.currentTimeMillis()
        latestActiveSessionIdentifier = identifier
        pendingSessionIdentifiers.remove(identifier)
        pendingSessionCreatedAtMs.remove(identifier)
      }
    }
    logger.debug("Registered Blender setup payload for session `$identifier`: $setupPayload")
  }

  private fun handleRuntimeFailurePayload(payloadNode: JsonNode) {
    val identifier = payloadNode.readFirstTextValue("identifier")
    if (identifier.isNotBlank()) {
      pendingSessionIdentifiers.remove(identifier)
      pendingSessionCreatedAtMs.remove(identifier)
      sessionAuthKeys.remove(identifier)
    }

    val message = payloadNode.path("message").asText("").ifBlank {
      MessageBundle.message("notification.blender.runtime.bootstrap.failed.generic")
    }
    val details = payloadNode.path("details").asText("")
    if (details.isNotBlank()) {
      logger.warn(ErrorTypes.RUNTIME_BOOTSTRAP_FAILED_WITH_DETAILS.format(message, details))
    } else {
      logger.warn(ErrorTypes.RUNTIME_BOOTSTRAP_FAILED.format(message))
    }
    notifications.sendError(
      message,
      MessageBundle.message("notification.blender.runtime.bootstrap.failed"),
    )
  }

  private fun cleanupExpiredSessions() {
    val now = System.currentTimeMillis()

    pendingSessionCreatedAtMs.entries.toList().forEach { (identifier, createdAtMs) ->
      if (now - createdAtMs > PENDING_SESSION_TTL_MS) {
        logger.debug("Removing stale pending Blender runtime session `$identifier`.")
        unregisterSession(identifier)
      }
    }

    activeSessionUpdatedAtMs.entries.toList().forEach { (identifier, updatedAtMs) ->
      if (now - updatedAtMs > ACTIVE_SESSION_TTL_MS) {
        logger.debug("Removing stale active Blender runtime session `$identifier`.")
        unregisterSession(identifier)
      }
    }
  }

  private fun parsePathMappings(pathMappingsNode: JsonNode): List<BlenderPathMapping> {
    if (!pathMappingsNode.isArray) {
      return emptyList()
    }
    return pathMappingsNode.mapNotNull { mapping ->
      val src = mapping.path("src").asText("")
      val load = mapping.path("load").asText("")
      if (src.isBlank() || load.isBlank()) {
        null
      } else {
        BlenderPathMapping(src = src, load = load)
      }
    }
  }

  private fun parseDebugProtocol(rawProtocol: String): BlenderDebugProtocol {
    return when (rawProtocol.trim().lowercase()) {
      "pydev",
      "pydevd",
      "pydevd-client" -> BlenderDebugProtocol.PYDEVD
      else -> BlenderDebugProtocol.DEBUGPY_DAP
    }
  }

  private fun JsonNode.readFirstTextValue(vararg keys: String): String {
    keys.forEach { key ->
      val value = path(key).asText("")
      if (value.isNotBlank()) {
        return value
      }
    }
    return ""
  }

  companion object {
    private const val PENDING_SESSION_TTL_MS = 60_000L
    private const val ACTIVE_SESSION_TTL_MS = 12 * 60 * 60_000L

    fun getInstance(project: Project): BlenderEditorServerService = project.service()
  }
}
