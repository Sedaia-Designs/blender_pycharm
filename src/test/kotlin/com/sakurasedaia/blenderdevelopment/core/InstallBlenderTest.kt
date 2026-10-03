package com.sakurasedaia.blenderdevelopment.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicInteger

class InstallBlenderTest : BasePlatformTestCase() {
    // TODO(V1): Add live and platform-specific ZIP, TAR, and DMG installation coverage on supported operating systems.
    private lateinit var downloadDirectory: Path
    private lateinit var installDirectory: Path
    private lateinit var tempDirectory: Path
    private val emptyArchiveDigest = MessageDigest.getInstance("SHA-256").digest()

    override fun setUp() {
        super.setUp()
        downloadDirectory = Files.createTempDirectory("install-blender-test")
        installDirectory = Files.createTempDirectory("install-blender-artifacts-test")
        tempDirectory = Files.createTempDirectory("install-blender-staging-test")
    }

    override fun tearDown() {
        try {
            downloadDirectory.toFile().deleteRecursively()
            installDirectory.toFile().deleteRecursively()
            tempDirectory.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testDownloadServiceForwardsArtifactDetailsAndResult() {
        val expectedDownload = downloadDirectory.resolve("downloaded-blender.tar.xz")
        var request: DownloadRequest? = null
        val installer =
            BlenderInstallationService(
                artifactDownloader = { actualProject, url, target, archiveName ->
                    request = DownloadRequest(actualProject === project, url, target, archiveName)
                    completedDownload(expectedDownload)
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val result =
            installer
                .downloadBlenderService(
                    project = project,
                    downloadUrl = BlenderVersions.getVersionMeta("4.5")!!.getDownloadURL(),
                    targetDirectory = downloadDirectory,
                    version = "4.5",
                )
                .join()

        assertEquals(expectedDownload, result)
        assertEquals(
            DownloadRequest(
                usesExpectedProject = true,
                url = BlenderVersions.getVersionMeta("4.5")!!.getDownloadURL(),
                target = downloadDirectory,
                archiveName =
                    BlenderVersions.getVersionMeta("4.5")?.getArchiveName()?.takeIf(String::isNotBlank)
                        ?: error("Expected a supported archive for the current test platform"),
            ),
            request,
        )
    }

    fun testDownloadVersionReturnsExistingArchiveWithoutDownloading() {
        val archiveName =
            BlenderVersions.getVersionMeta("4.5")?.getArchiveName()?.takeIf(String::isNotBlank)
                ?: error("Expected a supported archive for the current test platform")
        val existingArchive = Files.createFile(downloadDirectory.resolve(archiveName))
        var downloadInvoked = false
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ ->
                    downloadInvoked = true
                    failedDownload(AssertionError("Downloader should not be invoked"))
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val result = installer.downloadVersion("4.5", project).join()

        assertEquals(existingArchive, result)
        assertFalse(downloadInvoked)
    }

    fun testDownloadVersionFailsForUnknownVersionWithoutDownloading() {
        var downloadInvoked = false
        var errorReported = false
        val installer =
            BlenderInstallationService(
                artifactDownloader =
                    BlenderArtifactDownloader { _, _, _, _ ->
                        downloadInvoked = true
                        completedDownload(downloadDirectory.resolve("unexpected"))
                    },
                downloadPathOverride = downloadDirectory,
                errorReporter = BlenderInstallErrorReporter { errorReported = true },
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.downloadVersion("999.999", project).join() }.exceptionOrNull()

        assertTrue(failure is CompletionException)
        assertTrue(failure?.cause is IllegalArgumentException)
        assertFalse(downloadInvoked)
        assertTrue(errorReported)
    }

    fun testDownloadVersionPropagatesDownloadFailure() {
        var downloadInvoked = false
        val expectedFailure = IOException("No route to host")
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ ->
                    downloadInvoked = true
                    failedDownload(expectedFailure)
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.downloadVersion("4.5", project).join() }.exceptionOrNull()

        assertTrue(failure is CompletionException)
        assertTrue(failureCause(failure) is IOException)
        assertTrue(failureCause(failure)?.message.orEmpty().contains("BL-011"))
        assertSame(expectedFailure, failureCause(failure)?.cause)
        assertTrue(downloadInvoked)
    }

    fun testDownloadVersionPropagatesCancellation() {
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ ->
                    CompletableFuture.completedFuture(BlenderArtifactDownloadResult.Cancelled)
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.downloadVersion("4.5", project).join() }.exceptionOrNull()

        assertTrue(failure is CompletionException)
        assertTrue(failure?.cause is java.util.concurrent.CancellationException)
    }

    fun testMissingOfficialChecksumBlocksDownload() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        var downloaded = false
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ ->
                    downloaded = true
                    failedDownload(AssertionError("Missing checksum must block the download"))
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { null },
            )

        val failure = runCatching { installer.downloadVersion("4.5", project).join() }.exceptionOrNull()

        assertTrue(failureCause(failure) is IllegalStateException)
        assertTrue(failureCause(failure)?.message.orEmpty().contains("BL-007"))
        assertFalse(downloaded)
        assertFalse(Files.exists(archive))
    }

    fun testCorruptCacheIsRemovedBeforeAnyExtraction() {
        val archive = Files.writeString(downloadDirectory.resolve(archiveName()), "stale archive")
        var downloaded = false
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ ->
                    downloaded = true
                    failedDownload(AssertionError("Retry is a separate user action"))
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.downloadVersion("4.5", project).join() }.exceptionOrNull()

        assertTrue(failureCause(failure) is IOException)
        assertTrue(failureCause(failure)?.message.orEmpty().contains("BL-009"))
        assertFalse(Files.exists(archive))
        assertFalse(downloaded)
    }

    fun testCorruptCacheNeverReachesExtractor() {
        val archive = Files.writeString(downloadDirectory.resolve(archiveName()), "untrusted")
        var extracted = false
        val installer =
            BlenderInstallationService(
                downloadPathOverride = downloadDirectory,
                installPathOverride = installDirectory,
                tempPath = tempDirectory,
                artifactExtractor = { _, _, _ ->
                    extracted = true
                    throw AssertionError("Unverified archive reached extraction")
                },
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.extractBlender("4.5").join() }.exceptionOrNull()

        assertTrue(failureCause(failure) is IOException)
        assertFalse(extracted)
        assertFalse(Files.exists(archive))
    }

    fun testExtractorReceivesVerifiedPrivateCopy() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        var stagedArchive: Path? = null
        val installer =
            BlenderInstallationService(
                downloadPathOverride = downloadDirectory,
                installPathOverride = installDirectory,
                tempPath = tempDirectory,
                platformName = "linux",
                artifactExtractor = { staged, target, _ ->
                    stagedArchive = staged
                    assertTrue(Files.isRegularFile(staged))
                    assertFalse(archive == staged)
                    Files.createDirectory(target.resolve(artifactName()))
                },
                shouldCleanupArchive = { false },
                checksumResolver = { emptyArchiveDigest },
            )

        assertTrue(Files.isDirectory(installer.extractBlender("4.5").join()))
        assertFalse(Files.exists(checkNotNull(stagedArchive)))
        assertTrue(Files.exists(archive))
    }

    fun testWrongDigestDeletesPartialAndDoesNotPublishCache() {
        val partial = Files.writeString(downloadDirectory.resolve("${archiveName()}-test.part"), "truncated")
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ -> completedDownload(partial) },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.downloadVersion("4.5", project).join() }.exceptionOrNull()

        assertTrue(failureCause(failure) is SecurityException)
        assertTrue(failureCause(failure)?.message.orEmpty().contains("BL-008"))
        assertFalse(Files.exists(partial))
        assertFalse(Files.exists(downloadDirectory.resolve(archiveName())))
    }

    fun testConcurrentRequestsPublishOnlyOneVerifiedArchive() {
        val downloads = AtomicInteger()
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, target, name ->
                    downloads.incrementAndGet()
                    completedDownload(Files.createTempFile(target, "$name-", ".part"))
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val first = installer.downloadVersion("4.5", project)
        val second = installer.downloadVersion("4.5", project)

        assertEquals(first.join(), second.join())
        assertEquals(1, downloads.get())
        assertTrue(Files.isRegularFile(first.join()))
    }

    fun testInvalidOriginIsRejectedBeforeDownloaderRuns() {
        var downloaded = false
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ ->
                    downloaded = true
                    failedDownload(AssertionError("Untrusted origin must be rejected"))
                },
                downloadPathOverride = downloadDirectory,
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching {
            installer
                .downloadBlenderService(
                    project,
                    "https://download.blender.org.evil.example/release/Blender4.5/${archiveName()}",
                    downloadDirectory,
                    "4.5",
                )
                .join()
        }
            .exceptionOrNull()

        assertTrue(failureCause(failure) is SecurityException)
        assertFalse(downloaded)
    }

    fun testChecksumReadFailureBlocksCachedArchive() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        val installer =
            BlenderInstallationService(
                downloadPathOverride = downloadDirectory,
                checksumResolver = { throw IOException("offline") },
            )

        val failure = runCatching { installer.downloadVersion("4.5", project).join() }.exceptionOrNull()

        assertTrue(failureCause(failure) is IOException)
        assertTrue(Files.exists(archive))
    }

    fun testConcurrentInstallsExtractOnce() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        val extractions = AtomicInteger()
        val installer =
            BlenderInstallationService(
                downloadPathOverride = downloadDirectory,
                installPathOverride = installDirectory,
                tempPath = tempDirectory,
                platformName = "linux",
                artifactExtractor = { _, target, _ ->
                    extractions.incrementAndGet()
                    Files.createDirectory(target.resolve(artifactName()))
                },
                shouldCleanupArchive = { true },
                checksumResolver = { emptyArchiveDigest },
            )

        val first = installer.extractBlender("4.5")
        val second = installer.extractBlender("4.5")

        assertEquals(first.join(), second.join())
        assertEquals(1, extractions.get())
        assertFalse(Files.exists(archive))
    }

    fun testInstallVersionReturnsExistingInstallationWithoutDownloading() {
        val artifactName = BlenderVersions.getVersionMeta("4.5")?.artifactName ?: error("Expected Blender 4.5 metadata")
        val existingInstallation = Files.createDirectory(installDirectory.resolve(artifactName))
        var downloadInvoked = false
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ ->
                    downloadInvoked = true
                    completedDownload(downloadDirectory.resolve("unexpected"))
                },
                downloadPathOverride = downloadDirectory,
                installPathOverride = installDirectory,
                platformName = "linux",
            )

        val result = installer.extractBlender("4.5").join()

        assertEquals(existingInstallation, result)
        assertFalse(downloadInvoked)
    }

    fun testSuccessfulInstallationCleansArchiveWhenEnabled() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        val expectedInstallation = installDirectory.resolve(artifactName())
        var cleanedArchive: Path? = null
        val installer =
            installerForCleanupWorkflow(
                archive = archive,
                shouldCleanupArchive = true,
                archiveCleaner = { path ->
                    assertTrue("Installation must complete before archive cleanup", Files.isDirectory(expectedInstallation))
                    cleanedArchive = path
                    Files.delete(path)
                },
            )

        val installed = installer.extractBlender("4.5").join()

        assertEquals(expectedInstallation, installed)
        assertEquals(archive, cleanedArchive)
        assertFalse(Files.exists(archive))
    }

    fun testSuccessfulInstallationPreservesArchiveWhenCleanupDisabled() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        var cleanupInvoked = false
        val installer =
            installerForCleanupWorkflow(
                archive = archive,
                shouldCleanupArchive = false,
                archiveCleaner = { cleanupInvoked = true },
            )

        val installed = installer.extractBlender("4.5").join()

        assertEquals(installDirectory.resolve(artifactName()), installed)
        assertFalse(cleanupInvoked)
        assertTrue(Files.isRegularFile(archive))
    }

    fun testFailedInstallationPreservesArchive() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        val expectedFailure = IOException("Extraction failed")
        var cleanupInvoked = false
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ -> completedDownload(archive) },
                downloadPathOverride = downloadDirectory,
                installPathOverride = installDirectory,
                tempPath = tempDirectory,
                platformName = "linux",
                artifactExtractor = { _, _, _ -> throw expectedFailure },
                shouldCleanupArchive = { true },
                archiveCleaner = { cleanupInvoked = true },
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.extractBlender("4.5").join() }.exceptionOrNull()

        assertTrue(failure is CompletionException)
        assertSame(expectedFailure, failure?.cause)
        assertFalse(cleanupInvoked)
        assertTrue(Files.isRegularFile(archive))
    }

    fun testCleanupFailureDoesNotFailSuccessfulInstallation() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        val installer =
            installerForCleanupWorkflow(
                archive = archive,
                shouldCleanupArchive = true,
                archiveCleaner = { throw IOException("Archive is locked") },
            )

        val installed = installer.extractBlender("4.5").join()

        assertEquals(installDirectory.resolve(artifactName()), installed)
        assertTrue(Files.isDirectory(installed))
        assertTrue(Files.isRegularFile(archive))
    }

    fun testCheckForArtifactReturnsNullWhenInstallationDoesNotExist() {
        val installer =
            BlenderInstallationService(
                downloadPathOverride = downloadDirectory,
                installPathOverride = installDirectory,
            )

        assertNull(installer.checkForArtifact("4.5").join())
    }

    fun testConfiguredPathsAreReadForEachOperation() {
        val config = PluginConfig.getInstance()
        val originalState = config.state.copy()
        val firstDownloadDirectory = Files.createTempDirectory("install-blender-first-download-test")
        val firstInstallDirectory = Files.createTempDirectory("install-blender-first-artifact-test")
        try {
            config.loadState(
                originalState.copy(
                    downloadPath = firstDownloadDirectory.toString(),
                    blenderInstallPath = firstInstallDirectory.toString(),
                )
            )
            val installer = BlenderInstallationService(platformName = "linux")

            config.setDownloadPath(downloadDirectory.toString())
            config.setBlenderInstallPath(installDirectory.toString())
            val archiveName =
                BlenderVersions.getVersionMeta("4.5")?.getArchiveName()?.takeIf(String::isNotBlank)
                    ?: error("Expected a supported archive for the current test platform")
            val expectedArchive = Files.createFile(downloadDirectory.resolve(archiveName))
            val expectedInstallation = Files.createDirectory(installDirectory.resolve(artifactName()))

            assertEquals(expectedArchive, installer.checkForArchive("4.5"))
            assertEquals(expectedInstallation, installer.checkForArtifact("4.5").join())
        } finally {
            config.loadState(originalState)
            firstDownloadDirectory.toFile().deleteRecursively()
            firstInstallDirectory.toFile().deleteRecursively()
        }
    }

    fun testMoveFromTempFlattensWindowsArchiveRoot() {
        val artifactName = artifactName()
        val extractedRoot = Files.createDirectory(tempDirectory.resolve(artifactName))
        Files.writeString(extractedRoot.resolve("blender.exe"), "binary")
        val installer =
            BlenderInstallationService(
                installPathOverride = installDirectory,
                tempPath = tempDirectory,
                platformName = "windows",
            )

        val installed = installer.moveFromTemp(extractedRoot, "4.5")

        assertEquals(installDirectory.resolve(artifactName), installed)
        assertTrue(Files.isRegularFile(installed.resolve("blender.exe")))
        assertFalse(Files.exists(extractedRoot))
    }

    fun testMoveFromTempFlattensLinuxArchiveRoot() {
        val artifactName = artifactName()
        val extractedRoot = Files.createDirectory(tempDirectory.resolve(artifactName))
        Files.writeString(extractedRoot.resolve("blender"), "binary")
        val installer =
            BlenderInstallationService(
                installPathOverride = installDirectory,
                tempPath = tempDirectory,
                platformName = "linux",
            )

        val installed = installer.moveFromTemp(extractedRoot, "4.5")

        assertEquals(installDirectory.resolve(artifactName), installed)
        assertTrue(Files.isRegularFile(installed.resolve("blender")))
        assertFalse(Files.exists(extractedRoot))
    }

    fun testMoveFromTempRenamesMacApplicationAndArtifactCheckFindsIt() {
        val sourceApp = Files.createDirectory(tempDirectory.resolve("Blender.app"))
        val contents = Files.createDirectories(sourceApp.resolve("Contents/MacOS"))
        Files.writeString(contents.resolve("Blender"), "binary")
        val expectedInstallation = installDirectory.resolve("${artifactName()}.app")
        val installer =
            BlenderInstallationService(
                installPathOverride = installDirectory,
                tempPath = tempDirectory,
                platformName = "macos",
            )

        val installed = installer.moveFromTemp(sourceApp, "4.5")

        assertEquals(expectedInstallation, installed)
        assertTrue(Files.isRegularFile(installed.resolve("Contents/MacOS/Blender")))
        assertEquals(expectedInstallation, installer.checkForArtifact("4.5").join())
        assertFalse(Files.exists(sourceApp))
    }

    fun testDeleteVersionRemovesManagedInstallation() {
        val installedArtifact = Files.createDirectory(installDirectory.resolve(artifactName()))
        Files.writeString(installedArtifact.resolve("blender"), "binary")
        val installer =
            BlenderInstallationService(
                installPathOverride = installDirectory,
                platformName = "linux",
            )

        assertTrue(installer.deleteVersion("4.5").join())
        assertFalse(Files.exists(installedArtifact))
    }

    fun testDeleteVersionReportsMissingManagedInstallation() {
        val installer =
            BlenderInstallationService(
                installPathOverride = installDirectory,
                platformName = "linux",
            )

        assertFalse(installer.deleteVersion("4.5").join())
    }

    fun testExtractBlenderPropagatesUnsupportedPlatformFailure() {
        val archive = Files.createFile(downloadDirectory.resolve(archiveName()))
        val installer =
            BlenderInstallationService(
                artifactDownloader = { _, _, _, _ -> completedDownload(archive) },
                downloadPathOverride = downloadDirectory,
                installPathOverride = installDirectory,
                platformName = "unsupported",
                checksumResolver = { emptyArchiveDigest },
            )

        val failure = runCatching { installer.extractBlender("4.5").join() }.exceptionOrNull()

        assertTrue(failure is CompletionException)
        assertTrue(failure?.cause is UnsupportedOperationException)
    }

    private fun artifactName(): String = BlenderVersions.getVersionMeta("4.5")?.artifactName ?: error("Expected Blender 4.5 metadata")

    private fun archiveName(): String = BlenderVersions.getVersionMeta("4.5")?.getArchiveName() ?: error("Expected archive name")

    private fun failureCause(error: Throwable?): Throwable? {
        var cause = error
        while (cause is CompletionException && cause.cause != null) cause = cause.cause
        return cause
    }

    private fun installerForCleanupWorkflow(
        archive: Path,
        shouldCleanupArchive: Boolean,
        archiveCleaner: (Path) -> Unit,
    ): BlenderInstallationService =
        BlenderInstallationService(
            artifactDownloader = { _, _, _, _ -> completedDownload(archive) },
            downloadPathOverride = downloadDirectory,
            installPathOverride = installDirectory,
            tempPath = tempDirectory,
            platformName = "linux",
            artifactExtractor = { _, extractionPath, _ ->
                Files.createDirectory(extractionPath.resolve(artifactName()))
            },
            shouldCleanupArchive = { shouldCleanupArchive },
            archiveCleaner = archiveCleaner,
            checksumResolver = { emptyArchiveDigest },
        )

    private fun completedDownload(path: Path): CompletableFuture<BlenderArtifactDownloadResult> =
        CompletableFuture.completedFuture(BlenderArtifactDownloadResult.Downloaded(path))

    private fun failedDownload(cause: Throwable): CompletableFuture<BlenderArtifactDownloadResult> =
        CompletableFuture.completedFuture(BlenderArtifactDownloadResult.Failed(cause))

    private data class DownloadRequest(
        val usesExpectedProject: Boolean,
        val url: String,
        val target: Path,
        val archiveName: String,
    )
}
