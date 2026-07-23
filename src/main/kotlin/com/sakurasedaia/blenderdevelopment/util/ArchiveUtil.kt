package com.sakurasedaia.blenderdevelopment.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
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
      Decompressor.Zip(archive)
        .withZipExtensions()
        .extract(destination)
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
   * Extracts a DMG by mounting it with macOS `hdiutil` and copying its contents.
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
      val mountPoint = Files.createTempDirectory(
        SystemInfo.getSysInfo.tempDir,
        "dmg-mount-",
      )

      var attached = false
      try {
        runHdiutil(
          "attach",
          archive.toString(),
          "-readonly",
          "-nobrowse",
          "-mountpoint",
          mountPoint.toString(),
        )
        attached = true
        NioFiles.createDirectories(destination)

        Files.list(mountPoint).use { entries ->
          entries.forEach { source ->
            NioFiles.copyRecursively(source, destination.resolve(source.fileName))
          }
        }
      } finally {
        if (attached) {
          runHdiutil("detach", mountPoint.toString())
        }
        NioFiles.deleteRecursively(mountPoint)
      }
    }
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
    val message = MessageBundle.message(
      "notification.archive.extraction.failed",
      (archive.fileName ?: archive).toString(),
      exception.message ?: errorType.message,
    )
    PluginLogger.getInstance(project).error(errorType, exception)
    NotificationModal.getInstance(project).sendError(message)
  }

  private fun runHdiutil(vararg arguments: String) {
    val commandLine = GeneralCommandLine("/usr/bin/hdiutil")
      .withParameters(*arguments)
    val output = CapturingProcessHandler(commandLine).runProcess()

    if (output.exitCode != 0) {
      throw IOException(
        "hdiutil failed with exit code ${output.exitCode}: ${output.stderr}",
      )
    }
  }
}
