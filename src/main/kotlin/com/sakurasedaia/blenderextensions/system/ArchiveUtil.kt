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
        logger?.log("DMG extraction is not supported")
    }
}
