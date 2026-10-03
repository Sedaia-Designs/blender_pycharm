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
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.ArchiveUtil
import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import com.sakurasedaia.blenderdevelopment.util.currentProject
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
        Files.createDirectories(targetDirectory)
        val partialArchive = Files.createTempFile(targetDirectory, "$archiveName-", ".part")

        @Suppress("DialogTitleCapitalization")
        ProgressManager.getInstance()
            .run(
                object :
                    Task.Backgroundable(
                        project,
                        MessageBundle.message("notification.settings.versions.install.download.progress"),
                        true,
                    ) {
                    override fun run(indicator: ProgressIndicator) {
                        try {
                            NioFiles.createDirectories(targetDirectory)
                            BlenderReleaseTransport.open(URI.create(downloadUrl), archiveName).body().use { input ->
                                streamBlenderDownload(input, partialArchive, indicator::checkCanceled)
                            }
                            result.complete(BlenderArtifactDownloadResult.Downloaded(partialArchive))
                        } catch (e: ProcessCanceledException) {
                            deletePartialDownload(partialArchive)
                            result.complete(BlenderArtifactDownloadResult.Cancelled)
                            throw e
                        } catch (exception: Exception) {
                            deletePartialDownload(partialArchive)
                            result.complete(BlenderArtifactDownloadResult.Failed(exception))
                        }
                    }

                    override fun onCancel() {
                        deletePartialDownload(partialArchive)
                        result.complete(BlenderArtifactDownloadResult.Cancelled)
                    }
                }
            )

        return result
    }

    private fun deletePartialDownload(partialArchive: Path) {
        runCatching { Files.deleteIfExists(partialArchive) }
    }
}

/** Utility for downloading and installing versions of Blender */
@Service
@Suppress("UnstableApiUsage")
internal class BlenderInstallationService(
    private val artifactDownloader: BlenderArtifactDownloader = IdeBlenderArtifactDownloader,
    private val downloadPathOverride: Path? = null,
    private val installPathOverride: Path? = null,
    private val errorReporter: BlenderInstallErrorReporter = IdeBlenderInstallErrorReporter,
    private val tempPath: Path = Path.of(PathManager.getTempPath()),
    private val platformName: String = SystemInfo.getSysInfo.osName,
    private val artifactExtractor: (Path, Path, String) -> Path = ::extractDownloadedArtifact,
    private val shouldCleanupArchive: () -> Boolean = { PluginConfig.getInstance().getClearDownloadsAfterInstall() },
    private val archiveCleaner: (Path) -> Unit = EelFileUtils::deleteRecursively,
    private val checksumResolver: (BlenderArtifactDescriptor) -> ByteArray? = ::fetchOfficialDigest,
) {
    private val logger: PluginLogger = PluginLogger.getInstance()

    companion object {
        private val archiveLocks = ConcurrentHashMap<Path, Any>()
        private val activeInstalls = ConcurrentHashMap<Path, AtomicInteger>()

        fun getInstance(): BlenderInstallationService = service()
    }

    internal fun extractBlender(version: String): CompletableFuture<Path> {
        val archiveKey = downloadPath().resolve(descriptor(version).filename).toAbsolutePath().normalize()
        val active = activeInstalls.computeIfAbsent(archiveKey) { AtomicInteger() }
        active.incrementAndGet()
        @Suppress("UnstableApiUsage")
        val operation =
            checkForArtifact(version).thenCompose { installedArtifact ->
                if (installedArtifact != null) {
                    CompletableFuture.completedFuture(installedArtifact)
                } else {
                    downloadVersion(version)
                        .thenApplyAsync(
                            { archive ->
                                synchronized(archiveLocks.computeIfAbsent(archiveKey) { Any() }) {
                                    checkForArtifact(version).join()?.let {
                                        return@synchronized it
                                    }
                                    val descriptor = resolvedDescriptor(version, archiveKey)
                                    val digest = checkNotNull(descriptor.expectedSha256)
                                    val valid =
                                        try {
                                            verifyArchiveDigest(archive, digest)
                                        } catch (exception: IOException) {
                                            reject(
                                                ErrorTypes.ARCHIVE_VERIFICATION_IO_FAILURE,
                                                "notification.blender.archive.verification.io",
                                                exception,
                                            )
                                        }
                                    if (!valid) {
                                        reject(ErrorTypes.ARCHIVE_DIGEST_MISMATCH, "notification.blender.archive.digest.mismatch")
                                    }
                                    NioFiles.createDirectories(tempPath)
                                    val extractionPath = Files.createTempDirectory(tempPath, "blender-extract-")
                                    try {
                                        val stagedArchive = extractionPath.resolve(descriptor.filename)
                                        val stagedValid =
                                            try {
                                                Files.copy(archive, stagedArchive)
                                                verifyArchiveDigest(stagedArchive, digest)
                                            } catch (exception: IOException) {
                                                reject(
                                                    ErrorTypes.ARCHIVE_VERIFICATION_IO_FAILURE,
                                                    "notification.blender.archive.verification.io",
                                                    exception,
                                                )
                                            }
                                        if (!stagedValid) {
                                            reject(ErrorTypes.ARCHIVE_DIGEST_MISMATCH, "notification.blender.archive.digest.mismatch")
                                        }
                                        val extractedArtifact = artifactExtractor(stagedArchive, extractionPath, platformName)
                                        val installedArtifact = moveFromTemp(extractedArtifact, version)

                                        installedArtifact
                                    } finally {
                                        EelFileUtils.deleteRecursively(extractionPath)
                                    }
                                }
                            },
                            AppExecutorUtil.getAppExecutorService(),
                        )
                }
            }
        return operation.whenComplete { installed, failure ->
            val remaining = active.decrementAndGet()
            if (installed != null && failure == null && remaining == 0 && shouldCleanupArchive()) {
                synchronized(archiveLocks.computeIfAbsent(archiveKey) { Any() }) {
                    if (active.get() == 0 && Files.exists(archiveKey)) {
                        try {
                            archiveCleaner(archiveKey)
                        } catch (_: IOException) {
                            logger.warn(ErrorTypes.ARCHIVE_CLEANUP_FAILURE_WARNING.toString())
                        }
                    }
                }
            }
        }
    }

    internal fun downloadVersion(
        version: String,
        project: Project = currentProject(),
    ): CompletableFuture<Path> {
        val versionMeta = BlenderVersions.getVersionMeta(version)
        if (versionMeta == null) {
            errorReporter.unknownDownloadUrl()
            return CompletableFuture.failedFuture(IllegalArgumentException("Unknown Blender version: $version"))
        }
        val descriptor =
            try {
                BlenderArtifactDescriptor.forHost(versionMeta)
            } catch (exception: Exception) {
                return CompletableFuture.failedFuture(
                    rejection(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN, "notification.blender.archive.origin.invalid", exception)
                )
            }
        val archive = downloadPath().resolve(descriptor.filename)
        val lock = archiveLocks.computeIfAbsent(archive.toAbsolutePath().normalize()) { Any() }
        return CompletableFuture.supplyAsync(
            {
                synchronized(lock) {
                    val digest = checkNotNull(resolvedDescriptor(descriptor, archive).expectedSha256)
                    if (Files.exists(archive)) {
                        val valid =
                            try {
                                Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && verifyArchiveDigest(archive, digest)
                            } catch (exception: IOException) {
                                reject(
                                    ErrorTypes.ARCHIVE_VERIFICATION_IO_FAILURE,
                                    "notification.blender.archive.verification.io",
                                    exception,
                                )
                            }
                        if (valid) return@synchronized archive
                        try {
                            Files.deleteIfExists(archive)
                        } catch (exception: IOException) {
                            reject(ErrorTypes.ARCHIVE_VERIFICATION_IO_FAILURE, "notification.blender.archive.verification.io", exception)
                        }
                        reject(ErrorTypes.CORRUPT_BLENDER_CACHE, "notification.blender.archive.cache.corrupt")
                    }
                    val downloaded = downloadBlenderService(project, descriptor.archiveUri.toString(), downloadPath(), version).join()
                    try {
                        if (
                            downloaded != archive &&
                                (downloaded.parent != downloadPath() ||
                                    !downloaded.fileName.toString().endsWith(".part") ||
                                    !Files.isRegularFile(downloaded, LinkOption.NOFOLLOW_LINKS))
                        ) {
                            reject(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN, "notification.blender.archive.origin.invalid")
                        }
                        val valid =
                            try {
                                verifyArchiveDigest(downloaded, digest)
                            } catch (exception: IOException) {
                                reject(
                                    ErrorTypes.ARCHIVE_VERIFICATION_IO_FAILURE,
                                    "notification.blender.archive.verification.io",
                                    exception,
                                )
                            }
                        if (!valid) {
                            reject(ErrorTypes.ARCHIVE_DIGEST_MISMATCH, "notification.blender.archive.digest.mismatch")
                        }
                        try {
                            Files.move(downloaded, archive, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        } catch (_: AtomicMoveNotSupportedException) {
                            Files.move(downloaded, archive, StandardCopyOption.REPLACE_EXISTING)
                        }
                        archive
                    } finally {
                        if (downloaded != archive) runCatching { Files.deleteIfExists(downloaded) }
                    }
                }
            },
            AppExecutorUtil.getAppExecutorService(),
        )
    }

    internal fun downloadBlenderService(
        project: Project = currentProject(),
        downloadUrl: String,
        targetDirectory: Path = downloadPath(),
        version: String,
    ): CompletableFuture<Path> {
        try {
            BlenderArtifactDescriptor.requireTrustedUri(
                URI.create(downloadUrl),
                BlenderVersions.getVersionMeta(version)?.getArchiveName() ?: "",
            )
        } catch (exception: Exception) {
            return CompletableFuture.failedFuture(
                rejection(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN, "notification.blender.archive.origin.invalid", exception)
            )
        }
        val archiveName =
            BlenderVersions.getVersionMeta(version)?.getArchiveName()?.takeIf(String::isNotBlank)
                ?: "blender-installer.${SystemInfo().bundleFileType}"
        logger.debug("Downloading Blender archive from '$downloadUrl' to '${targetDirectory.resolve(archiveName)}'")
        val download =
            try {
                artifactDownloader.download(project, downloadUrl, targetDirectory, archiveName)
            } catch (exception: Exception) {
                return CompletableFuture.failedFuture(
                    rejection(ErrorTypes.BLENDER_DOWNLOAD_FAILED, "notification.blender.archive.download.failed", exception).apply {
                        initCause(exception)
                    }
                )
            }
        return download.thenCompose { result ->
            when (result) {
                is BlenderArtifactDownloadResult.Downloaded -> {
                    logger.debug("Downloaded Blender archive to '${result.archive}'")
                    CompletableFuture.completedFuture(result.archive)
                }
                BlenderArtifactDownloadResult.Cancelled -> {
                    CompletableFuture.failedFuture(CancellationException("Blender download was cancelled"))
                }
                is BlenderArtifactDownloadResult.Failed -> {
                    if (result.cause is SecurityException) {
                        reject(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN, "notification.blender.archive.origin.invalid", result.cause)
                    }
                    CompletableFuture.failedFuture(
                        rejection(ErrorTypes.BLENDER_DOWNLOAD_FAILED, "notification.blender.archive.download.failed", result.cause).apply {
                            initCause(result.cause)
                        }
                    )
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
    internal fun checkForArtifact(version: String): CompletableFuture<Path?> {
        val versionMeta = BlenderVersions.getVersionMeta(version)
        val installName = versionMeta?.let(::installedArtifactName)

        val installedApp = installName?.let(installPath()::resolve)
        if (installedApp != null && Files.isDirectory(installedApp)) {
            return CompletableFuture.completedFuture(installedApp)
        }
        return CompletableFuture.completedFuture(null)
    }

    internal fun checkForArchive(version: String): Path? {
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

        val versionMeta =
            requireNotNull(BlenderVersions.getVersionMeta(version)) {
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
        } catch (alreadyExists: FileAlreadyExistsException) {
            throw alreadyExists
        } catch (_: IOException) {
            try {
                NioFiles.copyRecursively(artifact, destination)
                NioFiles.deleteRecursively(artifact)
                return destination
            } catch (copyError: Exception) {
                NioFiles.deleteRecursively(destination)
                throw copyError
            }
        }
    }

    /** Deletes a version and updates the appropriate data streams. */
    @Suppress("UnstableApiUsage")
    fun deleteVersion(version: String): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync(
            {
                val installedArtifact = checkForArtifact(version).join() ?: return@supplyAsync false
                EelFileUtils.deleteRecursively(installedArtifact)
                true
            },
            AppExecutorUtil.getAppExecutorService(),
        )

    /** Used to update a Blender version via a very specific sequence and ensures a smooth update transition. */
    @Suppress("unused")
    fun updateVersion() {
        // TODO: This function will perform a sequenced operation with checks to update a selected MajorMinor to the latest blender version.
    }

    private fun installedArtifactName(version: BlenderVersion): String =
        if (platformName == "macos") "${version.artifactName}.app" else version.artifactName

    private fun downloadPath(): Path = downloadPathOverride ?: Path.of(PluginConfig.getInstance().getDownloadPath())

    private fun installPath(): Path = installPathOverride ?: Path.of(PluginConfig.getInstance().getBlenderInstallPath())

    private fun descriptor(version: String): BlenderArtifactDescriptor =
        BlenderArtifactDescriptor.forHost(requireNotNull(BlenderVersions.getVersionMeta(version)))

    private fun resolvedDescriptor(version: String, cachePath: Path): BlenderArtifactDescriptor =
        resolvedDescriptor(descriptor(version), cachePath)

    private fun resolvedDescriptor(descriptor: BlenderArtifactDescriptor, cachePath: Path): BlenderArtifactDescriptor =
        descriptor.copy(expectedSha256 = requiredDigest(descriptor, cachePath))

    private fun requiredDigest(descriptor: BlenderArtifactDescriptor, cachePath: Path): ByteArray {
        val digest =
            try {
                checksumResolver(descriptor)
            } catch (exception: MissingOfficialChecksumException) {
                removeUnverifiableCache(cachePath)
                reject(ErrorTypes.MISSING_OFFICIAL_BLENDER_CHECKSUM, "notification.blender.archive.checksum.missing", exception)
            } catch (exception: Exception) {
                reject(ErrorTypes.ARCHIVE_VERIFICATION_IO_FAILURE, "notification.blender.archive.verification.io", exception)
            }
        if (digest == null || digest.size != 32) {
            removeUnverifiableCache(cachePath)
            reject(ErrorTypes.MISSING_OFFICIAL_BLENDER_CHECKSUM, "notification.blender.archive.checksum.missing")
        }
        return digest
    }

    private fun removeUnverifiableCache(cachePath: Path) {
        try {
            Files.deleteIfExists(cachePath)
        } catch (exception: IOException) {
            reject(ErrorTypes.ARCHIVE_VERIFICATION_IO_FAILURE, "notification.blender.archive.verification.io", exception)
        }
    }

    private fun rejection(errorType: ErrorTypes, messageKey: String, cause: Throwable? = null): Exception {
        if (cause == null) logger.warn(errorType.toString()) else logger.warn(errorType.toString(), cause)
        NotificationModal.getInstance().sendError(MessageBundle.message(messageKey))
        return errorType.createException()
    }

    private fun reject(errorType: ErrorTypes, messageKey: String, cause: Throwable? = null): Nothing =
        throw rejection(errorType, messageKey, cause)
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
