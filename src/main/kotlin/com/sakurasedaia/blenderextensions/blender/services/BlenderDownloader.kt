package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.utils.ArchiveUtil
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.blender.utils.BlenderPathUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.blender.model.DownloadProgress
import com.sakurasedaia.blenderextensions.blender.model.ProgressType
import com.sakurasedaia.blenderextensions.blender.utils.toBlenderHandler

@Service(Service.Level.PROJECT)
class BlenderDownloader(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)
    private val isDownloadedCache = mutableMapOf<String?, Boolean>()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    fun updateProgress(progress: DownloadProgress) {
        _downloadProgress.value = progress
    }

    fun getBaseDownloadDirectory(): Path {
        val path = BlenderSettings.getInstance(project).state.downloadsPath
        return Path.of(path)
    }

    fun getAppDirectory(): Path {
        return getBaseDownloadDirectory().resolve("app")
    }

    fun getVersionDirectory(version: String?): Path {
        return getAppDirectory().resolve(version ?: "unknown")
    }
    
    fun isDownloaded(version: String?): Boolean {
        return isDownloadedCache.getOrPut(version) {
            val downloadDir = getVersionDirectory(version)
            BlenderPathUtil.findBlenderExecutable(downloadDir) != null
        }
    }

    fun clearCache() {
        isDownloadedCache.clear()
    }

    fun deleteVersion(version: String) {
        val downloadDir = getVersionDirectory(version)
        if (downloadDir.exists()) {
            downloadDir.toFile().deleteRecursively()
            logger.log(LangManager.message("log.blender.deleted.version", version, downloadDir.absolutePathString()))
            clearCache()
        }
    }

    fun getOrDownloadBlenderPath(version: String): String? {
        logger.log("BlenderDownloader.getOrDownloadBlenderPath: version=$version")
        
        // Check if already downloaded before showing progress
        val versionDir = getVersionDirectory(version)
        val executable = BlenderPathUtil.findBlenderExecutable(versionDir)
        if (executable != null) {
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
        val baseDir = getBaseDownloadDirectory()
        val appDir = getAppDirectory()
        val versionDir = getVersionDirectory(version)

        if (!appDir.exists()) {
            Files.createDirectories(appDir)
        }

        try {
            if (!isSysCompatible()) {
                throw Exception("System compatibility check failed")
            }
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.incompatible", e))
            return null
        }

        // If not, download it
        val downloadUrl = getDownloadUrl(version)
        logger.log("Blender $version not found in cache. Starting download from: $downloadUrl")
        val downloadedFile = downloadFile(downloadUrl, baseDir) ?: run {
            logger.log(LangManager.message("log.blender.download.failed", version, "Download failed"))
            return null
        }
        
        // Extract it
        logger.log(LangManager.message("log.blender.extracting", downloadedFile.name, versionDir.absolutePathString()))
        val result = extractFile(downloadedFile, appDir, version)
        if (result != 0) {
            logger.log("Extraction failed with code: $result. Deleting potentially corrupted archive: ${downloadedFile.absolutePathString()}")
            try {
                Files.deleteIfExists(downloadedFile)
            } catch (e: Exception) {
                logger.log("Failed to delete corrupted archive: ${e.message}")
            }
            return null
        }

        clearCache()
        val finalExecutable = BlenderPathUtil.findBlenderExecutable(versionDir)
        if (finalExecutable != null) {
            logger.log(LangManager.message("log.blender.extracted", version, finalExecutable.absolutePathString()))
            PythonService.getInstance(project).installFakeBpyModule(version)
        } else {
            logger.log(LangManager.message("log.blender.could.not.find.exec", versionDir.absolutePathString()))
            if (versionDir.exists()) {
                logger.log("Listing contents of ${versionDir.absolutePathString()} (recursive):")
                try {
                    Files.walk(versionDir, 3).use { stream ->
                        stream.forEach { path ->
                            val relative = versionDir.relativize(path)
                            logger.log(" - $relative (${if (path.isDirectory()) "dir" else "file"}, ${if (Files.isExecutable(path)) "exec" else "noexec"})")
                        }
                    }
                } catch (e: Exception) {
                    logger.log("Error listing directory: ${e.message}")
                }
            } else {
                logger.log("Directory ${versionDir.absolutePathString()} does not exist after extraction!")
            }
        }
        return finalExecutable?.absolutePathString()
    }

    internal fun getDownloadUrl(
        version: String,
        osName: String = BlenderHelper.getOsName(),
        archType: String = BlenderHelper.getArchName()
    ): String {
        /*
         * Utility to fetch the download URL from the Blender Website, an update will need to be made to
         * BlenderVersions to handle automatic Blender updates
         * */

        val extension = when (osName) {
            "windows" -> "zip"
            "linux" -> "tar.xz"
            "macos" -> "dmg"
            else -> {
                throw (IllegalArgumentException("OS is not supported"))
            }
        }

        val fullVersion = BlenderVersions.getFullVersion(version) ?: "$version.0"
        return "https://download.blender.org/release/Blender$version/blender-$fullVersion-$osName-$archType.$extension"
    }

    private fun downloadFile(url: String, targetDir: Path): Path? {
        val fileName = url.substringAfterLast("/")
        val targetFile = targetDir.resolve(fileName)
	      logger.log("Downloading to: ${targetFile.absolutePathString()}")
        val indicator = ProgressManager.getInstance().progressIndicator
        val statusText = LangManager.message("log.blender.downloading.progress", fileName)
        val version = _downloadProgress.value.version
        val handler = indicator.toBlenderHandler(this, version, statusText)
        handler.text2 = url
        
        if (!targetFile.exists()) {
            try {
                HttpRequests.request(url)
                    .connect { request ->
                        request.saveToFile(targetFile, handler)
                    }
                return targetFile
            } catch (e: Exception) {
                if (e is com.intellij.openapi.progress.ProcessCanceledException) {
                    logger.log(LangManager.message("log.blender.download.cancelled"))
                    throw e
                } else {
                    logger.log(LangManager.message("log.blender.download.error", e.message ?: "Unknown error"))
                }
                return null
            }
        } else {
            logger.log(LangManager.message("log.blender.cache.skip"))
            return targetFile
        }
    }

    private fun extractFile(file: Path, targetDir: Path, version: String): Int {
        val statusText = LangManager.message("log.blender.extracting.progress", file.name)
        val handler = ProgressManager.getInstance().progressIndicator.toBlenderHandler(this, version, statusText)
        handler.text2 = file.name
        handler.isIndeterminate = true
        
        val fileName = file.name
        return try {
            ArchiveUtil.extractFile(file, targetDir, logger, version)
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.extraction.failed", fileName, e.message ?: ""))
            -1
        }
    }

    private fun isSysCompatible(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        val isMac = osName.contains("mac")

        if (isMac) {
            logger.log(LangManager.message("log.blender.macos.unsupported"))
            return false
        }
        return true
    }
    
    companion object {
        fun getInstance(project: Project): BlenderDownloader = project.getService(BlenderDownloader::class.java)
    }
}
