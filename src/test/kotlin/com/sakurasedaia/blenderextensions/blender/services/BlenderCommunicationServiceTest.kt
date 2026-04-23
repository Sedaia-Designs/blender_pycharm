package com.sakurasedaia.blenderextensions.blender.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.GlobalScope
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class BlenderCommunicationServiceTest : BasePlatformTestCase() {

    @Test
    fun testSendReloadCommandEscaping() {
        val service = BlenderCommunicationService(project, GlobalScope)
        val port = service.startServer()

        val clientSocket = Socket("localhost", port)
        val out = PrintWriter(clientSocket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

        // Handshake
        out.println("{\"type\": \"ready\"}")

        // Wait for handshake to be processed
        var timeout = 50
        while (!service.isConnected() && timeout > 0) {
            Thread.sleep(100)
            timeout--
        }

        assertTrue("Service should be connected", service.isConnected())

        val extensionName = "My \"Special\" Extension"
        service.sendReloadCommand(extensionName)

        val receivedJson = reader.readLine()
        assertNotNull("Should have received a message", receivedJson)
        
        // Use a simple check or parse it back
        assertTrue("JSON should contain escaped quotes", receivedJson.contains("My \\\"Special\\\" Extension"))
        assertTrue("JSON should be valid", receivedJson.startsWith("{") && receivedJson.endsWith("}"))

        clientSocket.close()
        service.stopServer()
    }
}
