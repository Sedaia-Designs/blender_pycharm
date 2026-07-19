/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.core

import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.util.SysInfo
import com.sakurasedaia.blenderdevelopment.util.SystemHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.PluginConfig.BlendInstallInfo
import com.sakurasedaia.blenderdevelopment.process.ExternalProcessBuilder
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import kotlin.io.path.listDirectoryEntries

/** Project service that discovers Blender installations and updates plugin cache state. */
@Service(Service.Level.PROJECT)
class BlenderInstallationScanner(val project: Project) {
  val logger = PluginLogger.Companion.getInstance(project)
  private val notification = NotificationModal.getInstance(project)

  private data class ScanDiagnostics(
    var inaccessibleRoots: Int = 0,
    var versionProbeFailures: Int = 0,
  )

  /** Scans known OS-specific install locations and refreshes detected Blender installations cache. */
  fun refreshInstalledVersionsCache() {
    val systemInfo: SysInfo = SystemHelper.getSysInfo
    val diagnostics = ScanDiagnostics()
    val installedVersions = mutableListOf<BlendInstallInfo>()
    
    when (systemInfo.osName) {
      "windows" -> installedVersions.addAll(getWindowsBlenderInstalls(diagnostics))
      "macos" -> installedVersions.addAll(getMacBlenderInstalls(diagnostics))
      "linux" -> installedVersions.addAll(getLinuxBlenderInstalls(diagnostics))
      else -> notification.sendError(MessageBundle.message("notification.settings.scan.unsupported.os", systemInfo.osName))
    }
    
    PluginConfig.getInstance().setDetectedBlenderInstalls(installedVersions)
    notifyCriticalScanFeedback(installedVersions.size, diagnostics)
  }
  
  private fun getBlenderVersion(binary: File, diagnostics: ScanDiagnostics, internalBinary: String? = null): String? {
    val result = ExternalProcessBuilder(project).launchAndCaptureOutput(
      command = binary.absolutePath,
      args = arrayOf("--version"),
      internalBinary = internalBinary,
    )
    if (result.cancelled) {
      logger.debug("Version probe cancelled for `${binary.absolutePath}`")
      return null
    }
    if (result.failure != null) {
      diagnostics.versionProbeFailures += 1
      logger.warn("Version probe failed for `${binary.absolutePath}`", result.failure)
      return null
    }
    if (result.exitCode != 0) {
      logger.debug("Version probe exited with code ${result.exitCode} for `${binary.absolutePath}`")
      return null
    }

    val firstLine = result.firstLine.trim()
    if (firstLine.isBlank()) {
      logger.debug("Version probe returned empty output for `${binary.absolutePath}`")
      return null
    }
    return firstLine
  }

  private fun buildInstallInfo(binary: File, installPath: String, diagnostics: ScanDiagnostics, internalBinary: String? = null, whereIsInstall: String = "User"): BlendInstallInfo? {
    val detectedVersion = getBlenderVersion(binary, diagnostics, internalBinary) ?: return null
    return BlendInstallInfo(
      name = "$detectedVersion ($whereIsInstall)",
      version = formSemanticVersion(detectedVersion),
      path = installPath,
    )
  }

  private inline fun listDirectoryEntriesSafely(path: Path, onFailure: () -> Unit, consume: (Path) -> Unit) {
    try {
      path.listDirectoryEntries().forEach(consume)
    } catch (_: AccessDeniedException) {
      onFailure()
    } catch (_: NoSuchFileException) {
      onFailure()
    } catch (_: IOException) {
      onFailure()
    }
  }

  private fun logNoInstallsSummary(osName: String, installsFound: Int, skippedInaccessibleRoots: Int) {
    if (installsFound > 0) return
    if (skippedInaccessibleRoots > 0) {
      logger.warn("No Blender installations detected on $osName. Skipped $skippedInaccessibleRoots inaccessible install root(s).")
    } else {
      logger.warn("No Blender installations detected on $osName.")
    }
  }

  /**
   * Simple discovery logic for Blender Installs on Windows, does not attempt to locate
   * Portable Installs due to increased complexity, and user can set their own paths
   */
  private fun getWindowsBlenderInstalls(diagnostics: ScanDiagnostics): List<BlendInstallInfo> {
    val blenderInstalls = mutableListOf<BlendInstallInfo>()
    val blenderProgramFiles: Path = Path.of("Blender Foundation", "Blender")
    // Default Install location of all Blender Apps
    val programFiles: List<Path> = listOf(
      Path.of(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"),
      Path.of(System.getenv("ProgramFiles") ?: "C:\\Program Files"),
    ).distinct()
    
    
    programFiles.forEach { path ->
      val blenderInstallPath = path.resolve(blenderProgramFiles)
      if (blenderInstallPath.toFile().isDirectory()) {
        listDirectoryEntriesSafely(blenderInstallPath, onFailure = { diagnostics.inaccessibleRoots += 1 }) { version ->
          if (!version.toFile().isDirectory) return@listDirectoryEntriesSafely

          val blenderExecutable = version.resolve("blender.exe").toFile()
          if (!isExecutableFile(blenderExecutable)) return@listDirectoryEntriesSafely

          buildInstallInfo(blenderExecutable, version.toString(), diagnostics)?.let { install ->
            blenderInstalls.add(install)
          }
        }
      }
    }
    
    return blenderInstalls
  }
  
  /**
   * Simple discovery logic for Blender Installs on MacOS, does not attempt to locate
   * portable installations due to increased complexity, and user can set their own paths.
   */
  private fun getMacBlenderInstalls(diagnostics: ScanDiagnostics): List<BlendInstallInfo> {
    val blenderInstalls = mutableListOf<BlendInstallInfo>()
    
    val blenderBinaryRelative = "Contents/MacOS/Blender"
    
    // Default Install location of all Blender Apps, checks system level first since that's what Blender links in their DMG installer
    val applicationDirectories: List<Path> = listOf(
      Path.of("/Applications"),
      Path.of(System.getProperty("user.home"), "Applications"),
    ).distinct()
    applicationDirectories.forEach { appDir ->
      if (!appDir.toFile().isDirectory) return@forEach

      listDirectoryEntriesSafely(appDir, onFailure = { diagnostics.inaccessibleRoots += 1 }) { appBundlePath ->
        val appBundle = appBundlePath.toFile()
        if (!appBundle.isDirectory) return@listDirectoryEntriesSafely
        if (!appBundle.name.startsWith("Blender", ignoreCase = true) || !appBundle.name.endsWith(".app", ignoreCase = true)) {
          return@listDirectoryEntriesSafely
        }

        val blenderBinary = appBundlePath.resolve(blenderBinaryRelative).toFile()
        if (!isExecutableFile(blenderBinary)) return@listDirectoryEntriesSafely

        buildInstallInfo(appBundle, appBundle.absolutePath, diagnostics, internalBinary = "Blender")?.let { install ->
          blenderInstalls.add(install)
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
  private fun getLinuxBlenderInstalls(diagnostics: ScanDiagnostics): List<BlendInstallInfo> {
    val blenderInstalls = linkedSetOf<BlendInstallInfo>()

    // Fast path: prefer shell discovery first to respect current PATH precedence.
    resolveBinaryPathWithWhich()?.let {
      buildInstallInfo(Path.of(it).toFile(), it, diagnostics)?.let { install ->
        blenderInstalls.add(install)
      }
    }

    // Fallbacks for package managers (apt/pacman/yum) and Homebrew on Linux.
    val explicitBinaryPaths = listOf(
      Path.of("/usr/bin/blender"),
      Path.of("/usr/local/bin/blender"),
      Path.of("/usr/lib/blender/blender"),
      Path.of("/usr/lib64/blender/blender"),
    )
    explicitBinaryPaths.forEach { candidate ->
      val candidateFile = candidate.toFile()
      if (isExecutableFile(candidateFile)) {
        buildInstallInfo(candidateFile, candidateFile.absolutePath, diagnostics)?.let { install ->
          blenderInstalls.add(install)
        }
      }
    }

    val brewPrefixes = listOfNotNull(
      System.getenv("HOMEBREW_PREFIX")?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
      Path.of("/home/linuxbrew/.linuxbrew"),
      Path.of("/linuxbrew/.linuxbrew"),
    ).distinct()

    brewPrefixes.forEach { prefix ->
      val linkedBinary = prefix.resolve("bin").resolve("blender-runtime").toFile()
      if (isExecutableFile(linkedBinary)) {
        buildInstallInfo(linkedBinary, linkedBinary.absolutePath, diagnostics)?.let { install ->
          blenderInstalls.add(install)
        }
      }

      val blenderCellar = prefix.resolve("Cellar").resolve("blender-runtime")
      if (blenderCellar.toFile().isDirectory) {
        listDirectoryEntriesSafely(blenderCellar, onFailure = { diagnostics.inaccessibleRoots += 1 }) { versionPath ->
          if (!versionPath.toFile().isDirectory) return@listDirectoryEntriesSafely
          val cellarBinary = versionPath.resolve("bin").resolve("blender-runtime").toFile()
          if (!isExecutableFile(cellarBinary)) return@listDirectoryEntriesSafely

          buildInstallInfo(cellarBinary, cellarBinary.absolutePath, diagnostics)?.let { install ->
            blenderInstalls.add(install)
          }
        }
      }
    }

    return blenderInstalls.toList()
  }

  private fun notifyCriticalScanFeedback(installsFound: Int, diagnostics: ScanDiagnostics) {
    if (installsFound > 0) return
    if (diagnostics.versionProbeFailures > 0) {
      notification.sendWarning(
        MessageBundle.message(
          "notification.settings.scan.completed.critical.failures",
          diagnostics.versionProbeFailures.toString(),
          diagnostics.inaccessibleRoots.toString(),
        )
      )
      return
    }
    if (diagnostics.inaccessibleRoots > 0) {
      notification.sendWarning(
        MessageBundle.message(
          "notification.settings.scan.completed.partial.access",
          diagnostics.inaccessibleRoots.toString(),
        )
      )
    }
  }
  
  private fun resolveBinaryPathWithWhich(): String? {
    val result = ExternalProcessBuilder(project).launchAndCaptureOutput("which", "blender-runtime")
    if (result.cancelled || result.failure != null || result.exitCode != 0) return null

    val locatedPath = result.firstLine.trim()
    return locatedPath.takeIf { it.isNotBlank() }?.let {
      val binary = Path.of(it).toFile()
      if (isExecutableFile(binary)) binary.absolutePath else null
    }
  }

  private fun isExecutableFile(file: File): Boolean = file.exists() && file.isFile && file.canExecute()
  
  /**
   * Extracts the semantic version from the Blender version string, returned 
   * from `blender --version`, which usually is formatted "Blender X.X.X"
   * Function attempts a simple extraction of the semantic version from the 
   * Blender version string, before falling back to more complex extraction methods.
   * 
   * @param commandOutput Blender version string from `blender --version`
   */
  private fun formSemanticVersion(commandOutput: String): String {
    try {
      return commandOutput.split(" ")[1]
    } catch (e: Exception) {
      val firstLine = commandOutput.lineSequence().firstOrNull().orEmpty().trim()
      
      // Preferred: full semver (e.g. 4.2.1)
      Regex("""\b(\d+\.\d+\.\d+)\b""").find(firstLine)?.let { return it.groupValues[1] }
      
      // Fallback: major.minor (e.g. 4.2) -> normalize to semver
      Regex("""\b(\d+\.\d+)\b""").find(firstLine)?.let { return "${it.groupValues[1]}.0" }
      
      // Last resort: extract first 2-3 numeric chunks and build semver
      val nums = Regex("""\d+""").findAll(firstLine).map { it.value }.toList()
      return when {
        nums.size >= 3 -> "${nums[0]}.${nums[1]}.${nums[2]}"
        nums.size == 2 -> "${nums[0]}.${nums[1]}.0"
        else -> ""
      }
    }
  }
}