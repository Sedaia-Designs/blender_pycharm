package com.sakurasedaia.blenderextensions.blender

import com.sakurasedaia.blenderextensions.system.ArchiveUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
import com.sakurasedaia.blenderextensions.python.PythonService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

@Service(Service.Level.PROJECT)
class BlenderDownloader(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)
    private val isDownloadedCache = mutableMapOf<String?, Boolean>()

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.None)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    data class DownloadProgress(
        val isDownloading: Boolean = false,
        val progress: Double = 0.0,
        val statusText: String = "",
        val version: String = "",
        val type: ProgressType = ProgressType.NONE
    ) {
        companion object {
            val None = DownloadProgress()
        }
    }

    enum class ProgressType {
        NONE, DOWNLOAD, LINTER
    }

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
            findBlenderExecutable(downloadDir) != null
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
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val isWindows = osName.contains("win")
        val isLinux = !isWindows

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
        
        // Check if already downloaded
        val executable = findBlenderExecutable(versionDir)
        if (executable != null) {
            logger.log(LangManager.message("log.blender.using.cached", version, executable.absolutePathString()))
            return executable.absolutePathString()
        }

        // If not, download it
        val downloadUrl = getDownloadUrl(version, isWindows, isLinux, arch)
        
        logger.log(LangManager.message("log.blender.downloading", version))
        val downloadedFile = downloadFile(downloadUrl, baseDir) ?: run {
            logger.log(LangManager.message("log.blender.download.failed", version, "Download failed"))
            return null
        }
        
        // Extract it
        logger.log(LangManager.message("log.blender.extracting", downloadedFile.name, versionDir.absolutePathString()))
        extractFile(downloadedFile, versionDir, version)

        clearCache()
        val finalExecutable = findBlenderExecutable(versionDir)
        if (finalExecutable != null) {
            logger.log(LangManager.message("log.blender.extracted", version, finalExecutable.absolutePathString()))
            PythonService.getInstance(project).installFakeBpyModule(version)
        } else {
            logger.log(LangManager.message("log.blender.could.not.find.exec", versionDir.absolutePathString()))
        }
        return finalExecutable?.absolutePathString()
    }

    private fun findBlenderExecutable(directory: Path): Path? {
        if (!directory.exists() || !directory.isDirectory()) return null

        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")

        val executableName = if (isWindows) "blender.exe" else "blender"
        
        // Walk the directory to find the executable, limited depth for performance
        Files.walk(directory, 3).use { stream ->
            return stream.filter { path ->
                path.name == executableName && path.isRegularFile() && (isWindows || Files.isExecutable(path))
            }.findFirst().orElse(null)
        }
    }

    private fun getDownloadUrl(version: String, isWindows: Boolean, isLinux: Boolean, arch: String): String {
        val baseUrl = "https://download.blender.org/release/Blender$version/"
        val platformSuffix = when {
            isWindows -> "windows-x64\\.zip"
            isLinux -> "linux-x64\\.tar\\.xz"
            else -> throw Exception("Unsupported platform: $arch")
        }
        
        val regex = Regex("blender-$version\\.(\\d+)-$platformSuffix")
        try {
            val html = HttpRequests.request(baseUrl).readString()
            val matches = regex.findAll(html).toList()
            val best = matches.maxByOrNull { it.groupValues[1].toIntOrNull() ?: -1 }?.value
            if (best != null) return baseUrl + best
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.online.version.error", e.message ?: "Unknown error"))
        }
        
        // Fallback to a safe default if online detection fails
        val fallbackPatch = BlenderVersions.SUPPORTED_VERSIONS.find { it.majorMinor == version }?.fallbackPatch ?: "0"
        val suffix = when {
            isWindows -> "windows-x64.zip"
            else -> "linux-x64.tar.xz"
        }
        return "${baseUrl}blender-$version.$fallbackPatch-$suffix"
    }

    private fun downloadFile(url: String, targetDir: Path): Path? {
        val fileName = url.substringAfterLast("/")
        val targetFile = targetDir.resolve(fileName)
        
        val indicator = ProgressManager.getInstance().progressIndicator
        val statusText = LangManager.message("log.blender.downloading.progress", fileName)
        indicator?.text = statusText
        indicator?.text2 = url
        
        val version = _downloadProgress.value.version

        if (!targetFile.exists()) {
            try {
                HttpRequests.request(url)
                    .connect { request ->
                        val progressIndicator = indicator ?: com.intellij.openapi.progress.EmptyProgressIndicator()
                        request.saveToFile(targetFile, object : com.intellij.openapi.progress.ProgressIndicator {
                            override fun setFraction(fraction: Double) {
                                progressIndicator.fraction = fraction
                                _downloadProgress.value = DownloadProgress(true, fraction, statusText, version, ProgressType.DOWNLOAD)
                            }

                            override fun isPopupWasShown() = progressIndicator.isPopupWasShown
                            override fun isShowing() = progressIndicator.isShowing
                            override fun isModal() = progressIndicator.isModal
                            override fun getModalityState() = progressIndicator.modalityState
                            override fun setModalityProgress(modalityProgress: com.intellij.openapi.progress.ProgressIndicator?) { progressIndicator.setModalityProgress(modalityProgress) }
                            override fun setIndeterminate(indeterminate: Boolean) { progressIndicator.isIndeterminate = indeterminate }
                            override fun isIndeterminate() = progressIndicator.isIndeterminate
                            override fun checkCanceled() = progressIndicator.checkCanceled()
                            override fun start() = progressIndicator.start()
                            override fun stop() = progressIndicator.stop()
                            override fun isRunning() = progressIndicator.isRunning
                            override fun cancel() = progressIndicator.cancel()
                            override fun isCanceled() = progressIndicator.isCanceled
                            override fun setText(text: String?) { progressIndicator.text = text }
                            override fun getText() = progressIndicator.text
                            override fun setText2(text: String?) { progressIndicator.text2 = text }
                            override fun getText2() = progressIndicator.text2
                            override fun getFraction() = progressIndicator.fraction
                            override fun pushState() = progressIndicator.pushState()
                            override fun popState() = progressIndicator.popState()
                        })
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

    private fun extractFile(file: Path, targetDir: Path, version: String) {
        val indicator = ProgressManager.getInstance().progressIndicator
        val statusText = LangManager.message("log.blender.extracting.progress", file.name)
        indicator?.text = statusText
        indicator?.text2 = file.name
        indicator?.isIndeterminate = true
        
        _downloadProgress.value = DownloadProgress(true, -1.0, statusText, version, ProgressType.DOWNLOAD)
        
        val fileName = file.name
        try {
            when {
                fileName.endsWith(".zip") -> ArchiveUtil.extractZip(file, targetDir, true, logger)
                fileName.endsWith(".tar.xz") -> ArchiveUtil.extractTarXz(file, getVersionDirectory(version), getAppDirectory(), logger)
                else -> logger.log(LangManager.message("log.blender.unsupported.format", fileName))
            }
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.extraction.failed", fileName, e.message ?: ""))
            throw e
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
