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

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.process.ExternalProcessBuilder
import com.sakurasedaia.blenderdevelopment.state.InstallType
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.PluginConfig.BlendInstallInfo
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.io.path.listDirectoryEntries

/** Project service that discovers Blender installations and updates plugin cache state. */
@Service(Service.Level.PROJECT)
class BlenderInstallationScanner(val project: Project) {
    val logger = PluginLogger.getInstance(project)
    private val notification = NotificationModal.getInstance(project)

    private data class ScanDiagnostics(
        var inaccessibleRoots: Int = 0,
        var versionProbeFailures: Int = 0,
    )

    /**
     * Scans known OS-specific install locations and refreshes detected Blender installations cache.
     *
     * @param shouldCancel callback checked during discovery and process execution.
     */
    fun refreshInstalledVersionsCache(shouldCancel: () -> Boolean = { false }) {

        ensureActive(shouldCancel)
        val systemInfo: SystemInfo.Format = SystemInfo()
        val diagnostics = ScanDiagnostics()
        val installedVersions = linkedMapOf<String, BlendInstallInfo>()

        when (systemInfo.osName) {
            "windows" -> getWindowsBlenderInstalls(diagnostics, shouldCancel).forEach { installedVersions.putIfAbsent(it.path, it) }
            "macos" -> getMacBlenderInstalls(diagnostics, shouldCancel).forEach { installedVersions.putIfAbsent(it.path, it) }
            "linux" -> getLinuxBlenderInstalls(diagnostics, shouldCancel).forEach { installedVersions.putIfAbsent(it.path, it) }
            else -> notification.sendError(MessageBundle.message("notification.settings.scan.unsupported.os", systemInfo.osName))
        }

        getConfiguredRootBlenderInstalls(diagnostics, shouldCancel).forEach { installedVersions.putIfAbsent(it.path, it) }

        ensureActive(shouldCancel)
        PluginConfig.getInstance().setDetectedBlenderInstalls(installedVersions.values.toList())
        notifyCriticalScanFeedback(installedVersions.size, diagnostics)
    }

    private fun getBlenderVersion(
        binary: File,
        diagnostics: ScanDiagnostics,
        shouldCancel: () -> Boolean,
        internalBinary: String? = null,
    ): String? {
        ensureActive(shouldCancel)
        val result =
            ExternalProcessBuilder(project)
                .launchAndCaptureOutput(
                    command = binary.absolutePath,
                    args = arrayOf("--version"),
                    shouldCancel = shouldCancel,
                    internalBinary = internalBinary,
                    timeout = Duration.ofSeconds(10),
                )
        if (result.cancelled) {
            logger.debug("Version probe cancelled for `${binary.absolutePath}`")
            return null
        }
        if (result.failure != null) {
            diagnostics.versionProbeFailures += 1
            logger.warn(ErrorTypes.INSTALL_VERSION_PROBE_FAILED.format(binary.absolutePath), result.failure)
            return null
        }
        if (result.timedOut) {
            logger.debug("Version probe timed out for `${binary.absolutePath}`")
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

    private fun buildInstallInfo(
        binary: File,
        installPath: String,
        diagnostics: ScanDiagnostics,
        shouldCancel: () -> Boolean,
        internalBinary: String? = null,
        installType: InstallType = InstallType.USER,
    ): BlendInstallInfo? {
        val detectedVersion = getBlenderVersion(binary, diagnostics, shouldCancel, internalBinary) ?: return null
        val installTypeLabel =
            when (installType) {
                InstallType.USER -> "User"
                InstallType.PYCHARM -> "Custom"
            }
        return BlendInstallInfo(
            name = "$detectedVersion ($installTypeLabel)",
            version = formSemanticVersion(detectedVersion),
            path = installPath,
            installType = installType,
        )
    }

    private fun listDirectoryEntriesSafely(path: Path, shouldCancel: () -> Boolean, onFailure: () -> Unit, consume: (Path) -> Unit) {
        try {
            path.listDirectoryEntries().forEach { entry ->
                ensureActive(shouldCancel)
                consume(entry)
            }
        } catch (_: AccessDeniedException) {
            onFailure()
        } catch (_: NoSuchFileException) {
            onFailure()
        } catch (_: IOException) {
            onFailure()
        }
    }

    /**
     * Scans default installation directories for Blender on Windows OS and attempts to locate installed versions.
     *
     * @param diagnostics Tracks diagnostic information during the scanning process, including inaccessible paths and version probe
     *   failures.
     * @param shouldCancel A callback function that, when invoked, checks if the scanning process should be cancelled.
     * @return A list of discovered Blender installations, where each installation is represented by a `BlendInstallInfo` object containing
     *   details like name, version, and path. Returns an empty list if no installations are found.
     */
    private fun getWindowsBlenderInstalls(
        diagnostics: ScanDiagnostics,
        shouldCancel: () -> Boolean,
    ): List<BlendInstallInfo> {

        val blenderInstalls = mutableListOf<BlendInstallInfo>()
        val blenderProgramFiles: Path = Path.of("Blender Foundation", "Blender")
        // Default Install location of all Blender Apps
        val programFiles: List<Path> =
            listOf(
                    Path.of(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"),
                    Path.of(System.getenv("ProgramFiles") ?: "C:\\Program Files"),
                )
                .distinct()

        programFiles.forEach { path ->
            ensureActive(shouldCancel)
            val blenderInstallPath = path.resolve(blenderProgramFiles)
            if (blenderInstallPath.toFile().isDirectory()) {
                listDirectoryEntriesSafely(blenderInstallPath, shouldCancel, onFailure = { diagnostics.inaccessibleRoots += 1 }) { version
                    ->
                    if (!version.toFile().isDirectory) return@listDirectoryEntriesSafely

                    val blenderExecutable = version.resolve("blender.exe").toFile()
                    if (!isExecutableFile(blenderExecutable)) return@listDirectoryEntriesSafely

                    buildInstallInfo(blenderExecutable, version.toString(), diagnostics, shouldCancel)?.let { install ->
                        blenderInstalls.add(install)
                    }
                }
            }
        }

        return blenderInstalls
    }

    /**
     * Simple discovery logic for Blender Installs on macOS, does not attempt to locate portable installations due to increased complexity,
     * and user can set their own paths.
     */
    private fun getMacBlenderInstalls(diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): List<BlendInstallInfo> {
        val blenderInstalls = mutableListOf<BlendInstallInfo>()

        val blenderBinaryRelative = "Contents/MacOS/Blender"

        // Default Install location of all Blender Apps, checks system level first since that's what Blender links in their DMG installer
        val applicationDirectories: List<Path> =
            listOf(
                    Path.of("/Applications"),
                    Path.of(System.getProperty("user.home"), "Applications"),
                )
                .distinct()
        applicationDirectories.forEach { appDir ->
            ensureActive(shouldCancel)
            if (!appDir.toFile().isDirectory) return@forEach

            listDirectoryEntriesSafely(appDir, shouldCancel, onFailure = { diagnostics.inaccessibleRoots += 1 }) { appBundlePath ->
                val appBundle = appBundlePath.toFile()
                if (!appBundle.isDirectory) return@listDirectoryEntriesSafely
                if (!appBundle.name.startsWith("Blender", ignoreCase = true) || !appBundle.name.endsWith(".app", ignoreCase = true)) {
                    return@listDirectoryEntriesSafely
                }

                val blenderBinary = appBundlePath.resolve(blenderBinaryRelative).toFile()
                if (!isExecutableFile(blenderBinary)) return@listDirectoryEntriesSafely

                buildInstallInfo(appBundle, appBundle.absolutePath, diagnostics, shouldCancel, internalBinary = "Blender")?.let { install ->
                    blenderInstalls.add(install)
                }
            }
        }

        return blenderInstalls.distinct()
    }

    /**
     * Blender discovery logic for Blender Installs on Linux, does not attempt to locate portable installations due to increased complexity,
     * and user can set their own paths.
     *
     * Scans common Linux package managers and Linux Homebrew installations.
     */
    private fun getLinuxBlenderInstalls(diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): List<BlendInstallInfo> {
        val blenderInstalls = linkedSetOf<BlendInstallInfo>()

        // Fast path: prefer shell discovery first to respect current PATH precedence.
        resolveBinaryPathWithWhich(shouldCancel)?.let {
            buildInstallInfo(Path.of(it).toFile(), it, diagnostics, shouldCancel)?.let { install ->
                blenderInstalls.add(install)
            }
        }

        // Fallbacks for package managers (apt/pacman/yum) and Homebrew on Linux.
        val explicitBinaryPaths =
            listOf(
                Path.of("/usr/bin/blender"),
                Path.of("/usr/local/bin/blender"),
                Path.of("/usr/lib/blender/blender"),
                Path.of("/usr/lib64/blender/blender"),
            )
        explicitBinaryPaths.forEach { candidate ->
            ensureActive(shouldCancel)
            val candidateFile = candidate.toFile()
            if (isExecutableFile(candidateFile)) {
                buildInstallInfo(candidateFile, candidateFile.absolutePath, diagnostics, shouldCancel)?.let { install ->
                    blenderInstalls.add(install)
                }
            }
        }

        val brewPrefixes =
            listOfNotNull(
                    System.getenv("HOMEBREW_PREFIX")?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
                    Path.of("/home/linuxbrew/.linuxbrew"),
                    Path.of("/linuxbrew/.linuxbrew"),
                )
                .distinct()

        brewPrefixes.forEach { prefix ->
            ensureActive(shouldCancel)
            val linkedBinary = prefix.resolve("bin").resolve("blender-runtime").toFile()
            if (isExecutableFile(linkedBinary)) {
                buildInstallInfo(linkedBinary, linkedBinary.absolutePath, diagnostics, shouldCancel)?.let { install ->
                    blenderInstalls.add(install)
                }
            }

            val blenderCellar = prefix.resolve("Cellar").resolve("blender-runtime")
            if (blenderCellar.toFile().isDirectory) {
                listDirectoryEntriesSafely(blenderCellar, shouldCancel, onFailure = { diagnostics.inaccessibleRoots += 1 }) { versionPath ->
                    if (!versionPath.toFile().isDirectory) return@listDirectoryEntriesSafely
                    val cellarBinary = versionPath.resolve("bin").resolve("blender-runtime").toFile()
                    if (!isExecutableFile(cellarBinary)) return@listDirectoryEntriesSafely

                    buildInstallInfo(cellarBinary, cellarBinary.absolutePath, diagnostics, shouldCancel)?.let { install ->
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

    private fun getConfiguredRootBlenderInstalls(diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): List<BlendInstallInfo> {
        ensureActive(shouldCancel)
        val rawConfiguredRoot = PluginConfig.getInstance().getBlenderInstallPath().trim()
        if (rawConfiguredRoot.isBlank()) {
            return emptyList()
        }

        val configuredRoot = resolveUserPath(rawConfiguredRoot)
        if (!configuredRoot.toFile().isDirectory) {
            return emptyList()
        }

        val discovered = linkedMapOf<String, BlendInstallInfo>()
        scanInstallCandidate(configuredRoot, diagnostics, shouldCancel)?.let { install ->
            discovered[install.path] = install
        }

        listDirectoryEntriesSafely(configuredRoot, shouldCancel, onFailure = { diagnostics.inaccessibleRoots += 1 }) { entry ->
            if (!entry.toFile().isDirectory) return@listDirectoryEntriesSafely
            scanInstallCandidate(entry, diagnostics, shouldCancel)?.let { install ->
                discovered[install.path] = install
            }
        }

        return discovered.values.toList()
    }

    private fun scanInstallCandidate(candidatePath: Path, diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): BlendInstallInfo? {
        ensureActive(shouldCancel)
        val candidateDir = candidatePath.toFile()

        if (candidateDir.name.endsWith(".app", ignoreCase = true)) {
            val appBinary = candidatePath.resolve("Contents").resolve("MacOS").resolve("Blender").toFile()
            if (isExecutableFile(appBinary)) {
                return buildInstallInfo(
                    candidateDir,
                    candidateDir.absolutePath,
                    diagnostics,
                    shouldCancel,
                    internalBinary = "Blender",
                    installType = InstallType.PYCHARM,
                )
            }
        }

        val directBinaries =
            listOf(
                candidatePath.resolve("blender").toFile(),
                candidatePath.resolve("blender.exe").toFile(),
                candidatePath.resolve("blender-runtime").toFile(),
                candidatePath.resolve("bin").resolve("blender").toFile(),
                candidatePath.resolve("bin").resolve("blender.exe").toFile(),
                candidatePath.resolve("bin").resolve("blender-runtime").toFile(),
            )

        directBinaries
            .firstOrNull { isExecutableFile(it) }
            ?.let { binary ->
                return buildInstallInfo(
                    binary,
                    candidateDir.absolutePath,
                    diagnostics,
                    shouldCancel,
                    installType = InstallType.PYCHARM,
                )
            }

        return null
    }

    private fun resolveUserPath(value: String): Path {
        if (!value.startsWith("~")) {
            return Path.of(value).normalize()
        }
        val home = System.getProperty("user.home")
        val withoutTilde = value.removePrefix("~").removePrefix("/")
        return Path.of(home).resolve(withoutTilde).normalize()
    }

    private fun resolveBinaryPathWithWhich(shouldCancel: () -> Boolean): String? {
        ensureActive(shouldCancel)
        val result = ExternalProcessBuilder(project).launchAndCaptureOutput("which", "blender-runtime", shouldCancel = shouldCancel)
        if (result.cancelled || result.failure != null || result.exitCode != 0) return null

        val locatedPath = result.firstLine.trim()
        return locatedPath
            .takeIf { it.isNotBlank() }
            ?.let {
                val binary = Path.of(it).toFile()
                if (isExecutableFile(binary)) binary.absolutePath else null
            }
    }

    private fun isExecutableFile(file: File): Boolean = file.exists() && file.isFile && file.canExecute()

    private fun ensureActive(shouldCancel: () -> Boolean) {
        if (project.isDisposed || Thread.currentThread().isInterrupted || shouldCancel()) {
            throw CancellationException("Blender installation scan was cancelled")
        }
    }

    /**
     * Extracts the semantic version from the Blender version string, returned from `blender --version`, which usually is formatted "Blender
     * X.X.X" Function attempts a simple extraction of the semantic version from the Blender version string, before falling back to more
     * complex extraction methods.
     *
     * @param commandOutput Blender version string from `blender --version`
     */
    private fun formSemanticVersion(commandOutput: String): String {
        try {
            return commandOutput.split(" ")[1]
        } catch (e: Exception) {
            val firstLine = commandOutput.lineSequence().firstOrNull().orEmpty().trim()

            // Preferred: full semver (e.g. 4.2.1)
            Regex("""\b(\d+\.\d+\.\d+)\b""").find(firstLine)?.let {
                return it.groupValues[1]
            }

            // Fallback: major.minor (e.g. 4.2) -> normalize to semver
            Regex("""\b(\d+\.\d+)\b""").find(firstLine)?.let {
                return "${it.groupValues[1]}.0"
            }

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
