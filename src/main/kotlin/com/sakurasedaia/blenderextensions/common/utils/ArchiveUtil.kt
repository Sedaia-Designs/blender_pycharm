package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper

object ArchiveUtil {
    fun extractFile(file: Path, targetDir: Path, logger: BlenderLogger? = null, version: String = "", override: Boolean = true): Int {
        /*
        * Function is the entrypoint for extracting files from the downloaded Blender distribution.
        *
        * This entire function needs to be easily modifiable to ensure that future Blender distributions
        * can be supported if they change the packaging methods.
        * */
        
        val versionDir = targetDir.resolve(version)
        if (Files.exists(versionDir)) {
            if (!override) {
                logger?.log("Target directory already exists, skipping extraction: $versionDir")
                return 0
            }
            versionDir.toFile().deleteRecursively()
            logger?.log("Purged existing $versionDir")
        }
        
        val result = try {
            when {
                BlenderHelper.isZip(file) -> extractZip(file, targetDir, version, logger)
                BlenderHelper.isTarXz(file) -> extractTar(file, targetDir, version, logger)
                BlenderHelper.isDmg(file) -> extractDmg(file, targetDir, version, logger)
                else -> {
                    logger?.error("Unsupported archive format: ${file.name}")
                    -1
                }
            }
        } catch (e: Exception) {
            logger?.error("Failed to extract ${file.name}", e)
            -1
        }
        
        when (result) {
            0 -> logger?.log("Successfully extracted ${file.name} to ${targetDir.resolve(version)}")
            1 -> logger?.log("User canceled extraction of ${file.name}")
            else -> logger?.error("Failed to extract ${file.name}")
        }
        return result
    }
    
    private fun extractTar(file: Path, targetDir: Path, version: String, logger: BlenderLogger? = null): Int {
        val targetPath: Path = targetDir.resolve(version)
        Files.createDirectories(targetPath)
        logger?.log("Created $targetPath")
        val command = GeneralCommandLine(
            "tar",
            "-xJf",
            file.absolutePathString(),
            "-C",
            targetPath.absolutePathString(),
            "--strip-components=1"
        )
        
        logger?.log("Extracting ${file.name}: Running command $command")
        return ExternalProcessUtil.executeCommand(command, logger = logger)
    }
    
    private fun extractZip(file: Path, targetDir: Path, version: String, logger: BlenderLogger? = null): Int {
        // Blender only distributes Zip files for Window builds of the software
        if (!BlenderHelper.isWindows()) {
            logger?.error("ZIP extraction is only supported on Windows. Blender only distributes Zip files for Windows builds.")
            return -1
        }

        try {
            val targetPath: Path = targetDir.resolve(version)
            Files.createDirectories(targetPath)
            logger?.log("Created $targetPath")

            val command = GeneralCommandLine(
                "powershell",
                "-Command",
                "Expand-Archive -Path '${file.absolutePathString()}' -DestinationPath '${targetPath.absolutePathString()}' -Force"
            )

            logger?.log("Extracting ${file.name}: Running command $command")
            return ExternalProcessUtil.executeCommand(command, logger = logger)
        } catch (e: Exception) {
            logger?.error("Failed to extract ZIP ${file.name}", e)
            return -1
        }
    }
    
    private fun extractDmg(file: Path, targetDir: Path, version: String, logger: BlenderLogger? = null): Int {
        /* This function is empty on purpose and is here merely to serve as a placeholder for the MacOS extraction process.
        * The current plan for this function is the following.
        *
        * 1. Mount the dmg file
        * 2. Extract the .app directory from the mounted dmg and rename it to `Blender-${Major.Minor}.app`
        * 3. Unmount the dmg file
        * 4. Adjust the startup script to point to the correct **binary** path (Path is `Blender-${Major.Minor}.app/Contents/MacOS/Blender`)
        * */
        logger?.log("DMG extraction is not currently implemented: ${file.name}")
        return -1
    }
}
