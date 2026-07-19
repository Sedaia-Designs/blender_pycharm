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
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class BlenderRuntimeLaunchSession(
  val identifier: String,
  val editorPort: Int,
)

internal data class BlenderPathMapping(
  val src: String,
  val load: String,
)

internal enum class BlenderDebugProtocol {
  DEBUGPY_DAP,
  PYDEVD,
}

internal data class BlenderSetupPayload(
  val identifier: String,
  val blenderPort: Int,
  val debugpyPort: Int,
  val scriptsFolder: String,
  val pathMappings: List<BlenderPathMapping>,
  val debugProtocol: BlenderDebugProtocol,
)

@Service(Service.Level.PROJECT)
internal class BlenderEditorServerService(private val project: Project) : Disposable {
  private val logger = PluginLogger.getInstance(project)
  private val objectMapper = ObjectMapper()
  private val pendingSessionIdentifiers = ConcurrentHashMap.newKeySet<String>()
  private val setupPayloadsByIdentifier = ConcurrentHashMap<String, BlenderSetupPayload>()
  private val activeSessionPayloads = ConcurrentHashMap<String, BlenderSetupPayload>()
  private val serverLock = Any()

  @Volatile
  private var server: HttpServer? = null

  @Volatile
  private var serverPort: Int = -1

  @Volatile
  private var latestActiveSessionIdentifier: String? = null

  fun prepareLaunchSession(): BlenderRuntimeLaunchSession {
    val port = ensureServerStarted()
    val identifier = UUID.randomUUID().toString()
    pendingSessionIdentifiers.add(identifier)
    return BlenderRuntimeLaunchSession(identifier = identifier, editorPort = port)
  }

  fun unregisterSession(identifier: String) {
    pendingSessionIdentifiers.remove(identifier)
    setupPayloadsByIdentifier.remove(identifier)
    activeSessionPayloads.remove(identifier)
    if (latestActiveSessionIdentifier == identifier) {
      latestActiveSessionIdentifier = activeSessionPayloads.keys.firstOrNull()
    }
  }

  fun findSetupPayload(identifier: String): BlenderSetupPayload? = setupPayloadsByIdentifier[identifier]

  fun removeSetupPayload(identifier: String): BlenderSetupPayload? = setupPayloadsByIdentifier.remove(identifier)

  fun findActiveSessionPayload(identifier: String): BlenderSetupPayload? = activeSessionPayloads[identifier]

  fun findLatestActiveSessionPayload(): BlenderSetupPayload? {
    val identifier = latestActiveSessionIdentifier ?: return null
    return activeSessionPayloads[identifier]
  }

  fun getActiveSessionPayloads(): List<BlenderSetupPayload> = activeSessionPayloads.values.toList()

  override fun dispose() {
    synchronized(serverLock) {
      server?.stop(0)
      server = null
      serverPort = -1
      pendingSessionIdentifiers.clear()
      setupPayloadsByIdentifier.clear()
      activeSessionPayloads.clear()
      latestActiveSessionIdentifier = null
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

      if (type == "setup") {
        registerSetupPayload(payloadNode)
      }

      exchange.sendResponseHeaders(200, 0)
      exchange.responseBody.use { it.write("OK".toByteArray()) }
    }.onFailure { error ->
      logger.warn("Failed to handle Blender runtime payload.", error)
      runCatching {
        exchange.sendResponseHeaders(400, -1)
      }
    }.also {
      exchange.close()
    }
  }

  private fun registerSetupPayload(payloadNode: JsonNode) {
    val identifier = payloadNode.readFirstTextValue("identifier", "vscodeIdentifier")
    if (identifier.isBlank()) {
      logger.warn("Blender setup payload is missing identifier: $payloadNode")
      return
    }

    if (!pendingSessionIdentifiers.contains(identifier)) {
      logger.warn("Blender setup payload received for unknown session identifier `$identifier`.")
    }

    val setupPayload = BlenderSetupPayload(
      identifier = identifier,
      blenderPort = payloadNode.path("blenderPort").asInt(-1),
      debugpyPort = payloadNode.path("debugpyPort").asInt(-1),
      scriptsFolder = payloadNode.path("scriptsFolder").asText(""),
      pathMappings = parsePathMappings(payloadNode.path("pathMappings").takeIf { !it.isMissingNode }
        ?: payloadNode.path("addonPathMappings")),
      debugProtocol = parseDebugProtocol(payloadNode.path("debugProtocol").asText("")),
    )
    setupPayloadsByIdentifier[identifier] = setupPayload
    activeSessionPayloads[identifier] = setupPayload
    latestActiveSessionIdentifier = identifier
    pendingSessionIdentifiers.remove(identifier)
    logger.debug("Registered Blender setup payload for session `$identifier`: $setupPayload")
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
    fun getInstance(project: Project): BlenderEditorServerService = project.service()
  }
}
