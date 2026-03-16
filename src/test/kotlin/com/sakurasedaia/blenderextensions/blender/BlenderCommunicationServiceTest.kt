package com.sakurasedaia.blenderextensions.blender

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

class BlenderCommunicationServiceTest : BasePlatformTestCase() {

    fun testServerLifecycle() {
        val service = BlenderCommunicationService.getInstance(project)
        val port = service.startServer()
        assertTrue(port > 0)
        
        // Connect to the server
        val clientSocket = Socket("127.0.0.1", port)
        assertTrue(clientSocket.isConnected)

        // Handshake
        val writer = PrintWriter(clientSocket.getOutputStream(), true)
        writer.println("{\"type\": \"ready\"}")
        
        // Wait a bit for the server thread to accept
        Thread.sleep(500)
        assertTrue(service.isConnected())
        
        service.stopServer()
        clientSocket.close()
    }

    fun testSendReloadCommand() {
        val service = BlenderCommunicationService.getInstance(project)
        val port = service.startServer()
        
        val clientSocket = Socket("127.0.0.1", port)
        val writer = PrintWriter(clientSocket.getOutputStream(), true)
        writer.println("{\"type\": \"ready\"}")

        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
        
        // Wait for server to register connection
        Thread.sleep(500)
        
        service.sendReloadCommand("my_ext")
        
        val received = reader.readLine()
        assertEquals("{\"type\": \"reload\", \"name\": \"my_ext\"}", received)
        
        service.stopServer()
        clientSocket.close()
    }
}
