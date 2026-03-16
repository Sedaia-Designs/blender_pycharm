package com.sakurasedaia.blenderextensions.blender

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.python.PythonUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
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

    fun getBaseDownloadDirectory(): Path {
        return Path.of(PathManager.getSystemPath(), "blender_downloads")
    }

    fun getAppDirectory(): Path {
        return getBaseDownloadDirectory().resolve("app")
    }

    fun getVersionDirectory(version: String?): Path {
        return getAppDirectory().resolve(version ?: "unknown")
    }

    fun getLintDirectory(version: String): Path {
        return getBaseDownloadDirectory().resolve("lint").resolve(version)
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
        val isMac = osName.contains("mac")
        val isLinux = !isWindows && !isMac

        val baseDir = getBaseDownloadDirectory()
        val appDir = getAppDirectory()
        val versionDir = getVersionDirectory(version)

        if (!appDir.exists()) {
            Files.createDirectories(appDir)
        }
        try {
            if (!isSysCompatible(version)) {
                throw Exception("System compatibility check failed")
            }
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.incompatible.macintoshintel", e))
            return null
        }


        // Check if already downloaded
        val executable = findBlenderExecutable(versionDir)
        if (executable != null) {
            logger.log(LangManager.message("log.blender.using.cached", version, executable.absolutePathString()))
            return executable.absolutePathString()
        }

        // If not, download it
        val downloadUrl = getDownloadUrl(version, isWindows, isMac, isLinux, arch) ?: run {
            logger.log(LangManager.message("log.blender.download.failed", version, "URL not found"))
            return null
        }
        
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
            installFakeBpyModule(finalExecutable, version)
        } else {
            logger.log(LangManager.message("log.blender.could.not.find.exec", versionDir.absolutePathString()))
        }
        return finalExecutable?.absolutePathString()
    }

    fun installFakeBpyModule(blenderExePath: Path, version: String) {
        val pythonExe = PythonUtil.findPythonExecutable(blenderExePath) ?: return
        val lintDir = getLintDirectory(version)
        
        val statusText = LangManager.message("log.blender.installing.linter.progress", version)
        _downloadProgress.value = DownloadProgress(true, -1.0, statusText, version, ProgressType.LINTER)

        if (!lintDir.exists()) {
            Files.createDirectories(lintDir)
        }

        val commandLine = GeneralCommandLine(
            pythonExe.toString(),
            "-m", "pip", "install",
            "fake-bpy-module-$version",
            "--target", lintDir.toString()
        )

        try {
            val handler = OSProcessHandler(commandLine)
            handler.startNotify()
            handler.waitFor()
            logger.log("Successfully installed fake-bpy-module-$version for linting.")
        } catch (e: Exception) {
            logger.log("Failed to install fake-bpy-module for linting: ${e.message}")
        } finally {
            _downloadProgress.value = DownloadProgress.None
        }
    }

    private fun findBlenderExecutable(directory: Path): Path? {
        if (!directory.exists() || !directory.isDirectory()) return null

        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isMac = osName.contains("mac")

        val executableName = if (isWindows) "blender.exe" else "blender"
        
        // Walk the directory to find the executable, limited depth for performance
        Files.walk(directory, 3).use { stream ->
            return stream.filter { path ->
                if (isMac) {
                    path.name == "Blender" && path.toString().contains(".app/Contents/MacOS")
                } else {
                    path.name == executableName && path.isRegularFile() && (isWindows || Files.isExecutable(path))
                }
            }.findFirst().orElse(null)
        }
    }

    private fun getDownloadUrl(version: String, isWindows: Boolean, isMac: Boolean, isLinux: Boolean, arch: String): String {
        val baseUrl = "https://download.blender.org/release/Blender$version/"
        val isArm64 = arch.contains("aarch64") || arch.contains("arm64")
        val platformSuffix = when {
            isWindows -> "windows-x64\\.zip"
            isMac -> if (isArm64) "macos-arm64\\.dmg" else "macos-x64\\.dmg"
            else -> "linux-x64\\.tar\\.xz"
        }
        
        val regex = Regex("blender-$version\\.(\\d+)-$platformSuffix")
        try {
            val html = HttpRequests.request(baseUrl).readString()
            val matches = regex.findAll(html).toList()
            val best = matches.maxByOrNull { it.groupValues[1].toIntOrNull() ?: -1 }?.value
            if (best != null) return baseUrl + best
        } catch (e: Exception) {
            logger.log("Error during online version detection: ${e.message}")
        }
        
        // Fallback to a safe default if online detection fails
        val fallbackPatch = BlenderVersions.SUPPORTED_VERSIONS.find { it.majorMinor == version }?.fallbackPatch ?: "0"
        val suffix = when {
            isWindows -> "windows-x64.zip"
            isMac -> if (isArm64) "macos-arm64.dmg" else "macos-x64.dmg"
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
                        val connection = request.connection
                        val contentLength = connection.contentLengthLong
                        
                        request.saveToFile(targetFile, object : com.intellij.openapi.progress.ProgressIndicator by (indicator ?: com.intellij.openapi.progress.EmptyProgressIndicator()) {
                            override fun setFraction(fraction: Double) {
                                indicator?.fraction = fraction
                                _downloadProgress.value = DownloadProgress(true, fraction, statusText, version, ProgressType.DOWNLOAD)
                            }
                        })
                    }
                return targetFile
            } catch (e: Exception) {
                if (e is com.intellij.openapi.progress.ProcessCanceledException) {
                    logger.log("Download cancelled by user")
                } else {
                    logger.log("Download failed: ${e.message}")
                }
                return null
            }
        } else {
            logger.log("Desired version already exists in cache, skipping download")
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
                fileName.endsWith(".zip") -> extractZip(file, targetDir, version)
                fileName.endsWith(".tar.xz") -> extractTarXz(file, targetDir, version)
                fileName.endsWith(".dmg") -> extractDmg(file, targetDir, version)
                else -> logger.log(LangManager.message("log.blender.unsupported.format", fileName))
            }
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.extraction.failed", fileName, e.message ?: ""))
            throw e
        }
    }

    private fun extractZip(file: Path, targetDir: Path, version: String) {
        val commands = listOf(
            GeneralCommandLine("powershell", "Expand-Archive", "-Path", file.absolutePathString(), "-DestinationPath", targetDir.absolutePathString(), "-Force"),
            GeneralCommandLine("powershell", "Get-ChildItem -Path '${targetDir.absolutePathString()}' -Directory | ForEach-Object { Move-Item -Path \"\$(\$_.FullName)\\*\" -Destination '${targetDir.absolutePathString()}' -Force; Remove-Item -Path \"\$(\$_.FullName)\" -Force }"),
            GeneralCommandLine("powershell", "Remove-Item", "-Path", file.absolutePathString())
        )
        executeExtractionCommands(commands)
    }

    private fun extractTarXz(file: Path, targetDir: Path, version: String) {
        val commands = listOf(
            GeneralCommandLine("sh", "-c", "tar -xvf ${file.absolutePathString()} -C ${targetDir.absolutePathString()} --strip-components=1"),
            GeneralCommandLine("sh", "-c", "rm ${file.absolutePathString()}")
        )

        executeExtractionCommands(commands)
    }

    private fun extractDmg(file: Path, targetDir: Path, version: String) {
        if (!System.getProperty("os.name").lowercase().contains("mac")) {
            logger.log(LangManager.message("log.blender.dmg.extracted"))
            return
        }

        val mountPoint = Path.of("/tmp", "blender_mount_${System.currentTimeMillis()}")
        val appName = "Blender $version.app"
        val blendDir = targetDir.resolve(version)
        val appInBlendDir = blendDir.resolve(appName)
        Files.createDirectories(mountPoint)
        try {
            // First, ensure any previous mount at this point is detached (unlikely with timestamp, but good practice)
            executeExtractionCommands(listOf(
                GeneralCommandLine("hdiutil", "detach", mountPoint.absolutePathString(), "-force").apply {
                    setWorkDirectory(targetDir.toFile())
                }
            ), silentFailure = true)

            logger.log(LangManager.message("log.blender.mounting", file.absolutePathString()))
            executeExtractionCommands(listOf(
                GeneralCommandLine("hdiutil", "attach", file.absolutePathString(), "-mountpoint", mountPoint.absolutePathString(), "-nobrowse", "-readonly")
            ))
            
            Files.list(mountPoint).use { stream ->
                val appFile = stream.filter { it.name == "Blender.app" }.findFirst().orElse(null)
                if (appFile != null) {
                    logger.log(LangManager.message("log.blender.copying.app", appFile.name, appInBlendDir.absolutePathString()))
                    Files.createDirectories(blendDir)
                    executeExtractionCommands(listOf(
                        GeneralCommandLine("cp", "-R", appFile.absolutePathString(), appInBlendDir.absolutePathString())
                    ))
                } else {
                    logger.log(LangManager.message("log.blender.failed.copy.app", mountPoint.absolutePathString()))
                }
            }
        } catch (e: Exception) {
            logger.log(LangManager.message("log.blender.failed.mount", file.absolutePathString(), e.message ?: ""))
            logger.log(LangManager.message("log.blender.error.during.download", e.message ?: ""))
        } finally {
            logger.log(LangManager.message("log.blender.detaching", mountPoint.absolutePathString()))
            executeExtractionCommands(listOf(
                GeneralCommandLine("hdiutil", "detach", mountPoint.absolutePathString(), "-force"),
                GeneralCommandLine("rm", file.absolutePathString())
            ))
            try {
                Files.deleteIfExists(mountPoint)
            } catch (_: Exception) {}
        }
    }

    private fun executeExtractionCommands(commandLines: List<GeneralCommandLine>, silentFailure: Boolean = false) {
        for (commandLine in commandLines) {
            try {
                val handler = OSProcessHandler(commandLine)
                handler.startNotify()
                while (!handler.waitFor(100)) {
                    ProgressManager.checkCanceled()
                }
                if (handler.exitCode != 0 && !silentFailure) {
                    logger.log("Extraction command failed with exit code ${handler.exitCode}: ${commandLine.commandLineString}")
                    break
                }
            } catch (e: Exception) {
                if (e is com.intellij.openapi.progress.ProcessCanceledException) {
                    logger.log("Extraction cancelled by user")
                    throw e
                }
                if (!silentFailure) {
                    logger.log("Failed to execute extraction command: ${e.message}")
                }
                break
            }
        }
    }

    private fun isSysCompatible(version: String): Boolean {
        // TODO: Implement logic to prevent Apple Macintosh from installing Blender 5.0 and newer
        val versionInt = version.split(".").map { it.toInt() }.toIntArray()
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val isMac = osName.contains("mac")
        val isLegacy = arch.contains("x86_64") || arch.contains("amd64")

        if (isMac) {
            if (versionInt[0] >= 5 && isLegacy) {
                logger.log("Blender 5.0 and newer are not compatible with Apple Macintosh")
                return false
            }
            return true
        }
        return true
    }

    companion object {
        fun getInstance(project: Project): BlenderDownloader = project.getService(BlenderDownloader::class.java)
    }
}
