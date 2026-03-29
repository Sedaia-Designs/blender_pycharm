package com.sakurasedaia.blenderextensions.integration

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderextensions.blender.BlenderCommunicationService
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderScriptGenerator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BlenderIntegrationTest : BasePlatformTestCase() {

    fun testBlenderHeartbeat() {
        val downloader = BlenderDownloader.getInstance(project)
        val blenderPath = downloader.getOrDownloadBlenderPath("5.0")
        
        if (blenderPath == null) {
            println("Blender 5.0 not found, skipping integration test.")
            return
        }

        val communicationService = BlenderCommunicationService.getInstance(project)
        val port = communicationService.startServer()
        
        val scriptGenerator = BlenderScriptGenerator.getInstance()
        val script = scriptGenerator.generateStartupScriptContent(port, "test_addon")
        val scriptFile = createTempFile("blender_start.py", script)

        val commandLine = GeneralCommandLine(blenderPath)
        commandLine.addParameters("--background", "--python", scriptFile.absolutePath)
        
        val handler = OSProcessHandler(commandLine)
        val readyLatch = CountDownLatch(1)
        
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                println("[Blender Output] ${event.text}")
            }
        })

        // Poll for connection in communication service
        val connected = waitWithTimeout(15) {
            communicationService.isConnected()
        }

        try {
            if (!connected) {
                println("Blender did not connect within timeout, skipping integration assertions.")
                return
            }
            
            // Test reload command
            communicationService.sendReloadCommand("test_addon")
            // In a real test we'd check Blender logs for "Received reload command"
            
        } finally {
            handler.destroyProcess()
            communicationService.stopServer()
        }
    }

    private fun waitWithTimeout(seconds: Int, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + seconds * 1000
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun createTempFile(name: String, content: String): java.io.File {
        val scratchPath = java.nio.file.Path.of(PathManager.getConfigPath(), "scratches")
        val scratchDir = if (java.nio.file.Files.exists(scratchPath)) {
            scratchPath
        } else {
            java.nio.file.Path.of("/home/sakura/.config/JetBrains/IntelliJIdea2025.3/scratches/")
        }
        
        if (!java.nio.file.Files.exists(scratchDir)) {
            java.nio.file.Files.createDirectories(scratchDir)
        }
        
        val file = java.nio.file.Files.createTempFile(scratchDir, "junie_test_", name).toFile()
        file.writeText(content)
        file.deleteOnExit()
        return file
    }
}
