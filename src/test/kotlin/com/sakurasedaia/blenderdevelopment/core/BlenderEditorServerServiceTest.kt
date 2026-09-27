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
@file:Suppress("SameParameterValue")

package com.sakurasedaia.blenderdevelopment.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal class BlenderEditorServerServiceTest : BasePlatformTestCase() {
    private val objectMapper = ObjectMapper()
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

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

    fun testExpiredSessionCredentialsAreRejected() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val body = setupBody(session.identifier)
        val signature = signatureFor(session, body)
        expirePendingSession(service, session.identifier)

        val response = sendSetup(session, body, signature)

        assertEquals(401, response.statusCode())
        assertNull(service.findSessionAuthKey(session.identifier))
        assertNull(service.removeSetupPayload(session.identifier))
    }

    fun testJsonContentTypeWithCharsetIsAccepted() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val body = setupBody(session.identifier)

        val response =
            sendSetup(
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

        val response =
            sendSetup(
                session,
                body,
                signatureFor(session, body),
                contentType = "text/plain",
            )

        assertEquals(415, response.statusCode())
        assertNotNull(service.findSessionAuthKey(session.identifier))
        assertNull(service.removeSetupPayload(session.identifier))
    }

    fun testMissingContentTypeIsRejectedWithoutMutatingSession() {
        assertRejectedSetup(415) { session, body ->
            sendSetup(session, body, signatureFor(session, body), contentType = null)
        }
    }

    fun testNonPostMethodIsRejectedBeforeReadingPayload() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val request =
            HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:${session.editorPort}/")).timeout(Duration.ofSeconds(2)).GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(405, response.statusCode())
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

    fun testExactBoundarySizePayloadIsAccepted() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val body = setupBodyAtSize(session.identifier, 64 * 1024)

        val response = sendSetup(session, body, signatureFor(session, body))

        assertEquals(64 * 1024, body.toByteArray().size)
        assertEquals(200, response.statusCode())
        assertNotNull(service.removeSetupPayload(session.identifier))
    }

    fun testOversizedChunkedPayloadIsRejectedWithoutMutatingSession() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val body = "x".repeat(65 * 1024)
        val publisher =
            HttpRequest.BodyPublishers.ofInputStream {
                ByteArrayInputStream(body.toByteArray())
            }

        val response = sendSetup(session, body, signatureFor(session, body), bodyPublisher = publisher)

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

    fun testAbsentTypeIsRejectedWithoutMutatingSession() {
        assertRejectedPayload(mapOf("identifier" to "SESSION"))
    }

    fun testBlankTypeIsRejectedWithoutMutatingSession() {
        assertRejectedPayload(mapOf("type" to " ", "identifier" to "SESSION"))
    }

    fun testUnknownTypeIsRejectedWithoutMutatingSession() {
        assertRejectedPayload(mapOf("type" to "futureMessage", "identifier" to "SESSION"))
    }

    fun testPortsOutsideValidRangeAreRejectedWithoutMutatingSession() {
        listOf(0, 65_536).forEach { invalidPort ->
            assertRejectedSetup { session -> setupBody(session.identifier, blenderPort = invalidPort) }
            assertRejectedSetup { session -> setupBody(session.identifier, debugpyPort = invalidPort) }
        }
    }

    fun testAbsentRequiredSetupFieldsAreRejectedWithoutMutatingSession() {
        listOf("blenderPort", "debugpyPort", "scriptsFolder", "pathMappings", "debugProtocol").forEach { field ->
            assertRejectedSetup { session ->
                objectMapper.writeValueAsString(setupPayload(session.identifier).toMutableMap().apply { remove(field) })
            }
        }
    }

    fun testBlankScriptsFolderIsRejectedWithoutMutatingSession() {
        assertRejectedSetup { session -> setupBody(session.identifier, scriptsFolder = " ") }
    }

    fun testMalformedPathMappingsAreRejectedWithoutMutatingSession() {
        val invalidMappings =
            listOf(
                "not-a-list",
                listOf(mapOf("src" to "", "load" to "/runtime")),
                listOf(mapOf("src" to "/project")),
            )
        invalidMappings.forEach { mappings ->
            assertRejectedSetup { session -> setupBody(session.identifier, pathMappings = mappings) }
        }
    }

    fun testUnsupportedDebugProtocolIsRejectedWithoutMutatingSession() {
        assertRejectedSetup { session -> setupBody(session.identifier, debugProtocol = "gdb") }
    }

    fun testUnknownSetupFieldsRemainForwardCompatible() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val payload = setupPayload(session.identifier).toMutableMap().apply { put("futureField", true) }
        val body = objectMapper.writeValueAsString(payload)

        val response = sendSetup(session, body, signatureFor(session, body))

        assertEquals(200, response.statusCode())
        assertNotNull(service.removeSetupPayload(session.identifier))
    }

    fun testCoercibleSetupFieldIsRejectedWithoutMutatingSession() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val body =
            objectMapper.writeValueAsString(
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

    fun testBothSignedFailureReportsClearPendingSessionAndNotifyUser() {
        listOf("bootstrapFailure", "dependencyFailure").forEach { failureType ->
            val service = BlenderEditorServerService.getInstance(project)
            val session = service.prepareLaunchSession()
            val body = failureBody(session.identifier, failureType, "Runtime failed: 日本語")
            val notifications = mutableListOf<Notification>()
            project.messageBus
                .connect(testRootDisposable)
                .subscribe(
                    Notifications.TOPIC,
                    object : Notifications {
                        override fun notify(notification: Notification) {
                            notifications.add(notification)
                        }
                    },
                )

            val response = sendSetup(session, body, signatureFor(session, body))

            assertEquals(200, response.statusCode())
            assertNull(service.findSessionAuthKey(session.identifier))
            assertNull(service.removeSetupPayload(session.identifier))
            assertTrue(notifications.any { it.content == "Runtime failed: 日本語" })
        }
    }

    fun testInvalidFailureSchemaCannotClearPendingSession() {
        listOf("", " ").forEach { message ->
            val service = BlenderEditorServerService.getInstance(project)
            val session = service.prepareLaunchSession()
            val body = failureBody(session.identifier, message = message)

            val response = sendSetup(session, body, signatureFor(session, body))

            assertEquals(400, response.statusCode())
            assertNotNull(service.findSessionAuthKey(session.identifier))
            assertNull(service.removeSetupPayload(session.identifier))
        }
    }

    fun testUnsignedFailureReportCannotClearPendingSession() {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()

        val response = sendSetup(session, failureBody(session.identifier), signature = null)

        assertEquals(401, response.statusCode())
        assertNotNull(service.findSessionAuthKey(session.identifier))
        assertNull(service.removeSetupPayload(session.identifier))
    }

    private fun setupBody(
        identifier: String,
        blenderPort: Int = 51_234,
        debugpyPort: Int = 56_789,
        scriptsFolder: String = "/tmp/blender/scripts",
        pathMappings: Any = listOf(mapOf("src" to "/project", "load" to "/runtime")),
        debugProtocol: String = "debugpy-dap",
    ): String =
        objectMapper.writeValueAsString(setupPayload(identifier, blenderPort, debugpyPort, scriptsFolder, pathMappings, debugProtocol))

    private fun setupPayload(
        identifier: String,
        blenderPort: Int = 51_234,
        debugpyPort: Int = 56_789,
        scriptsFolder: String = "/tmp/blender/scripts",
        pathMappings: Any = listOf(mapOf("src" to "/project", "load" to "/runtime")),
        debugProtocol: String = "debugpy-dap",
    ): Map<String, Any> =
        mapOf(
            "type" to "setup",
            "identifier" to identifier,
            "blenderPort" to blenderPort,
            "debugpyPort" to debugpyPort,
            "scriptsFolder" to scriptsFolder,
            "pathMappings" to pathMappings,
            "debugProtocol" to debugProtocol,
        )

    private fun failureBody(
        identifier: String,
        type: String = "bootstrapFailure",
        message: String = "Runtime bootstrap failed",
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "type" to type,
                "identifier" to identifier,
                "message" to message,
                "details" to "Test failure details",
            )
        )

    private fun setupBodyAtSize(identifier: String, targetSize: Int): String {
        val payload = setupPayload(identifier).toMutableMap()
        payload["padding"] = ""
        val emptyPaddingBody = objectMapper.writeValueAsString(payload)
        payload["padding"] = "x".repeat(targetSize - emptyPaddingBody.toByteArray().size)
        return objectMapper.writeValueAsString(payload)
    }

    private fun assertRejectedSetup(bodyFactory: (BlenderRuntimeLaunchSession) -> String) {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val body = bodyFactory(session)
        val response = sendSetup(session, body, signatureFor(session, body))

        assertEquals(400, response.statusCode())
        assertNotNull(service.findSessionAuthKey(session.identifier))
        assertNull(service.removeSetupPayload(session.identifier))
    }

    private fun assertRejectedSetup(
        expectedStatus: Int,
        request: (BlenderRuntimeLaunchSession, String) -> HttpResponse<String>,
    ) {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val body = setupBody(session.identifier)
        val response = request(session, body)

        assertEquals(expectedStatus, response.statusCode())
        assertNotNull(service.findSessionAuthKey(session.identifier))
        assertNull(service.removeSetupPayload(session.identifier))
    }

    private fun assertRejectedPayload(payloadTemplate: Map<String, String>) {
        val service = BlenderEditorServerService.getInstance(project)
        val session = service.prepareLaunchSession()
        val payload = payloadTemplate.mapValues { (_, value) ->
            if (value == "SESSION") session.identifier else value
        }
        val body = objectMapper.writeValueAsString(payload)
        val response = sendSetup(session, body, signatureFor(session, body))

        assertEquals(400, response.statusCode())
        assertNotNull(service.findSessionAuthKey(session.identifier))
        assertNull(service.removeSetupPayload(session.identifier))
    }

    @Suppress("UNCHECKED_CAST")
    private fun expirePendingSession(service: BlenderEditorServerService, identifier: String) {
        val field = BlenderEditorServerService::class.java.getDeclaredField("pendingSessionCreatedAtMs")
        field.isAccessible = true
        val createdAtByIdentifier = field.get(service) as MutableMap<String, Long>
        createdAtByIdentifier[identifier] = 0L
    }

    private fun signatureFor(session: BlenderRuntimeLaunchSession, body: String): String =
        BlenderAuthentication.notarizeMessage(BlenderAuthentication.decode(session.encodedAuthKey), body)

    private fun sendSetup(
        session: BlenderRuntimeLaunchSession,
        body: String,
        signature: String?,
        contentType: String? = "application/json",
        bodyPublisher: HttpRequest.BodyPublisher = HttpRequest.BodyPublishers.ofString(body),
    ): HttpResponse<String> {
        val requestBuilder =
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:${session.editorPort}/"))
                .timeout(Duration.ofSeconds(2))
                .POST(bodyPublisher)
        if (contentType != null) {
            requestBuilder.header("Content-Type", contentType)
        }
        if (signature != null) {
            requestBuilder.header("X-Blender-PyCharm-Signature", signature)
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }
}
