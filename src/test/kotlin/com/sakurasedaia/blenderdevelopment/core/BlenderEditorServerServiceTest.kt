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
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal class BlenderEditorServerServiceTest : BasePlatformTestCase() {
  private val objectMapper = ObjectMapper()
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(2))
    .build()

  override fun runInDispatchThread(): Boolean = false

  fun testPrepareLaunchSessionCreatesRetrievableAuthenticationKey() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()

    assertTrue(session.editorPort > 0)
    assertTrue(session.identifier.isNotBlank())
    val storedKey = service.findSessionAuthKey(session.identifier)
    assertNotNull(storedKey)
    assertTrue(BlenderAuthentication.decode(session.encodedAuthKey).contentEquals(checkNotNull(storedKey)))
  }

  fun testValidSignedSetupRegistersSession() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody(session.identifier)

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(200, response.statusCode())
    val payload = service.removeSetupPayload(session.identifier)
    assertNotNull(payload)
    assertEquals(session.identifier, payload?.identifier)
    assertEquals(51_234, payload?.blenderPort)
    assertEquals(56_789, payload?.debugpyPort)
    assertEquals(BlenderDebugProtocol.DEBUGPY_DAP, payload?.debugProtocol)
  }

  fun testMissingSignatureIsRejectedWithoutInvalidatingSessionKey() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()

    val response = sendSetup(session, setupBody(session.identifier), signature = null)

    assertEquals(401, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testMissingIdentifierIsRejectedWithoutInvalidatingSessionKey() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = objectMapper.writeValueAsString(mapOf("type" to "setup"))

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(400, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testMalformedSignatureIsRejectedWithoutInvalidatingSessionKey() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()

    val response = sendSetup(session, setupBody(session.identifier), signature = "not-hexadecimal")

    assertEquals(401, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testSignatureForDifferentBodyIsRejected() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody(session.identifier)
    val signature = signatureFor(session, setupBody(session.identifier, blenderPort = 51_235))

    val response = sendSetup(session, body, signature)

    assertEquals(401, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testSignatureFromDifferentSessionIsRejected() {
    val service = BlenderEditorServerService.getInstance(project)
    val targetSession = service.prepareLaunchSession()
    val otherSession = service.prepareLaunchSession()
    val body = setupBody(targetSession.identifier)

    val response = sendSetup(targetSession, body, signatureFor(otherSession, body))

    assertEquals(401, response.statusCode())
    assertNotNull(service.findSessionAuthKey(targetSession.identifier))
    assertNull(service.removeSetupPayload(targetSession.identifier))
  }

  fun testUnknownSessionIsRejected() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody("unknown-session")

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(401, response.statusCode())
    assertNull(service.removeSetupPayload("unknown-session"))
  }

  fun testSuccessfulSetupCannotBeReplayed() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody(session.identifier)
    val signature = signatureFor(session, body)

    val firstResponse = sendSetup(session, body, signature)
    val replayResponse = sendSetup(session, body, signature)

    assertEquals(200, firstResponse.statusCode())
    assertEquals(400, replayResponse.statusCode())
    assertEquals(51_234, service.removeSetupPayload(session.identifier)?.blenderPort)
  }

  fun testUnregisterSessionRemovesPendingAuthenticationState() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody(session.identifier)
    service.unregisterSession(session.identifier)

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(401, response.statusCode())
    assertNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testJsonContentTypeWithCharsetIsAccepted() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody(session.identifier)

    val response = sendSetup(
      session,
      body,
      signatureFor(session, body),
      contentType = "application/json; charset=utf-8",
    )

    assertEquals(200, response.statusCode())
    assertNotNull(service.removeSetupPayload(session.identifier))
  }

  fun testUnsupportedContentTypeIsRejected() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody(session.identifier)

    val response = sendSetup(
      session,
      body,
      signatureFor(session, body),
      contentType = "text/plain",
    )

    assertEquals(415, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testOversizedPayloadIsRejectedBeforeAuthentication() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = "x".repeat(65 * 1024)

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(413, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testMalformedJsonIsRejected() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = "{not-json}"

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(400, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testTrailingJsonValueIsRejected() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = "${setupBody(session.identifier)} {}"

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(400, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testNonObjectJsonIsRejected() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = "[]"

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(400, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testInvalidSetupSchemaIsRejectedWithoutMutatingSession() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = setupBody(session.identifier, blenderPort = 0)

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(400, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testCoercibleSetupFieldIsRejectedWithoutMutatingSession() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = objectMapper.writeValueAsString(
      mapOf(
        "type" to "setup",
        "identifier" to session.identifier,
        "blenderPort" to "51234",
        "debugpyPort" to 56_789,
        "scriptsFolder" to "/tmp/blender/scripts",
        "pathMappings" to emptyList<Any>(),
        "debugProtocol" to "debugpy-dap",
      )
    )

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(400, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testValidSignedFailureReportClearsPendingSession() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()
    val body = failureBody(session.identifier)

    val response = sendSetup(session, body, signatureFor(session, body))

    assertEquals(200, response.statusCode())
    assertNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  fun testUnsignedFailureReportCannotClearPendingSession() {
    val service = BlenderEditorServerService.getInstance(project)
    val session = service.prepareLaunchSession()

    val response = sendSetup(session, failureBody(session.identifier), signature = null)

    assertEquals(401, response.statusCode())
    assertNotNull(service.findSessionAuthKey(session.identifier))
    assertNull(service.removeSetupPayload(session.identifier))
  }

  private fun setupBody(identifier: String, blenderPort: Int = 51_234): String =
    objectMapper.writeValueAsString(
      mapOf(
        "type" to "setup",
        "identifier" to identifier,
        "blenderPort" to blenderPort,
        "debugpyPort" to 56_789,
        "scriptsFolder" to "/tmp/blender/scripts",
        "pathMappings" to listOf(mapOf("src" to "/project", "load" to "/runtime")),
        "debugProtocol" to "debugpy-dap",
      )
    )

  private fun failureBody(identifier: String): String =
    objectMapper.writeValueAsString(
      mapOf(
        "type" to "bootstrapFailure",
        "identifier" to identifier,
        "message" to "Runtime bootstrap failed",
        "details" to "Test failure details",
      )
    )

  private fun signatureFor(session: BlenderRuntimeLaunchSession, body: String): String =
    BlenderAuthentication.notarizeMessage(BlenderAuthentication.decode(session.encodedAuthKey), body)

  private fun sendSetup(
    session: BlenderRuntimeLaunchSession,
    body: String,
    signature: String?,
    contentType: String? = "application/json",
  ): HttpResponse<String> {
    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create("http://127.0.0.1:${session.editorPort}/"))
      .timeout(Duration.ofSeconds(2))
      .POST(HttpRequest.BodyPublishers.ofString(body))
    if (contentType != null) {
      requestBuilder.header("Content-Type", contentType)
    }
    if (signature != null) {
      requestBuilder.header("X-Blender-PyCharm-Signature", signature)
    }
    return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
  }
}
