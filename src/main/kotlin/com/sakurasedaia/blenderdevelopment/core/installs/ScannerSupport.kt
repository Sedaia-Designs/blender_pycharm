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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.core.installs

import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.process.ExternalProcessBuilder
import com.sakurasedaia.blenderdevelopment.state.InstallType
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.PluginConfig.BlendInstallInfo
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.io.path.listDirectoryEntries

internal data class ScanDiagnostics(
    var inaccessibleRoots: Int = 0,
    var versionProbeFailures: Int = 0,
)

/** Shared, platform-independent mechanics used by Blender installation scanners. */
internal class ScannerSupport(private val project: Project) {
    private val logger = PluginLogger.getInstance(project)

    fun buildInstallInfo(
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
                InstallType.PYCHARM -> "Managed"
            }
        return BlendInstallInfo(
            name = "$detectedVersion ($installTypeLabel)",
            version = formSemanticVersion(detectedVersion),
            path = installPath,
            installType = installType,
        )
    }

    fun listDirectoryEntriesSafely(path: Path, shouldCancel: () -> Boolean, onFailure: () -> Unit, consume: (Path) -> Unit) {
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

    fun checkHomebrew(diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): List<BlendInstallInfo> {
        val installs = linkedSetOf<BlendInstallInfo>()
        val prefixes =
            listOfNotNull(
                System.getenv("HOMEBREW_PREFIX")?.takeIf { it.isNotBlank() }?.let(Path::of),
                Path.of("/opt/homebrew"),
                Path.of("/usr/local"),
                Path.of("/home/linuxbrew/.linuxbrew"),
                Path.of("/linuxbrew/.linuxbrew"),
            )
        findHomebrewBlenderBinaries(prefixes, shouldCancel, onFailure = { diagnostics.inaccessibleRoots += 1 }).forEach { binary ->
            val file = binary.toFile()
            buildInstallInfo(file, file.absolutePath, diagnostics, shouldCancel)?.let(installs::add)
        }
        return installs.toList()
    }

    internal fun findHomebrewBlenderBinaries(
        brewPrefixes: List<Path>,
        shouldCancel: () -> Boolean = { false },
        onFailure: () -> Unit = {},
        isExecutable: (Path) -> Boolean = { isExecutableFile(it.toFile()) },
    ): List<Path> {
        val discovered = linkedSetOf<Path>()
        val binaryNames = listOf("blender", "blender-runtime")

        brewPrefixes
            .map { it.toAbsolutePath().normalize() }
            .distinct()
            .forEach { prefix ->
                ensureActive(shouldCancel)
                binaryNames.forEach binaryLoop@{ binaryName ->
                    val linkedBinary = prefix.resolve("bin").resolve(binaryName).normalize()
                    if (isExecutable(linkedBinary)) discovered.add(linkedBinary)

                    val cellar = prefix.resolve("Cellar").resolve(binaryName).normalize()
                    if (!cellar.toFile().isDirectory) return@binaryLoop
                    listDirectoryEntriesSafely(cellar, shouldCancel, onFailure) { versionDirectory ->
                        if (!versionDirectory.toFile().isDirectory) return@listDirectoryEntriesSafely
                        val binary = versionDirectory.resolve("bin").resolve(binaryName).toAbsolutePath().normalize()
                        if (isExecutable(binary)) discovered.add(binary)
                    }
                }
            }
        return discovered.toList()
    }

    fun getConfiguredRootBlenderInstalls(diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): List<BlendInstallInfo> {
        ensureActive(shouldCancel)
        val rawRoot = PluginConfig.getInstance().getBlenderInstallPath().trim()
        if (rawRoot.isBlank()) return emptyList()
        val configuredRoot = resolveUserPath(rawRoot)
        if (!configuredRoot.toFile().isDirectory) return emptyList()

        val discovered = linkedMapOf<String, BlendInstallInfo>()
        scanInstallCandidate(configuredRoot, diagnostics, shouldCancel)?.let { discovered[it.path] = it }
        listDirectoryEntriesSafely(configuredRoot, shouldCancel, onFailure = { diagnostics.inaccessibleRoots += 1 }) { entry ->
            if (!entry.toFile().isDirectory) return@listDirectoryEntriesSafely
            scanInstallCandidate(entry, diagnostics, shouldCancel)?.let { discovered[it.path] = it }
        }
        return discovered.values.toList()
    }

    fun resolveBinaryPathWithWhich(shouldCancel: () -> Boolean): String? {
        ensureActive(shouldCancel)
        val result = ExternalProcessBuilder(project).launchAndCaptureOutput("which", "blender-runtime", shouldCancel = shouldCancel)
        if (result.cancelled || result.failure != null || result.exitCode != 0) return null
        return result.firstLine
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { path ->
                Path.of(path).toFile().takeIf(::isExecutableFile)?.absolutePath
            }
    }

    fun ensureActive(shouldCancel: () -> Boolean) {
        if (project.isDisposed || Thread.currentThread().isInterrupted || shouldCancel()) {
            throw CancellationException("Blender installation scan was cancelled")
        }
    }

    private fun getBlenderVersion(
        binary: File,
        diagnostics: ScanDiagnostics,
        shouldCancel: () -> Boolean,
        internalBinary: String?,
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

    private fun scanInstallCandidate(
        candidatePath: Path,
        diagnostics: ScanDiagnostics,
        shouldCancel: () -> Boolean,
    ): BlendInstallInfo? {
        ensureActive(shouldCancel)
        val candidateDirectory = candidatePath.toFile()
        if (candidateDirectory.name.endsWith(".app", ignoreCase = true)) {
            val appBinary = candidatePath.resolve("Contents").resolve("MacOS").resolve("Blender").toFile()
            if (isExecutableFile(appBinary)) {
                return buildInstallInfo(
                    candidateDirectory,
                    candidateDirectory.absolutePath,
                    diagnostics,
                    shouldCancel,
                    internalBinary = "Blender",
                    installType = InstallType.PYCHARM,
                )
            }
        }

        val binary =
            listOf(
                    candidatePath.resolve("blender"),
                    candidatePath.resolve("blender.exe"),
                    candidatePath.resolve("blender-runtime"),
                    candidatePath.resolve("bin").resolve("blender"),
                    candidatePath.resolve("bin").resolve("blender.exe"),
                    candidatePath.resolve("bin").resolve("blender-runtime"),
                )
                .map(Path::toFile)
                .firstOrNull(::isExecutableFile) ?: return null
        return buildInstallInfo(binary, candidateDirectory.absolutePath, diagnostics, shouldCancel, installType = InstallType.PYCHARM)
    }

    private fun resolveUserPath(value: String): Path {
        if (!value.startsWith("~")) return Path.of(value).normalize()
        val relativePath = value.removePrefix("~").removePrefix("/")
        return Path.of(System.getProperty("user.home")).resolve(relativePath).normalize()
    }
}

internal fun isExecutableFile(file: File): Boolean = file.exists() && file.isFile && file.canExecute()

internal fun formSemanticVersion(commandOutput: String): String {
    val firstLine = commandOutput.lineSequence().firstOrNull().orEmpty().trim()
    Regex("""\b(\d+\.\d+\.\d+)\b""").find(firstLine)?.let {
        return it.groupValues[1]
    }
    Regex("""\b(\d+\.\d+)\b""").find(firstLine)?.let {
        return "${it.groupValues[1]}.0"
    }
    val numbers = Regex("""\d+""").findAll(firstLine).map { it.value }.toList()
    return when {
        numbers.size >= 3 -> "${numbers[0]}.${numbers[1]}.${numbers[2]}"
        numbers.size == 2 -> "${numbers[0]}.${numbers[1]}.0"
        else -> ""
    }
}
