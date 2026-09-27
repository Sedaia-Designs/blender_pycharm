package com.sakurasedaia.blenderdevelopment.core.installs

import com.sakurasedaia.blenderdevelopment.state.PluginConfig.BlendInstallInfo
import java.nio.file.Path

/** Discovers Blender application bundles and Homebrew installations on macOS. */
internal class MacScanner(private val support: ScannerSupport) {
    fun scan(
        diagnostics: ScanDiagnostics,
        shouldCancel: () -> Boolean,
        additionalSearchRoots: List<Path> = emptyList(),
    ): List<BlendInstallInfo> {
        val installs = mutableListOf<BlendInstallInfo>()
        val searchRoots = buildList {
            add(Path.of("/Applications"))
            add(Path.of(System.getProperty("user.home"), "Applications"))
            addAll(additionalSearchRoots)
        }

        findBlenderBundles(
                searchRoots = searchRoots,
                maxSearchDepth = MAX_SEARCH_DEPTH,
                shouldCancel = shouldCancel,
                onFailure = { diagnostics.inaccessibleRoots += 1 },
            )
            .forEach { bundle ->
                val bundleFile = bundle.toFile()
                support
                    .buildInstallInfo(
                        bundleFile,
                        bundleFile.absolutePath,
                        diagnostics,
                        shouldCancel,
                        internalBinary = "Blender",
                    )
                    ?.let(installs::add)
            }

        installs.addAll(support.checkHomebrew(diagnostics, shouldCancel))
        return installs.distinct()
    }

    internal fun findBlenderBundles(
        searchRoots: List<Path>,
        maxSearchDepth: Int,
        shouldCancel: () -> Boolean = { false },
        onFailure: () -> Unit = {},
        isExecutable: (Path) -> Boolean = { isExecutableFile(it.toFile()) },
    ): List<Path> {
        val pendingDirectories = ArrayDeque(searchRoots.map { it.toAbsolutePath().normalize() }.distinct().map { it to 0 })
        val checkedDirectories = mutableSetOf<Path>()
        val checkedBundles = mutableSetOf<Path>()
        val discoveredBundles = linkedSetOf<Path>()

        while (pendingDirectories.isNotEmpty()) {
            val (directory, depth) = pendingDirectories.removeFirst()
            support.ensureActive(shouldCancel)
            if (!checkedDirectories.add(directory) || !directory.toFile().isDirectory) continue

            support.listDirectoryEntriesSafely(directory, shouldCancel, onFailure) { entry ->
                val candidate = entry.toAbsolutePath().normalize()
                if (!candidate.toFile().isDirectory) return@listDirectoryEntriesSafely
                if (!candidate.fileName.toString().endsWith(".app", ignoreCase = true)) {
                    if (depth < maxSearchDepth) pendingDirectories.addLast(candidate to depth + 1)
                    return@listDirectoryEntriesSafely
                }

                if (!checkedBundles.add(candidate)) return@listDirectoryEntriesSafely
                val blenderBinary = candidate.resolve("Contents").resolve("MacOS").resolve("Blender")
                if (isExecutable(blenderBinary)) discoveredBundles.add(candidate)
            }
        }

        return discoveredBundles.toList()
    }

    private companion object {
        const val MAX_SEARCH_DEPTH = 3
    }
}
