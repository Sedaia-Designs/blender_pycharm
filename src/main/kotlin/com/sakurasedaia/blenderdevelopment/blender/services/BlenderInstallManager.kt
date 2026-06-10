package com.sakurasedaia.blenderdevelopment.blender.services

import com.intellij.openapi.project.Project
import com.intellij.util.io.Decompressor
import com.sakurasedaia.blenderdevelopment.config.*
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.logging.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.InstallProgressMonitor
import com.sakurasedaia.blenderdevelopment.model.SystemHelper
import com.sakurasedaia.blenderdevelopment.system.ExternalProcessUtil
import kotlinx.coroutines.runBlocking
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Manages the downloading, installation, and execution of Blender.
 */
class BlenderInstallManager(private val project: Project) {
	private val logger = PluginLogger.getInstance(project)
	private val notifications = NotificationModal.getInstance(project)
	private val progressMonitor = InstallProgressMonitor.getInstance(project)
	private val pluginState = BlenderPluginConfig.getInstance().state
	private val projectState = BlenderProjectConfig.getInstance(project).state
	private val processUtil = ExternalProcessUtil(project)
	/**
	 * Resolves the download URL for a given Blender version and platform.
	 *
	 * @param version Blender version string.
	 * @param platform Target platform (e.g., "windows", "linux", "macos").
	 * @param arch Target architecture (e.g., "x64", "arm64").
	 */
	private fun resolveDownloadUrl(version: String = "5.1.0", platform: String = "macos", arch: String = "arm64"): String {
		// Splits the Semver for the purpose of path discovery
		val majorMinor = version.split(".").take(2).joinToString(".")
		val fileExtension = SystemHelper.getSystemBundleExtension(platform)
		if (fileExtension == "unknown") {
			logger.warn("Cannot resolve Blender download URL for unsupported platform '$platform'")
			notifications.sendWarning("Unsupported platform '$platform' for Blender downloads.")
			return ""
		}
		return "https://download.blender.org/release/Blender$majorMinor/blender-$version-$platform-$arch.$fileExtension"
	}

	private fun resolveDownloadPath(version: String, platform: String, arch: String): String {
		val fileExtension = SystemHelper.getSystemBundleExtension(platform)
		return "${System.getProperty("java.io.tmpdir")}/blender-$version-$platform-$arch.$fileExtension"
	}
	
	/**
	 * The Public facing Async wrapper for the BlenderDownloader
	 *
	 * @param version Blender version string.
	 * @param platform Target platform (e.g., "windows", "linux", "macos").
	 * @param arch Target architecture (e.g., "x64", "arm64").
	 */
	fun downloadBlender(version: String, platform: String, arch: String) {
		progressMonitor.runTask("Download Blender $version") { progress ->
			downloadBlenderSync(version, platform, arch, progress)
		}
	}
	
	/**
	 * Downloads the given Blender version to the user's temporary directory.
	 *
	 * @param version Blender version string.
	 * @param platform Target platform (e.g., "windows", "linux", "macos").
	 * @param arch Target architecture (e.g., "x64", "arm64").
	 * @param progress Progress monitor for tracking download progress.
	 */
	private fun downloadBlenderSync(
		version: String,
		platform: String,
		arch: String,
		progress: InstallProgressMonitor.ProgressHandle,
	) {
		progress.text("Preparing Blender download")
		progress.details("Resolving download URL")
		progress.fraction(0.1)
		val downloadUrl = resolveDownloadUrl(version, platform, arch)
		if (downloadUrl.isEmpty()) {
			progress.details("Cannot resolve download URL")
			return
		}
		
		val downloadPath = resolveDownloadPath(version, platform, arch)
		progress.details("Preparing local destination")
		progress.fraction(0.25)
		progress.checkCanceled()
		logger.log("Resolved download URL: $downloadUrl")
		
		// TODO: Download archive to `downloadPath` before installation.
		try {
			// Use Intellij Download utility to download the appropriate file
		} catch (t: Throwable) {
			logger.error(errorType = ErrorTypes.DOWNLOAD_ERROR, t)
			notifications.sendError("Failed to download Blender: ${t.message}")
			return
		}
		
		progress.details("Handing off to installer")
		progress.fraction(0.5)
		installBlenderSync(downloadPath, progress = progress)
		progress.fraction(1.0)
	}
	
	/**
	 * Installs the given Blender version to it's appropriate location based on the user's preferences and Blender Package provided using Intellij's Decompressor utility.
	 *
	 * @param path Path to the Blender bundle file.
	 */
	fun installBlender(path: String) {
		progressMonitor.runTask("Install Blender") { progress ->
			installBlenderSync(path, progress)
		}
	}
	
	/**
	 * Core logic utility for installing Blender, called by the installBlender() method and makes the necessary preparations and checks for installation.
	 *
	 * @param path Path to the Blender bundle file.
	 * @param progress Progress handle for tracking the installation progress.
	 */
	private fun installBlenderSync(
		path: String,
		progress: InstallProgressMonitor.ProgressHandle,
	) {
		progress.text("Installing Blender")
		progress.details("Validating installer bundle")
		progress.indeterminate(true)
		progress.checkCanceled()
		logger.log("Blender install requested: path=$path")
		
		if (Path.of(path).toFile().isDirectory) {
			notifications.sendWarning("Blender install requested for a directory, not an installer bundle. Please provide a valid installer bundle.")
			return
		}
		
		val bundle = Path.of(path)
		val outputDir = Path.of(pluginState.blenderInstallPath)
		val name = bundle.fileName.toString().lowercase()
		
		progress.details("Extracting Blender bundle")
		progress.checkCanceled()
		
		when {
			name.endsWith(".zip") -> {
				logger.log("Detected ZIP bundle: $name, extracting to: $outputDir")
				extractZipBundle(bundle, outputDir)
			}
			name.endsWith(".tar.xz") -> {
				logger.log("Detected TAR.XZ bundle: $name, extracting to: $outputDir")
				extractTarBundle(bundle, outputDir)
			}
			name.endsWith(".dmg") -> {
				logger.log("Detected DMG bundle: $name, extracting to: $outputDir")
				extractDmgBundle(bundle, outputDir)
			}
			else -> {
				logger.error(ErrorTypes.ARCHIVE_FORMAT_UNSUPPORTED)
				notifications.sendWarning("Blender installer format not supported: $name")
			}
		}
		
		if (pluginState.clearDownloadsAfterInstall) {
			val downloadedBundle = Path.of(path)
			progress.details("Cleaning downloaded installer bundle")
			progress.checkCanceled()
			try {
				if (Files.deleteIfExists(downloadedBundle)) {
					logger.log("Deleted downloaded Blender bundle: $path")
				} else {
					logger.log("No downloaded Blender bundle found to delete at: $path")
				}
			} catch (t: Throwable) {
				logger.warn("Failed to delete downloaded Blender bundle: $path", t)
				notifications.sendWarning("Blender installed, but failed to delete downloaded bundle at '$path'.")
			}
		}
	}
	
	/**
	 * Extracts the given Zip bundle to the given output directory.
	 *
	 * @param bundle Path to the Zip bundle.
	 * @param outputDir Path to the output directory.
	 */
	private fun extractZipBundle(bundle: Path, outputDir: Path) {
		try {
			Decompressor.Zip(bundle).overwrite(true).extract(outputDir)
			
			val zipName = bundle.fileName.toString()
			val oldDirName = Path.of(outputDir.toString(), zipName.removeSuffix(".zip"))
			val newDirName = Path.of(outputDir.toString(), zipName.split("-")[1])
			
			if (oldDirName.toFile().exists()) Files.deleteIfExists(oldDirName)
			
			Files.move(oldDirName, newDirName)
			
			logger.log("Successfully extracted Blender bundle to: $outputDir")
			notifications.sendInfo("Successfully extracted Blender bundle to: $outputDir")
		} catch (t: Throwable) {
			logger.error(ErrorTypes.ARCHIVE_FORMAT_UNSUPPORTED, t)
			notifications.sendError("Failed to extract Zip Bundle: ${t.message}")
		}
	}
	
	/**
	 * Extracts the given Tarball bundle to the given output directory.
	 *
	 * @param bundle Path to the Tarball bundle.
	 * @param outputDir Path to the output directory.
	 */
	private fun extractTarBundle(bundle: Path, outputDir: Path) {
		try {
			Decompressor.Tar(bundle).overwrite(true).extract(outputDir)
			
			val tarName = bundle.fileName.toString()
			val oldDirName = Path.of(outputDir.toString(), tarName.removeSuffix(".tar.xz"))
			val newDirName = Path.of(outputDir.toString(), tarName.split("-")[1])
			
			if (oldDirName.toFile().exists()) Files.deleteIfExists(oldDirName)
			
			Files.move(oldDirName, newDirName)
			
			logger.log("Successfully extracted Blender bundle to: $outputDir")
			notifications.sendInfo("Successfully extracted Blender bundle to: $outputDir")
		} catch (t: Throwable) {
			logger.error(ErrorTypes.ARCHIVE_FORMAT_UNSUPPORTED, t)
			notifications.sendError("Failed to extract Tarball Bundle: ${t.message}")
		}
	}
	
	/**
	 * Extracts the given DMG bundle to the given output directory.
	 *
	 * @param bundle Path to the DMG bundle.
	 * @param outputDir Path to the output directory.
	 */
	private fun extractDmgBundle(bundle: Path, outputDir: Path) {
		try {
			val dmgName = bundle.fileName.toString()
			val version = dmgName.split("-")[1]
			val finalAppName = "Blender $version"
			val finalAppBundlePath = Path.of(outputDir.toString(), "$finalAppName.app")
			
			val mountPoint = Path.of("/Volumes", "Blender")
			val mountedBundle = Path.of(mountPoint.toString(), "Blender.app")
			
			var primaryFailure: Throwable? = null
			
			try {
				// 1. Check if Blender is already mounted at the canonical mountpoint, force-detach first if it is.
				when {
					isDmgMountedAt(mountPoint, outputDir) -> {
						runBlocking {
							processUtil.runExternalToolAsync(
								executable = "hdiutil",
								arguments = listOf("detach", "-force", mountPoint.toString()),
								workingDir = outputDir.toString(),)
						}
					}
					Files.exists(mountPoint) -> { error("Mount point '$mountPoint' exists but is not a mounted DMG. Refusing to force detach.") }
				}
				
				// 2. Mount the new DMG.
				runBlocking {
					processUtil.runExternalToolAsync(
						executable = "hdiutil",
						arguments = listOf("attach", bundle.toString(), "-nobrowse", "-mountpoint", mountPoint.toString()),
						workingDir = outputDir.toString(),
					)
				}
				
				// 3. Copy Blender.app recursively into the final versioned app bundle path.
				if (Files.exists(finalAppBundlePath)) {
					deleteDirectoryRecursively(finalAppBundlePath)
				}
				copyDirectoryRecursively(mountedBundle, finalAppBundlePath)
				
			} catch (t: Throwable) {
				primaryFailure = t
				throw t
			} finally {
				// 4. Unmount the DMG without masking the original extraction failure.
				try {
					runBlocking {
						processUtil.runExternalToolAsync(
							executable = "hdiutil",
							arguments = listOf("detach", mountPoint.toString()),
							workingDir = outputDir.toString(),
						)
					}
				} catch (detachFailure: Throwable) {
					if (primaryFailure != null) {
						primaryFailure.addSuppressed(detachFailure)
					} else {
						throw detachFailure
					}
				}
			}
			
		} catch (t: Throwable) {
			logger.error(ErrorTypes.DMG_EXTRACTION_FAILED, t)
			notifications.sendError("Failed to extract DMG Bundle: ${t.message}")
		}
	}
	
	private fun isDmgMountedAt(mountPoint: Path, workingDir: Path): Boolean {
		val result = runBlocking {
			processUtil.runExternalToolAndCaptureAsync(
				executable = "hdiutil",
				arguments = listOf("info"),
				workingDir = workingDir.toString(),
			)
		}
		if (result.exitCode != 0) return false
		
		var currentImageIsDmg = false
		for (line in result.stdout.lineSequence()) {
			val trimmed = line.trim()
			if (trimmed.startsWith("image-path", ignoreCase = true)) {
				val imagePath = trimmed.substringAfter(':', "").trim()
				currentImageIsDmg = imagePath.lowercase().endsWith(".dmg")
				continue
			}
			if (trimmed.startsWith("mount-point", ignoreCase = true)) {
				val foundMountPoint = trimmed.substringAfter(':', "").trim()
				if (currentImageIsDmg && foundMountPoint == mountPoint.toString()) {
					return true
				}
			}
		}
		return false
	}
	
	private fun copyDirectoryRecursively(sourceDir: Path, targetDir: Path) {
		require(Files.isDirectory(sourceDir)) { "Source is not a directory: $sourceDir" }
		
		Files.walkFileTree(sourceDir, object : SimpleFileVisitor<Path>() {
			override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
				val relativePath = sourceDir.relativize(dir)
				val destination = targetDir.resolve(relativePath)
				Files.createDirectories(destination)
				return FileVisitResult.CONTINUE
			}
			
			override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
				val relativePath = sourceDir.relativize(file)
				val destination = targetDir.resolve(relativePath)
				Files.copy(
					file,
					destination,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.COPY_ATTRIBUTES,
				)
				return FileVisitResult.CONTINUE
			}
		})
	}
	
	private fun deleteDirectoryRecursively(path: Path) {
		Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
			override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
				Files.deleteIfExists(file)
				return FileVisitResult.CONTINUE
			}
			
			override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
				if (exc != null) throw exc
				Files.deleteIfExists(dir)
				return FileVisitResult.CONTINUE
			}
		})
	}
}
