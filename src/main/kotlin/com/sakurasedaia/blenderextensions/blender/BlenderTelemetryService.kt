package com.sakurasedaia.blenderextensions.blender

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.python.PythonUtil
import com.sakurasedaia.blenderextensions.run.BlenderRunConfigurationOptions
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class BlenderTelemetryService(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)

    fun collectAndLogTelemetry(
        context: String = "Startup",
        options: BlenderRunConfigurationOptions? = null,
        blenderPath: String? = null,
        blenderVersion: String? = null
    ) {
        val osName = System.getProperty("os.name")
        val osVersion = System.getProperty("os.version")
        val osArch = System.getProperty("os.arch")
        val javaVersion = System.getProperty("java.version")
        
        val ramGb = try {
            val osBean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            val totalMemory = osBean.totalPhysicalMemorySize
            totalMemory / (1024 * 1024 * 1024)
        } catch (e: Exception) {
            -1L
        }

        val telemetryData = StringBuilder()
        telemetryData.append("\n--- Offline Telemetry [$context] ---\n")
        telemetryData.append("OS: $osName ($osVersion)\n")
        telemetryData.append("Architecture: $osArch\n")
        telemetryData.append("RAM: ${if (ramGb != -1L) "$ramGb GB" else "Unknown"}\n")
        telemetryData.append("Java Version: $javaVersion\n")
        
        // Add project-specific info
        val projectPath = project.basePath
        if (projectPath != null) {
            val sandboxDir = Path.of(projectPath, ".blender-sandbox")
            telemetryData.append("Sandbox exists: ${sandboxDir.exists()}\n")
        }

        // Add Blender info if available
        if (blenderPath != null) {
            telemetryData.append("Blender Path: $blenderPath\n")
            val version = blenderVersion ?: PythonUtil.getBlenderVersion(blenderPath)
            telemetryData.append("Blender Version: $version\n")
            
            val pythonInfo = PythonService.getInstance(project).getBlenderPythonInfo(blenderPath)
            telemetryData.append("Python Version: ${pythonInfo.first}\n")
            telemetryData.append("fake-bpy-module status: ${if (pythonInfo.second) "Installed" else "Not Found"}\n")
        }

        // Add Run Configuration settings if available
        if (options != null) {
            telemetryData.append("Run Configuration Settings:\n")
            telemetryData.append("  - Sandboxed: ${options.isSandboxed}\n")
            telemetryData.append("  - Import User Config: ${options.importUserConfig}\n")
            telemetryData.append("  - Additional Args: ${options.additionalArguments}\n")
            telemetryData.append("  - Custom Command: ${options.blenderCommand.isNullOrBlank().not()}\n")
        }
        
        telemetryData.append("--------------------------------------")

        logger.log(telemetryData.toString())
    }

    companion object {
        fun getInstance(project: Project): BlenderTelemetryService = project.getService(BlenderTelemetryService::class.java)
    }
}
