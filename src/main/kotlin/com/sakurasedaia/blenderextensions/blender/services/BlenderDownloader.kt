package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.utils.ArchiveUtil
import com.sakurasedaia.blenderextensions.common.utils.FileUtil
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.blender.utils.BlenderPathUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.sakurasedaia.blenderextensions.common.utils.HashUtil
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.blender.model.DownloadProgress
import com.sakurasedaia.blenderextensions.blender.model.ProgressType
import com.sakurasedaia.blenderextensions.blender.utils.toBlenderHandler

/**
 * Service for downloading and extracting Blender versions.
 * 
 * The download progression follows these steps:
 * 1. [isDownloaded] checks if the requested version is already available in the local cache.
 * 2. [getOrDownloadBlenderPath] triggers the download if not found.
 * 3. [getDownloadUrl] determines the correct URL based on the user's OS and architecture.
 * 4. [downloadFile] fetches the archive from download.blender.org.
 * 5. [ArchiveUtil.extractFile] extracts the archive to the plugin's dedicated Blender directory.
 * 6. [PythonService] installs fake-bpy modules for better IDE integration after extraction.
 */
@Service(Service.Level.PROJECT)
class BlenderDownloader(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)
    private val isDownloadedCache = mutableMapOf<String?, Boolean>()
    private val cacheLock = Any()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    fun updateProgress(progress: DownloadProgress) {
        _downloadProgress.value = progress
    }

    fun isDownloaded(version: String?): Boolean {
        synchronized(cacheLock) {
            return isDownloadedCache.getOrPut(version) {
                val downloadDir = BlenderPathUtil.getVersionDirectory(project, version)
                BlenderPathUtil.findBlenderExecutable(downloadDir) != null
            }
        }
    }

    fun clearCache() {
        synchronized(cacheLock) {
            isDownloadedCache.clear()
        }
    }

    fun deleteVersion(version: String) {
        val downloadDir = BlenderPathUtil.getVersionDirectory(project, version)
        if (downloadDir.exists()) {
            downloadDir.toFile().deleteRecursively()
            logger.log(LangManager.message("log.blender.deleted.version", version, downloadDir.absolutePathString()))
            clearCache()
        }
    }

    fun getOrDownloadBlenderPath(version: String): String? {
        logger.log("BlenderDownloader.getOrDownloadBlenderPath: version=$version")
        
        // Check if already downloaded before showing progress
        val versionDir = BlenderPathUtil.getVersionDirectory(project, version)
        val executable = BlenderPathUtil.findBlenderExecutable(versionDir)
        if (executable != null) {
            FileUtil.makeExecutable(executable)
            
            logger.log("Blender $version found at: ${executable.absolutePathString()}")
            logger.log(LangManager.message("log.blender.using.cached", version, executable.absolutePathString()))
            return executable.absolutePathString()
        }

        _downloadProgress.value = DownloadProgress(isDownloading = true, version = version, statusText = LangManager.message("log.blender.downloading", version), type = ProgressType.DOWNLOAD)
        try {
            val result = getOrDownloadBlenderPathInternal(version)
            _downloadProgress.value = DownloadProgress.None
            return result
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress.None
            throw e
        }
    }

    private fun getOrDownloadBlenderPathInternal(version: String): String? {
        if (!isSysCompatible(version)) {
            return null
        }

        val baseDir = BlenderPathUtil.getBaseDownloadDirectory(project)
        val appDir = BlenderPathUtil.getAppDirectory(project).also { if (!it.exists()) Files.createDirectories(it) }
        val versionDir = BlenderPathUtil.getVersionDirectory(project, version)

        // If not, download it
        val downloadUrl = getDownloadUrl(version)
        val hashUrl = getHashFileUrl(version)
        
        logger.log("Blender $version not found in cache. Starting download from: $downloadUrl")
        
        // 1. Download Hash File
        val hashFile = downloadFile(hashUrl, baseDir, silent = true)
        
        // 2. Download Blender Archive
        val downloadedFile = downloadFile(downloadUrl, baseDir) ?: return null
        
        // 3. Verify SHA-256
        if (hashFile != null && hashFile.exists()) {
            val expectedHash = parseHashFromSha256File(hashFile, downloadedFile.name)
            if (expectedHash != null) {
                logger.log("Verifying SHA-256 for ${downloadedFile.name}...")
                if (!HashUtil.verifySha256(downloadedFile, expectedHash, logger)) {
                    logger.error("SHA-256 mismatch for ${downloadedFile.name}. Deleting corrupted file.")
                    downloadedFile.deleteIfExists()
                    return null
                }
            } else {
                logger.log("Could not find hash for ${downloadedFile.name} in $hashUrl, skipping verification")
            }
        } else {
            logger.log("Could not download hash file $hashUrl, skipping verification")
        }
        
        // 4. Extract it
        logger.log(LangManager.message("log.blender.extracting", downloadedFile.name, versionDir.absolutePathString()))
        val statusText = LangManager.message("log.blender.extracting.progress", downloadedFile.name)
        val handler = ProgressManager.getInstance().progressIndicator.toBlenderHandler(this, version, statusText, ProgressType.EXTRACT)
        
        val result = ArchiveUtil.extractFile(downloadedFile, appDir, logger, version, progressIndicator = handler)
        if (result != 0) {
            logger.log("Extraction failed with code: $result. Deleting potentially corrupted archive: ${downloadedFile.absolutePathString()}")
            downloadedFile.deleteIfExists()
            return null
        }

        clearCache()
        val finalExecutable = BlenderPathUtil.findBlenderExecutable(versionDir)
        if (finalExecutable != null) {
            FileUtil.makeExecutable(finalExecutable)
            
            logger.log(LangManager.message("log.blender.extracted", version, finalExecutable.absolutePathString()))
            PythonService.getInstance(project).installFakeBpyModule(version)
        } else {
            logger.log(LangManager.message("log.blender.could.not.find.exec", versionDir.absolutePathString()))
        }
        return finalExecutable?.absolutePathString()
    }

    internal fun getDownloadUrl(
        version: String,
        osName: String = BlenderHelper.getOsName(),
        archType: String = BlenderHelper.getArchName()
    ): String {
        val extension = BlenderHelper.getExtensionByOs(osName)
        val fullVersion = BlenderVersions.getFullVersion(version) ?: "$version.0"
        return "https://download.blender.org/release/Blender$version/blender-$fullVersion-$osName-$archType.$extension"
    }

    private fun downloadFile(url: String, targetDir: Path, silent: Boolean = false): Path? {
        val fileName = url.substringAfterLast("/")
        val targetFile = targetDir.resolve(fileName)
        if (!silent) logger.log("Downloading to: ${targetFile.absolutePathString()}")
        val indicator = ProgressManager.getInstance().progressIndicator
        val statusText = LangManager.message("log.blender.downloading.progress", fileName)
        val version = _downloadProgress.value.version
        val handler = indicator.toBlenderHandler(this, version, statusText)
        if (!silent) handler.text2 = url
        
        if (targetFile.exists()) {
            try {
                val remoteSize = HttpRequests.request(url).connect { it.connection.contentLengthLong }
                if (remoteSize > 0 && targetFile.fileSize() == remoteSize) {
                    if (!silent) logger.log(LangManager.message("log.blender.cache.skip"))
                    return targetFile
                } else if (remoteSize > 0) {
                    if (!silent) logger.log("Cached file size mismatch for $fileName (local: ${targetFile.fileSize()}, remote: $remoteSize). Re-downloading.")
                    targetFile.deleteIfExists()
                } else {
                    if (!silent) logger.log("Could not verify $fileName via content-length. Using cached file.")
                    return targetFile
                }
            } catch (e: Exception) {
                if (!silent) logger.log("Verification failed for $fileName: ${e.message}. Using cached file.")
                return targetFile
            }
        }

        try {
            HttpRequests.request(url)
                .connect { request ->
                    request.saveToFile(targetFile, handler)
                }
            return targetFile
        } catch (e: Exception) {
            if (e is com.intellij.openapi.progress.ProcessCanceledException) {
                if (!silent) logger.log(LangManager.message("log.blender.download.cancelled"))
                throw e
            } else {
                if (!silent) logger.log(LangManager.message("log.blender.download.error", e.message ?: "Unknown error"))
            }
            return null
        }
    }

    internal fun getHashFileUrl(version: String): String {
        val fullVersion = BlenderVersions.getFullVersion(version) ?: "$version.0"
        return "https://download.blender.org/release/Blender$version/blender-$fullVersion.sha256"
    }

    private fun parseHashFromSha256File(hashFile: Path, targetFileName: String): String? {
        try {
            val lines = hashFile.readLines()
            for (line in lines) {
                // Expected format: <hash>  <filename>
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2 && parts[1] == targetFileName) {
                    return parts[0]
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse hash file: ${e.message}", e)
        }
        return null
    }


    private fun isSysCompatible(version: String? = null): Boolean {
        if (!BlenderHelper.isOSCompatible(version)) {
            val message = if (version != null) {
                LangManager.message("log.blender.incompatible.version", version, BlenderHelper.getRawOsName(), BlenderHelper.getRawArchName())
            } else {
                LangManager.message("log.blender.incompatible", "System architecture is not supported")
            }
            logger.log(message)
            BlenderNotification(project).sendWarning(LangManager.message("notification.incompatible.title"), message)
            return false
        }
        
        return true
    }
    
    companion object {
        fun getInstance(project: Project): BlenderDownloader = project.getService(BlenderDownloader::class.java)
    }
}
