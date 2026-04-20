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

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    fun updateProgress(progress: DownloadProgress) {
        _downloadProgress.value = progress
    }

    fun isDownloaded(version: String?): Boolean {
        return isDownloadedCache.getOrPut(version) {
            val downloadDir = BlenderPathUtil.getVersionDirectory(project, version)
            BlenderPathUtil.findBlenderExecutable(downloadDir) != null
        }
    }

    fun clearCache() {
        isDownloadedCache.clear()
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
            val restriction = FileUtil.getExecutionRestrictionMessage(executable)
            if (restriction != null) logger.error(restriction)
            
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
        if (!isSysCompatible()) {
            return null
        }

        val baseDir = BlenderPathUtil.getBaseDownloadDirectory(project)
        val appDir = BlenderPathUtil.getAppDirectory(project).also { if (!it.exists()) Files.createDirectories(it) }
        val versionDir = BlenderPathUtil.getVersionDirectory(project, version)

        // If not, download it
        val downloadUrl = getDownloadUrl(version)
        logger.log("Blender $version not found in cache. Starting download from: $downloadUrl")
        val downloadedFile = downloadFile(downloadUrl, baseDir) ?: return null
        
        // Extract it
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
            val restriction = FileUtil.getExecutionRestrictionMessage(finalExecutable)
            if (restriction != null) logger.error(restriction)
            
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


    private fun isSysCompatible(): Boolean {
        if (BlenderHelper.isMac()) {
            logger.log(LangManager.message("log.blender.macos.unsupported"))
            return false
        }
        
        if (!BlenderHelper.isOSCompatible()) {
            logger.log(LangManager.message("log.blender.incompatible", "System architecture is not supported"))
            return false
        }
        
        return true
    }
    
    companion object {
        fun getInstance(project: Project): BlenderDownloader = project.getService(BlenderDownloader::class.java)
    }
}
