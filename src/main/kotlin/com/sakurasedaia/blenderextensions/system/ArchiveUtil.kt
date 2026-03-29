package com.sakurasedaia.blenderextensions.system

import com.intellij.execution.configurations.GeneralCommandLine
import com.sakurasedaia.blenderextensions.blender.BlenderLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

object ArchiveUtil {

    fun extractZip(file: Path, targetDir: Path, flatten: Boolean = true, logger: BlenderLogger? = null) {
        val commands = mutableListOf(
            GeneralCommandLine("powershell", "Expand-Archive", "-Path", file.absolutePathString(), "-DestinationPath", targetDir.absolutePathString(), "-Force")
        )
        if (flatten) {
            commands.add(GeneralCommandLine("powershell", "Get-ChildItem -Path '${targetDir.absolutePathString()}' -Directory | ForEach-Object { Move-Item -Path \"\$(\$_.FullName)\\*\" -Destination '${targetDir.absolutePathString()}' -Force; Remove-Item -Path \"\$(\$_.FullName)\" -Force }"))
        }
        commands.add(GeneralCommandLine("powershell", "Remove-Item", "-Path", file.absolutePathString()))
        
        ExternalProcessUtil.executeGroup(commands, silentFailure = false, logger = logger)
    }

    fun extractTar(file: Path, targetDir: Path, stripComponents: Int = 0, logger: BlenderLogger? = null) {
        val commands = mutableListOf<GeneralCommandLine>()
        val tarArgs = mutableListOf("-xf", file.absolutePathString(), "-C", targetDir.absolutePathString())
        if (stripComponents > 0) {
            tarArgs.add("--strip-components=$stripComponents")
        }
        
        commands.add(GeneralCommandLine("tar", *tarArgs.toTypedArray()))
        commands.add(GeneralCommandLine(if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "rm", 
            if (System.getProperty("os.name").lowercase().contains("win")) "Remove-Item" else "-f", 
            file.absolutePathString()))
        
        ExternalProcessUtil.executeGroup(commands, false, logger)
    }

    fun extractTarXz(file: Path, targetDir: Path, appDir: Path, logger: BlenderLogger? = null) {
        if (!targetDir.exists()) Files.createDirectories(targetDir)

        val rawTarExtractDir = appDir.resolve(file.name.split(".").dropLast(2).joinToString("."))

        val commands = listOf(
            GeneralCommandLine("tar", "-xf", file.absolutePathString(), "-C", appDir.absolutePathString()),
            GeneralCommandLine("mv", "-f", rawTarExtractDir.absolutePathString(), targetDir.absolutePathString()),
            GeneralCommandLine("rm", file.absolutePathString())
        )
        ExternalProcessUtil.executeGroup(commands, false, logger)
    }

    fun extractDmg(file: Path, targetDir: Path, version: String, logger: BlenderLogger? = null) {
        if (!System.getProperty("os.name").lowercase().contains("mac")) {
            logger?.log("DMG extraction is only supported on macOS")
            return
        }

        val mountPoint = Path.of("/tmp", "blender_mount_${System.currentTimeMillis()}")
        val appName = "Blender $version.app"
        val blendDir = targetDir.resolve(version)
        val appInBlendDir = blendDir.resolve(appName)
        Files.createDirectories(mountPoint)
        try {
            ExternalProcessUtil.executeGroup(listOf(GeneralCommandLine("hdiutil", "detach", mountPoint.absolutePathString(), "-force").apply {
                setWorkDirectory(targetDir.toFile())
            }), silentFailure = true, logger)

            logger?.log("Mounting DMG: ${file.absolutePathString()}")
            ExternalProcessUtil.executeCommand(
                GeneralCommandLine("hdiutil", "attach", file.absolutePathString(), "-mountpoint", mountPoint.absolutePathString(), "-nobrowse", "-readonly"), silentFailure = false, logger)

            Files.list(mountPoint).use { stream ->
                val appFile = stream.filter { it.name == "Blender.app" }.findFirst().orElse(null)
                if (appFile != null) {
                    logger?.log("Copying app: ${appFile.name} to ${appInBlendDir.absolutePathString()}")
                    Files.createDirectories(blendDir)
                    ExternalProcessUtil.executeGroup(listOf(
                        GeneralCommandLine("cp", "-R", appFile.absolutePathString(), appInBlendDir.absolutePathString())
                    ), false, logger)
                } else {
                    logger?.log("Failed to find Blender.app in mount point: ${mountPoint.absolutePathString()}")
                }
            }
        } catch (e: Exception) {
            logger?.log("Failed to mount DMG ${file.absolutePathString()}: ${e.message}")
        } finally {
            logger?.log("Detaching mount point: ${mountPoint.absolutePathString()}")
            ExternalProcessUtil.executeGroup(listOf(
                GeneralCommandLine("hdiutil", "detach", mountPoint.absolutePathString(), "-force"),
                GeneralCommandLine("rm", file.absolutePathString())
            ), false, logger)
            try {
                Files.deleteIfExists(mountPoint)
            } catch (_: Exception) {}
        }
    }
}
