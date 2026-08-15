package com.sakurasedaia.blenderdevelopment.core

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.util.ArchiveUtil
import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.currentProject
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException

/** Result of attempting to download a Blender distribution archive. */
internal sealed interface BlenderArtifactDownloadResult {
  /** Successful download containing the completed local archive. */
  data class Downloaded(val archive: Path) : BlenderArtifactDownloadResult

  /** Download cancelled through the IntelliJ progress indicator. */
  data object Cancelled : BlenderArtifactDownloadResult

  /** Download that could not be completed. */
  data class Failed(val cause: Throwable) : BlenderArtifactDownloadResult
}

internal fun interface BlenderArtifactDownloader {
  fun download(
    project: Project,
    downloadUrl: String,
    targetDirectory: Path,
    archiveName: String,
  ): CompletableFuture<BlenderArtifactDownloadResult>
}

internal fun interface BlenderInstallErrorReporter {
  fun unknownDownloadUrl()
}

private object IdeBlenderInstallErrorReporter : BlenderInstallErrorReporter {
  override fun unknownDownloadUrl() {
    PluginLogger.getInstance().error(errorType = ErrorTypes.UNKNOWN_DOWNLOAD_URL)
    NotificationModal.getInstance().sendError(ErrorTypes.UNKNOWN_DOWNLOAD_URL.toString())
  }
}

private object IdeBlenderArtifactDownloader : BlenderArtifactDownloader {
  override fun download(
    project: Project,
    downloadUrl: String,
    targetDirectory: Path,
    archiveName: String,
  ): CompletableFuture<BlenderArtifactDownloadResult> {
    val result = CompletableFuture<BlenderArtifactDownloadResult>()
    val archive = targetDirectory.resolve(archiveName)
    val partialArchive = targetDirectory.resolve("$archiveName.part")

    @Suppress("DialogTitleCapitalization")
    ProgressManager.getInstance().run(object : Task.Backgroundable(
      project,
      MessageBundle.message("notification.settings.versions.install.download.progress"),
      true,
    ) {
      override fun run(indicator: ProgressIndicator) {
        try {
          NioFiles.createDirectories(targetDirectory)
          Files.deleteIfExists(partialArchive)
          HttpRequests.request(downloadUrl).saveToFile(partialArchive, indicator, true)
          indicator.checkCanceled()
          moveCompletedDownload(partialArchive, archive)
          result.complete(BlenderArtifactDownloadResult.Downloaded(archive))
        }
        catch (e: ProcessCanceledException) {
          deletePartialDownload(partialArchive)
          result.complete(BlenderArtifactDownloadResult.Cancelled)
          throw e
        }
        catch (exception: Exception) {
          deletePartialDownload(partialArchive)
          result.complete(BlenderArtifactDownloadResult.Failed(exception))
        }
      }

      override fun onCancel() {
        deletePartialDownload(partialArchive)
        result.complete(BlenderArtifactDownloadResult.Cancelled)
      }
    })

    return result
  }

  private fun moveCompletedDownload(partialArchive: Path, archive: Path) {
    try {
      Files.move(
        partialArchive,
        archive,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(partialArchive, archive, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun deletePartialDownload(partialArchive: Path) {
    runCatching { Files.deleteIfExists(partialArchive) }
  }
}

/**
 * Utility for downloading and installing versions of Blender
 */
@Service
internal class InstallBlender(
  private val artifactDownloader: BlenderArtifactDownloader = IdeBlenderArtifactDownloader,
  private val downloadPathOverride: Path? = null,
  private val installPathOverride: Path? = null,
  private val errorReporter: BlenderInstallErrorReporter = IdeBlenderInstallErrorReporter,
  private val tempPath: Path = Path.of(PathManager.getTempPath()),
  private val platformName: String = SystemInfo.getSysInfo.osName,
  private val artifactExtractor: (Path, Path, String) -> Path = ::extractDownloadedArtifact,
  private val shouldCleanupArchive: () -> Boolean = { PluginConfig.getInstance().getClearDownloadsAfterInstall() },
  private val archiveCleaner: (Path) -> Unit = EelFileUtils::deleteRecursively,
) {
  private val logger: PluginLogger = PluginLogger.getInstance()

  companion object {
    fun getInstance(): InstallBlender = service()
  }

  internal fun extractBlender(version: String): CompletableFuture<Path> {
    @Suppress("UnstableApiUsage")
    return checkForArtifact(version).thenCompose { installedArtifact ->
      if (installedArtifact != null) {
        CompletableFuture.completedFuture(installedArtifact)
      }
      else {
        downloadVersion(version)
          .thenApplyAsync({ archive ->
            NioFiles.createDirectories(tempPath)
            val extractionPath = Files.createTempDirectory(tempPath, "blender-extract-")
            try {
              val extractedArtifact = artifactExtractor(archive, extractionPath, platformName)
              val installedArtifact = moveFromTemp(extractedArtifact, version)

              if (shouldCleanupArchive()) {
                try {
                  archiveCleaner(archive)
                }
                catch (_: IOException) {
                  logger.warn(ErrorTypes.ARCHIVE_CLEANUP_FAILURE_WARNING.toString())
                }
              }

              installedArtifact
            }
            finally {
              EelFileUtils.deleteRecursively(extractionPath)
            }
          }, AppExecutorUtil.getAppExecutorService())
      }
    }
  }

  internal fun downloadVersion(
    version: String,
    project: Project = currentProject(),
  ): CompletableFuture<Path> {
    val versionMeta = BlenderVersions.getVersionMeta(version)

    // 1. Check if a downloaded file exists. Exit and return the archive path if so
    checkForArchive(version)?.let { return CompletableFuture.completedFuture(it) }

    // 2. Parse version URL, and error out if failed
    val downloadUrl = versionMeta?.getDownloadURL()

    if (downloadUrl == null) {
      errorReporter.unknownDownloadUrl()
      return CompletableFuture.failedFuture(IllegalArgumentException("Unknown Blender version: $version"))
    }

    // 3. Download the artifact
    return downloadBlenderService(project, downloadUrl, downloadPath(), version)
  }

  internal fun downloadBlenderService(
    project: Project = currentProject(),
    downloadUrl: String,
    targetDirectory: Path = downloadPath(),
    version: String
  ): CompletableFuture<Path> {
    val archiveName = BlenderVersions.getVersionMeta(version)?.getArchiveName()
      ?.takeIf(String::isNotBlank)
      ?: "blender-installer.${SystemInfo().bundleFileType}"
    logger.debug("Downloading Blender archive from '$downloadUrl' to '${targetDirectory.resolve(archiveName)}'")
    return artifactDownloader.download(project, downloadUrl, targetDirectory, archiveName).thenCompose { result ->
      when (result) {
        is BlenderArtifactDownloadResult.Downloaded -> {
          logger.debug("Downloaded Blender archive to '${result.archive}'")
          CompletableFuture.completedFuture(result.archive)
        }
        BlenderArtifactDownloadResult.Cancelled -> {
          CompletableFuture.failedFuture(CancellationException("Blender download was cancelled"))
        }
        is BlenderArtifactDownloadResult.Failed -> {
          CompletableFuture.failedFuture(result.cause)
        }
      }
    }
  }

  /**
   * Checks to make sure an installation doesn't already exist at the desired location.
   *
   * @param version Blender version to check for.
   * @return completed future containing the installation directory, or `null` when it is not installed.
   */
  internal fun checkForArtifact(
    version: String
  ): CompletableFuture<Path?> {
    val versionMeta = BlenderVersions.getVersionMeta(version)
    val installName = versionMeta?.let(::installedArtifactName)

    val installedApp = installName?.let(installPath()::resolve)
    if (installedApp != null && Files.isDirectory(installedApp)) {
      return CompletableFuture.completedFuture(installedApp)
    }
    return CompletableFuture.completedFuture(null)
  }

  internal fun checkForArchive(
    version: String
  ): Path? {
    val versionMeta = BlenderVersions.getVersionMeta(version)
    val archiveName = versionMeta?.getArchiveName()?.takeIf(String::isNotBlank)

    val downloadedArchive = archiveName?.let(downloadPath()::resolve)
    if (downloadedArchive != null && Files.isRegularFile(downloadedArchive)) {
      return downloadedArchive
    }
    return null
  }

  /**
   * Moves an extracted Blender bundle from staging into the configured installation directory.
   *
   * @param artifact extracted Blender directory (`Blender.app` on macOS, versioned root elsewhere).
   * @param version Blender version used to produce the normalized installation name.
   * @return final installed bundle directory.
   */
  internal fun moveFromTemp(artifact: Path, version: String): Path {
    require(Files.isDirectory(artifact)) { "Extracted Blender artifact is not a directory: $artifact" }

    val versionMeta = requireNotNull(BlenderVersions.getVersionMeta(version)) {
      "Unknown Blender version: $version"
    }
    val installPath = installPath()
    val destination = installPath.resolve(installedArtifactName(versionMeta))
    NioFiles.createDirectories(installPath)
    if (Files.exists(destination)) {
      throw FileAlreadyExistsException(destination.toString())
    }

    try {
      return Files.move(artifact, destination)
    }
    catch (alreadyExists: FileAlreadyExistsException) {
      throw alreadyExists
    }
    catch (_: IOException) {
      try {
        NioFiles.copyRecursively(artifact, destination)
        NioFiles.deleteRecursively(artifact)
        return destination
      }
      catch (copyError: Exception) {
        NioFiles.deleteRecursively(destination)
        throw copyError
      }
    }
  }

  /**
   * Deletes a version and updates the appropriate data streams.
   */
  @Suppress("UnstableApiUsage")
  fun deleteVersion(version: String): CompletableFuture<Boolean> =
    CompletableFuture.supplyAsync({
      val installedArtifact = checkForArtifact(version).join() ?: return@supplyAsync false
      EelFileUtils.deleteRecursively(installedArtifact)
      true
    }, AppExecutorUtil.getAppExecutorService())

  /**
   * Used to update a Blender version via a very specific sequence and ensures a smooth update transition.
   */
  @Suppress("unused")
  fun updateVersion() {
    // TODO: This function will perform a sequenced operation with checks to update a selected MajorMinor to the latest blender version.
  }

  private fun installedArtifactName(version: BlenderVersion): String =
    if (platformName == "macos") "${version.artifactName}.app" else version.artifactName

  private fun downloadPath(): Path =
    downloadPathOverride ?: Path.of(PluginConfig.getInstance().getDownloadPath())

  private fun installPath(): Path =
    installPathOverride ?: Path.of(PluginConfig.getInstance().getBlenderInstallPath())
}

private fun extractDownloadedArtifact(archive: Path, extractionPath: Path, platformName: String): Path =
  when (platformName) {
    "windows" -> {
      ArchiveUtil.extractZip(archive, extractionPath)
      extractionPath.resolve(archiveBaseName(archive))
    }
    "macos" -> {
      ArchiveUtil.extractDmg(archive, extractionPath)
      extractionPath.resolve("Blender.app")
    }
    "linux" -> {
      ArchiveUtil.extractTar(archive, extractionPath)
      extractionPath.resolve(archiveBaseName(archive))
    }
    else -> throw ErrorTypes.UNSUPPORTED_OS.createException()
  }

private fun archiveBaseName(archive: Path): String {
  val fileName = archive.fileName.toString()
  return when {
    fileName.endsWith(".tar.xz", ignoreCase = true) -> fileName.dropLast(".tar.xz".length)
    fileName.endsWith(".zip", ignoreCase = true) -> fileName.dropLast(".zip".length)
    else -> fileName.substringBeforeLast('.', fileName)
  }
}
