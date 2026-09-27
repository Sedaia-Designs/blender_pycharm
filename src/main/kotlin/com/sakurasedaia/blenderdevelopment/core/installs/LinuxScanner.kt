package com.sakurasedaia.blenderdevelopment.core.installs

import com.sakurasedaia.blenderdevelopment.state.PluginConfig.BlendInstallInfo
import java.nio.file.Path

/** Discovers Blender installations in conventional Linux and Homebrew locations. */
internal class LinuxScanner(private val support: ScannerSupport) {
    fun scan(diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): List<BlendInstallInfo> {
        val installs = linkedSetOf<BlendInstallInfo>()

        support.resolveBinaryPathWithWhich(shouldCancel)?.let { path ->
            support.buildInstallInfo(Path.of(path).toFile(), path, diagnostics, shouldCancel)?.let(installs::add)
        }

        listOf(
                Path.of("/usr/bin/blender"),
                Path.of("/usr/lib/blender/blender"),
                Path.of("/usr/lib64/blender/blender"),
            )
            .forEach { candidate ->
                support.ensureActive(shouldCancel)
                val file = candidate.toFile()
                if (isExecutableFile(file)) {
                    support.buildInstallInfo(file, file.absolutePath, diagnostics, shouldCancel)?.let(installs::add)
                }
            }

        installs.addAll(support.checkHomebrew(diagnostics, shouldCancel))
        return installs.toList()
    }
}
