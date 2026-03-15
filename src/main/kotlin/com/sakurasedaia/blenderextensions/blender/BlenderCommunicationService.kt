package com.sakurasedaia.blenderextensions.blender

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import com.sakurasedaia.blenderextensions.LangManager
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class BlenderCommunicationService(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)
    private var serverSocket: ServerSocket? = null
    private var blenderClient: Socket? = null
    private val isRunning = AtomicBoolean(false)

    fun startServer(): Int {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort
        project.putUserData(BLENDER_PORT_KEY, port)

        Thread {
            try {
                while (!server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        null
                    }
                    if (client != null) {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val firstLine = reader.readLine()
                            if (firstLine != null && firstLine.contains("\"type\": \"ready\"")) {
                                logger.log(LangManager.message("log.blender.connected", port))
                                blenderClient = client
                            } else {
                                logger.log(LangManager.message("log.blender.handshake.failed"))
                                client.close()
                            }
                        } catch (e: Exception) {
                            logger.log(LangManager.message("log.blender.handshake.error", e.message ?: ""))
                            client.close()
                        }
                    }
                }
            } catch (e: Exception) {
                // Server closed or error
            }
        }.start()
        isRunning.set(true)
        return port
    }

    fun stopServer() {
        isRunning.set(false)
        try {
            serverSocket?.close()
            blenderClient?.close()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun isConnected(): Boolean = blenderClient?.let { !it.isClosed } ?: false

    fun sendReloadCommand(extensionName: String) {
        val client = blenderClient
        if (client == null || client.isClosed) {
            logger.log(LangManager.message("log.blender.cannot.reload"))
            return
        }

        try {
            val out = PrintWriter(client.getOutputStream(), true)
            out.println("{\"type\": \"reload\", \"name\": \"$extensionName\"}")
            logger.log(LangManager.message("log.blender.sent.reload", extensionName))
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.failed.reload", e.message ?: ""))
        }
    }

    companion object {
        val BLENDER_PORT_KEY = Key.create<Int>("BLENDER_PORT")
        fun getInstance(project: Project): BlenderCommunicationService = project.getService(BlenderCommunicationService::class.java)
    }
}
