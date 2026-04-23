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

    @Test
    fun testClientReplacementClosesOldSocket() {
        val service = BlenderCommunicationService(project, GlobalScope)
        val port = service.startServer()

        val client1 = Socket("localhost", port)
        PrintWriter(client1.getOutputStream(), true).println("{\"type\": \"ready\"}")

        // Wait for first connection
        var timeout = 50
        while (!service.isConnected() && timeout > 0) {
            Thread.sleep(100)
            timeout--
        }
        assertTrue("First client should be connected", service.isConnected())

        val client2 = Socket("localhost", port)
        PrintWriter(client2.getOutputStream(), true).println("{\"type\": \"ready\"}")

        // Wait for replacement
        // Since we don't have a direct way to see if it's the NEW client, 
        // we check if the OLD one was closed by the service.
        timeout = 50
        while (!client1.isClosed && timeout > 0) {
            // We need to trigger some IO or wait for the service to close it
            // Actually client1.isClosed only tells us if WE closed it locally.
            // To see if the OTHER end closed it, we might need to try reading/writing.
            try {
                if (client1.getInputStream().read() == -1) break
            } catch (e: Exception) {
                break
            }
            Thread.sleep(100)
            timeout--
        }

        // Try reading from client1, it should reach EOF if closed by server
        val readResult = client1.getInputStream().read()
        assertEquals("Old client should have been closed by server (EOF)", -1, readResult)

        assertTrue("Service should still be connected (to client2)", service.isConnected())

        client1.close()
        client2.close()
        service.stopServer()
    }
}
