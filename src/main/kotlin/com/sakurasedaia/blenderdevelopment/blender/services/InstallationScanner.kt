package com.sakurasedaia.blenderdevelopment.blender.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.model.SysInfo
import com.sakurasedaia.blenderdevelopment.model.SystemHelper
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

/**
 * A collection of helper methods for working with [com.sakurasedaia.blenderdevelopment.config.ProjectConfig] and [com.sakurasedaia.blenderdevelopment.config.PluginConfig].
 */
@Service(Service.Level.PROJECT)
class InstallationScanner(val project: Project) {
  val logger = PluginLogger.getInstance(project)
  
  /**
   * Returns a list of installed Blender versions, both from the system and from the plugin.
   */
  fun getInstalledBlenderVersions(): List<String> {
    val systemInfo: SysInfo = SystemHelper.getSysInfo
    val installedVersions = mutableListOf<String>()
    
    when (systemInfo.osName) {
      "windows" -> installedVersions.addAll(getWindowsBlenderInstalls())
      "linux" -> installedVersions.addAll(getLinuxBlenderInstalls())
      "macos" -> installedVersions.addAll(getMacBlenderInstalls())
    }
    
    return installedVersions
  }
  
  /**
   * Simple discovery logic for Blender Installs on Windows, does not attempt to locate
   * Portable Installs due to increased complexity, and user can set their own paths
   */
  private fun getWindowsBlenderInstalls(): List<String> {
    val blenderInstalls = mutableListOf<String>()
    val blenderProgramFiles: Path = Path.of("Blender Foundation", "Blender")
    // Default Install location of all Blender Apps
    val programFiles: List<Path> = listOf(
      Path.of(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"),
      Path.of(System.getenv("ProgramFiles") ?: "C:\\Program Files"),
    ).distinct()
    
    
    programFiles.forEach { path ->
      val blenderInstallPath = path.resolve(blenderProgramFiles)
      if (blenderInstallPath.toFile().isDirectory()) {
        blenderInstallPath.listDirectoryEntries().forEach { version ->
          val versionFile = version.toFile()
          if (versionFile.isDirectory()) {
            if (versionFile.listFiles().orEmpty().any { it.name.equals("blender.exe", ignoreCase = true) }) {
              blenderInstalls.add(version.toString())
            }
          }
        }
      }
    }
    
    
    logger.warn("getWindowsBlenderInstalls() not yet implemented")
    return blenderInstalls
  }
  
  /**
   * Simple discovery logic for Blender Installs on MacOS, does not attempt to locate
   * portable installations due to increased complexity, and user can set their own paths.
   */
  private fun getMacBlenderInstalls(): List<String> {
    val blenderInstalls = mutableListOf<String>()
    
    val blenderBinaryRelative = "Contents/MacOS/Blender"
    
    // Default Install location of all Blender Apps, checks system level first since that's what Blender links in their DMG installer
    val applicationDirectories: List<Path> = listOf(
      Path.of("/Applications"),
      Path.of(System.getProperty("user.home"), "Applications"),
    ).distinct()
    applicationDirectories.forEach { appDir ->
      val dir = appDir.toFile()
      if (!dir.isDirectory) return@forEach
      
      dir.listFiles().orEmpty()
        .asSequence()
        .filter { it.isDirectory && it.name.startsWith("Blender", ignoreCase = true) && it.name.endsWith(".app", ignoreCase = true) }
        .forEach { appBundle ->
          val blenderBinary = appBundle.toPath().resolve(blenderBinaryRelative).toFile()
          if (blenderBinary.exists() && blenderBinary.canExecute()) {
            blenderInstalls.add(appBundle.absolutePath)
          }
        }
    }
    
    return blenderInstalls.distinct()
  }
  
  /**
   * Blender discovery logic for Blender Installs on Linux, does not attempt to locate
   * portable installations due to increased complexity, and user can set their own paths.
   *
   * Scans common Linux package managers and Linux Homebrew installations.
   */
  private fun getLinuxBlenderInstalls(): List<String> {
    val blenderInstalls = linkedSetOf<String>()

    // Fast path: prefer shell discovery first to respect current PATH precedence.
    resolveBinaryPathWithWhich()?.let { blenderInstalls.add(it) }

    // Fallbacks for package managers (apt/pacman/yum) and Homebrew on Linux.
    val explicitBinaryPaths = listOf(
      Path.of("/usr/bin/blender"),
      Path.of("/usr/local/bin/blender"),
      Path.of("/usr/lib/blender/blender"),
      Path.of("/usr/lib64/blender/blender"),
    )
    explicitBinaryPaths.forEach { candidate ->
      if (isExecutableFile(candidate.toFile())) {
        blenderInstalls.add(candidate.toString())
      }
    }

    val brewPrefixes = listOfNotNull(
      System.getenv("HOMEBREW_PREFIX")?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
      Path.of("/home/linuxbrew/.linuxbrew"),
      Path.of("/linuxbrew/.linuxbrew"),
    ).distinct()

    brewPrefixes.forEach { prefix ->
      val linkedBinary = prefix.resolve("bin").resolve("blender").toFile()
      if (isExecutableFile(linkedBinary)) {
        blenderInstalls.add(linkedBinary.absolutePath)
      }

      val blenderCellar = prefix.resolve("Cellar").resolve("blender").toFile()
      if (blenderCellar.isDirectory) {
        blenderCellar.listFiles().orEmpty()
          .asSequence()
          .filter { it.isDirectory }
          .map { it.toPath().resolve("bin").resolve("blender").toFile() }
          .filter { isExecutableFile(it) }
          .forEach { blenderInstalls.add(it.absolutePath) }
      }
    }

    return blenderInstalls.toList()
  }

  private fun resolveBinaryPathWithWhich(): String? {
    return try {
      val process = ProcessBuilder("which", "blender").start()
      val output = process.inputStream.bufferedReader().use { it.readText().trim() }
      process.waitFor()
      output.takeIf { it.isNotBlank() }?.let { locatedPath ->
        val binary = Path.of(locatedPath).toFile()
        if (isExecutableFile(binary)) binary.absolutePath else null
      }
    } catch (exception: IOException) {
      logger.warn("Failed to execute `which blender` while scanning Blender binaries", exception)
      null
    } catch (exception: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.warn("Interrupted while scanning Blender binaries with `which blender`", exception)
      null
    } catch (exception: Exception) {
      logger.warn("Unexpected error while scanning Blender binaries with `which blender`", exception)
      null
    }
  }

  private fun isExecutableFile(file: File): Boolean = file.exists() && file.isFile && file.canExecute()

}
