package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.Decompressor
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper

object ArchiveUtil {
    fun extractFile(
        file: Path,
        targetDir: Path,
        logger: BlenderLogger? = null,
        version: String = "",
        override: Boolean = true,
        progressIndicator: ProgressIndicator? = null
    ): Int {
        /*
        * Function is the entrypoint for extracting files from the downloaded Blender distribution.
        *
        * This entire function needs to be easily modifiable to ensure that future Blender distributions
        * can be supported if they change the packaging methods.
        * */

        progressIndicator?.let {
            it.text = LangManager.message("log.blender.extracting.progress", file.name)
            it.text2 = file.name
            it.isIndeterminate = true
        }

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
                    logger?.error(LangManager.message("log.blender.unsupported.format", file.name))
                    -1
                }
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException) {
                logger?.log(LangManager.message("log.external.execution.cancelled"))
                throw e
            }
            logger?.error(LangManager.message("log.blender.extraction.failed", version, e.message ?: ""), e)
            -1
        }

        when (result) {
            0 -> logger?.log(LangManager.message("log.blender.extracted", version, targetDir.resolve(version)))
            1 -> logger?.log(LangManager.message("log.external.execution.cancelled"))
            else -> logger?.error(LangManager.message("log.blender.extraction.failed", version, "Unknown error"))
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

            logger?.log("Extracting ${file.name} using IntelliJ Decompressor")
            Decompressor.Zip(file).extract(targetPath)

            // Post-processing: if there is only one directory inside targetPath, move its content up (equivalent to --strip-components=1)
            val contents = Files.list(targetPath).use { it.toList() }
            if (contents.size == 1 && Files.isDirectory(contents[0])) {
                val subDir = contents[0]
                Files.list(subDir).use { subContents ->
                    subContents.forEach {
                        Files.move(it, targetPath.resolve(it.fileName))
                    }
                }
                Files.delete(subDir)
            }

            return 0
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
        logger?.log(LangManager.message("log.archive.dmg.unsupported"))
        return -1
    }
}
