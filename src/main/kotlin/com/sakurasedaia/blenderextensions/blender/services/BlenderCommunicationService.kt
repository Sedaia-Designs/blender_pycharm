package com.sakurasedaia.blenderextensions.blender.services

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger

@Service(Service.Level.PROJECT)
class BlenderCommunicationService(private val project: Project, private val cs: CoroutineScope) : Disposable {
    private val gson = Gson()
    private val logger = BlenderLogger.getInstance(project)
    private val clientLock = Any()
    private var serverSocket: ServerSocket? = null
    private var blenderClient: Socket? = null

    fun startServer(): Int {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort
        project.putUserData(BLENDER_PORT_KEY, port)

        cs.launch(Dispatchers.IO) {
            try {
                while (isActive && !server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        null
                    }
                    if (client != null) {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val firstLine = withContext(Dispatchers.IO) { reader.readLine() }
                            if (firstLine != null && firstLine.contains("\"type\": \"ready\"")) {
                                logger.log(LangManager.message("log.blender.connected", port))
                                synchronized(clientLock) {
                                    blenderClient?.close()
                                    blenderClient = client
                                }
                            } else {
                                logger.log(LangManager.message("log.blender.handshake.failed"))
                                client.close()
                            }
                        } catch (e: Exception) {
                            if (isActive) {
                                logger.log(LangManager.message("log.blender.handshake.error", e.message ?: ""))
                            }
                            client.close()
                        }
                    }
                }
            } catch (e: Exception) {
                // Server closed or error
            }
        }
        return port
    }

    override fun dispose() {
        stopServer()
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            synchronized(clientLock) {
                blenderClient?.close()
                blenderClient = null
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun isConnected(): Boolean = synchronized(clientLock) {
        blenderClient?.let { !it.isClosed } ?: false
    }

    fun sendReloadCommand(extensionName: String) {
        val client = synchronized(clientLock) { blenderClient }
        if (client == null || client.isClosed) {
            logger.log(LangManager.message("log.blender.cannot.reload"))
            return
        }

        try {
            val out = PrintWriter(client.getOutputStream(), true)
            val payload = mapOf("type" to "reload", "name" to extensionName)
            out.println(gson.toJson(payload))
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
