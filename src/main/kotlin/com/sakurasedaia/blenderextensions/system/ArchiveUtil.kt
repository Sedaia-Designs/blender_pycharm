package com.sakurasedaia.blenderextensions.system

import com.intellij.execution.configurations.GeneralCommandLine
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

object ArchiveUtil {
    fun extractFile(file: Path, targetDir: Path, logger: BlenderLogger? = null, version: String = "", override: Boolean = true) {
        /*
        * Function is the entrypoint for extracting files from the downloaded Blender distribution.
        *
        * This entire function needs to be easily modifiable to ensure that future Blender distributions
        * can be supported if they change the packaging methods.
        * */
        
        if (Files.exists(targetDir)) {
            if (!override) {
                logger?.log("Target directory already exists, skipping extraction: $targetDir")
                return
            }
            targetDir.toFile().deleteRecursively()
            logger?.log("Purged existing $targetDir")
        }
        
        val fName = file.name.lowercase()
        val result = try {
            when {
                fName.endsWith(".zip") -> extractZip(file, targetDir, version, logger)
                fName.endsWith(".tar.xz") -> extractTar(file, targetDir, version, logger)
                fName.endsWith(".dmg") -> extractDmg(file, targetDir, version, logger)
                else -> {
                    logger?.error("Unsupported archive format: $fName")
                }
            }
        } catch (e: Exception) {
            logger?.error("Failed to extract $fName", e)
            -1
        }
        
        when (result) {
            0 -> logger?.log("Successfully extracted ${file.name} to $targetDir")
            1 -> logger?.log("User canceled extraction of ${file.name}")
            else -> logger?.error("Failed to extract ${file.name}")
        }
    }
    
    private fun extractTar(file: Path, targetDir: Path, version: String, logger: BlenderLogger? = null): Int {
        val targetPath: Path = Path.of("${targetDir.absolutePathString()}/$version")
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
        // Handles the extraction of ZIP files on Windows
        
        // TODO: Write a renaming function to automatically rename the Blender Version. Current Implementation left unreachable on purpose
        logger?.log("ZIP extraction is not currently implemented: ${file.name}")
        return -1
        
        /*
        logger?.log("Extracting ${file.name}")
        val command = GeneralCommandLine(
            "powershell",
            "Expand-Archive",
            "-Path",
            file.absolutePathString(),
            "-DestinationPath",
            targetDir.absolutePathString(),
            "-Force"
        )
        
        return ExternalProcessUtil.executeCommand(command, logger = logger)
        */
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
