package com.sakurasedaia.blenderdevelopment.core.installs

import com.sakurasedaia.blenderdevelopment.state.PluginConfig.BlendInstallInfo
import java.nio.file.Path

/** Discovers Blender installations in conventional Windows installation directories. */
internal class WindowsScanner(private val support: ScannerSupport) {
    fun scan(diagnostics: ScanDiagnostics, shouldCancel: () -> Boolean): List<BlendInstallInfo> {
        val installs = mutableListOf<BlendInstallInfo>()
        val blenderProgramFiles = Path.of("Blender Foundation", "Blender")
        val programFiles =
            listOf(
                    Path.of(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"),
                    Path.of(System.getenv("ProgramFiles") ?: "C:\\Program Files"),
                )
                .distinct()

        programFiles.forEach { root ->
            support.ensureActive(shouldCancel)
            val installRoot = root.resolve(blenderProgramFiles)
            if (!installRoot.toFile().isDirectory) return@forEach

            support.listDirectoryEntriesSafely(installRoot, shouldCancel, onFailure = { diagnostics.inaccessibleRoots += 1 }) { version ->
                if (!version.toFile().isDirectory) return@listDirectoryEntriesSafely
                val executable = version.resolve("blender.exe").toFile()
                if (!isExecutableFile(executable)) return@listDirectoryEntriesSafely
                support.buildInstallInfo(executable, version.toString(), diagnostics, shouldCancel)?.let(installs::add)
            }
        }
        return installs
    }
}
