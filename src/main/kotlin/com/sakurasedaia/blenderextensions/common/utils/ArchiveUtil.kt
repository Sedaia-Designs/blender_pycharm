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
        val tempDir = Files.createTempDirectory(targetDir, "blender-extract-tar-")
        try {
            val command = GeneralCommandLine(
                "tar",
                "-xJf",
                file.absolutePathString(),
                "-C",
                tempDir.absolutePathString()
            )
            
            logger?.log("Extracting ${file.name} to temporary directory $tempDir: Running command $command")
            val result = ExternalProcessUtil.executeCommand(command, logger = logger)
            if (result != 0) {
                logger?.error("Tar extraction failed with exit code $result")
                return result
            }

            return moveStrippedContent(tempDir, targetPath, logger)
        } catch (e: Exception) {
            logger?.error("Failed to extract TAR ${file.name}", e)
            return -1
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
    
    private fun extractZip(file: Path, targetDir: Path, version: String, logger: BlenderLogger? = null): Int {
        // Blender only distributes Zip files for Window builds of the software
        if (!BlenderHelper.isWindows()) {
            logger?.error("ZIP extraction is only supported on Windows. Blender only distributes Zip files for Windows builds.")
            return -1
        }

        val targetPath: Path = targetDir.resolve(version)
        val tempDir = Files.createTempDirectory(targetDir, "blender-extract-zip-")
        try {
            logger?.log("Extracting ${file.name} to temporary directory $tempDir using IntelliJ Decompressor")
            Decompressor.Zip(file).extract(tempDir)

            return moveStrippedContent(tempDir, targetPath, logger)
        } catch (e: Exception) {
            logger?.error("Failed to extract ZIP ${file.name}", e)
            return -1
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    internal fun moveStrippedContent(tempDir: Path, target: Path, logger: BlenderLogger?): Int {
        try {
            val topLevelItems = Files.list(tempDir).use { it.toList() }
            if (topLevelItems.isEmpty()) {
                logger?.error("Extracted archive is empty")
                return -1
            }

            if (topLevelItems.size == 1 && Files.isDirectory(topLevelItems[0])) {
                val sourceDir = topLevelItems[0]
                logger?.log("Detected single top-level directory ${sourceDir.name}, stripping it and moving to $target")
                Files.move(sourceDir, target)
            } else {
                logger?.log("Multiple top-level items detected or no directory, moving all items to $target")
                Files.createDirectories(target)
                Files.list(tempDir).use { stream ->
                    stream.forEach { path ->
                        Files.move(path, target.resolve(path.fileName))
                    }
                }
            }
            return 0
        } catch (e: Exception) {
            logger?.error("Failed to move extracted content to $target: ${e.message}", e)
            target.toFile().deleteRecursively()
            return -1
        }
    }
    
    private fun extractDmg(file: Path, targetDir: Path, version: String, logger: BlenderLogger? = null): Int {
        if (!BlenderHelper.isMac()) {
            logger?.error("DMG extraction is only supported on macOS.")
            return -1
        }

        val targetPath: Path = targetDir.resolve(version)
        var mountPoint: Path? = null

        try {
            // 1. Mount the DMG
            logger?.log("Mounting DMG: ${file.name}")
            val mountCommand = GeneralCommandLine("hdiutil", "attach", "-plist", "-nobrowse", file.absolutePathString())
            val mountOutput = ExternalProcessUtil.execAndGetOutput(mountCommand)

            if (mountOutput.exitCode != 0) {
                logger?.error("Failed to mount DMG: ${mountOutput.stderr}")
                return mountOutput.exitCode
            }

            // Extract mount point from plist output (looking for <string>/Volumes/...</string>)
            val mountPointMatch = "/Volumes/[^<]+".toRegex().find(mountOutput.stdout)
            if (mountPointMatch == null) {
                logger?.error("Could not determine mount point from hdiutil output")
                return -1
            }
            mountPoint = Path.of(mountPointMatch.value)
            logger?.log("DMG mounted at $mountPoint")

            // 2. Find .app and Copy using ditto
            val appBundle = Files.list(mountPoint).use { stream ->
                stream.filter { it.extension == "app" && it.isDirectory() }.findFirst().orElse(null)
            }

            if (appBundle == null) {
                logger?.error("No .app bundle found in mounted DMG")
                return -1
            }

            val appTargetName = "Blender-${version}.app"
            val finalAppPath = targetPath.resolve(appTargetName)
            Files.createDirectories(targetPath)

            logger?.log("Copying $appBundle to $finalAppPath using ditto")
            val copyCommand = GeneralCommandLine("ditto", appBundle.absolutePathString(), finalAppPath.absolutePathString())
            val copyResult = ExternalProcessUtil.executeCommand(copyCommand, logger = logger)
            if (copyResult != 0) {
                logger?.error("Failed to copy .app bundle")
                return copyResult
            }

            // 3. Clear quarantine attributes
            logger?.log("Clearing quarantine attributes from $finalAppPath")
            val xattrCommand = GeneralCommandLine("xattr", "-rd", "com.apple.quarantine", finalAppPath.absolutePathString())
            ExternalProcessUtil.executeCommand(xattrCommand, silentFailure = true, logger = logger)

            return 0
        } catch (e: Exception) {
            logger?.error("Failed to extract DMG ${file.name}: ${e.message}", e)
            return -1
        } finally {
            // 4. Always unmount
            mountPoint?.let {
                logger?.log("Unmounting DMG from $it")
                val unmountCommand = GeneralCommandLine("hdiutil", "detach", it.absolutePathString(), "-force")
                ExternalProcessUtil.executeCommand(unmountCommand, silentFailure = true, logger = logger)
            }
        }
    }
}
