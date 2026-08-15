package com.sakurasedaia.blenderdevelopment.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Extracts Blender distribution archives with project logging and user-visible error reporting. */
object ArchiveUtil {
  /**
   * Extracts a ZIP archive using IntelliJ's [Decompressor.Zip].
   *
   * @param archive archive to extract.
   * @param destination directory that receives the extracted files.
   * @param project project used for logging and notifications.
   */
  fun extractZip(archive: Path, destination: Path, project: Project = currentProject()) {
    extract(archive, destination, project) {
      NioFiles.createDirectories(destination)
      Decompressor.Zip(archive).withZipExtensions().extract(destination)
    }
  }

  /**
   * Extracts a TAR archive using IntelliJ's [Decompressor.Tar].
   *
   * @param archive archive to extract.
   * @param destination directory that receives the extracted files.
   * @param project project used for logging and notifications.
   */
  fun extractTar(archive: Path, destination: Path, project: Project = currentProject()) {
    extract(archive, destination, project) {
      NioFiles.createDirectories(destination)
      Decompressor.Tar(archive).extract(destination)
    }
  }

  /**
   * Extracts a DMG by mounting it with macOS `diskutil` and copying its contents.
   *
   * @param archive archive to extract.
   * @param destination directory that receives the extracted files.
   * @param project project used for logging and notifications.
   */
  fun extractDmg(archive: Path, destination: Path, project: Project = currentProject()) {
    if (SystemInfo.getSysInfo.osName != "macos") {
      val exception = UnsupportedOperationException("DMG extraction is only supported on macOS")
      reportFailure(project, archive, ErrorTypes.UNSUPPORTED_OS, exception)
      throw exception
    }

    extract(archive, destination, project) {
      NioFiles.createDirectories(SystemInfo.getSysInfo.tempDir)
      val mountPoint =
          Files.createTempDirectory(
              SystemInfo.getSysInfo.tempDir,
              "dmg-mount-",
          )

      var attached = false
      var deviceIdentifier: String? = null
      var extractionFailure: Exception? = null
      try {
        val output = runDiskutil(*diskutilAttachArguments(archive, mountPoint))
        attached = true
        deviceIdentifier = findDiskIdentifier(output.stdout)
        copyDmgApplication(mountPoint, destination)
      } catch (exception: Exception) {
        extractionFailure = exception
        throw exception
      } finally {
        cleanupMountedImage(attached, deviceIdentifier, mountPoint, extractionFailure)
      }
    }
  }

  internal fun diskutilAttachArguments(archive: Path, mountPoint: Path): Array<String> =
      arrayOf(
          "image",
          "attach",
          "--readOnly",
          "--nobrowse",
          "--mountPoint",
          mountPoint.toString(),
          archive.toString(),
      )

  internal fun findDiskIdentifier(output: String): String? = DEVICE_IDENTIFIER_PATTERN.find(output)?.value

  internal fun copyDmgApplication(mountPoint: Path, destination: Path) {
    val sourceApplication = mountPoint.resolve("Blender.app")
    if (!Files.isDirectory(sourceApplication)) {
      throw IOException("Mounted DMG does not contain Blender.app")
    }

    NioFiles.createDirectories(destination)
    NioFiles.copyRecursively(sourceApplication, destination.resolve(sourceApplication.fileName))
  }

  private inline fun extract(archive: Path, destination: Path, project: Project, operation: () -> Unit) {
    val logger = PluginLogger.getInstance(project)
    logger.debug("Extracting archive '$archive' to '$destination'")

    try {
      operation()
      logger.debug("Extracted archive '$archive' to '$destination'")
    } catch (exception: Exception) {
      reportFailure(project, archive, ErrorTypes.ARCHIVE_EXTRACTION_ERROR, exception)
      throw exception
    }
  }

  private fun reportFailure(project: Project, archive: Path, errorType: ErrorTypes, exception: Exception) {
    val message =
        MessageBundle.message(
            "notification.archive.extraction.failed",
            (archive.fileName ?: archive).toString(),
            exception.message ?: errorType.toString(),
        )
    PluginLogger.getInstance(project).error(errorType, exception)
    NotificationModal.getInstance(project).sendError(message)
  }

  private fun cleanupMountedImage(
      attached: Boolean,
      deviceIdentifier: String?,
      mountPoint: Path,
      extractionFailure: Exception?,
  ) {
    var cleanupFailure: Exception? = null
    if (attached) {
      try {
        runDiskutil("eject", deviceIdentifier ?: mountPoint.toString())
      } catch (exception: Exception) {
        cleanupFailure = exception
      }
    }

    try {
      NioFiles.deleteRecursively(mountPoint)
    } catch (exception: Exception) {
      cleanupFailure?.addSuppressed(exception)
      if (cleanupFailure == null) cleanupFailure = exception
    }

    cleanupFailure?.let { failure ->
      if (extractionFailure != null) extractionFailure.addSuppressed(failure) else throw failure
    }
  }

  private fun runDiskutil(vararg arguments: String): ProcessOutput {
    val commandLine = GeneralCommandLine("/usr/sbin/diskutil").withParameters(*arguments)
    val output = CapturingProcessHandler(commandLine).runProcess()

    if (output.exitCode != 0) {
      throw IOException("diskutil failed with exit code ${output.exitCode}: ${output.stderr}")
    }

    return output
  }

  private val DEVICE_IDENTIFIER_PATTERN = Regex("/dev/disk\\d+(?:s\\d+)*")
}
