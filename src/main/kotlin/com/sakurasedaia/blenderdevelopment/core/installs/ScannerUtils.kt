package com.sakurasedaia.blenderdevelopment.core.installs

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.PluginConfig.BlendInstallInfo
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.SystemInfo

/**
 * Coordinates platform-specific Blender installation discovery and refreshes the shared installation cache.
 *
 * @property project project whose lifecycle owns installation scans.
 */
@Service(Service.Level.PROJECT)
class ScannerUtils(private val project: Project) {
    private val notification = NotificationModal.getInstance(project)
    private val support = ScannerSupport(project)

    /**
     * Scans known platform and configured install locations, then refreshes the detected installation cache.
     *
     * @param shouldCancel callback checked throughout discovery and version probing.
     */
    fun refreshInstalledVersionsCache(shouldCancel: () -> Boolean = { false }) {
        support.ensureActive(shouldCancel)
        val diagnostics = ScanDiagnostics()
        val installedVersions = linkedMapOf<String, BlendInstallInfo>()

        val platformInstalls =
            when (val osName = SystemInfo().osName) {
                "windows" -> WindowsScanner(support).scan(diagnostics, shouldCancel)
                "macos" -> MacScanner(support).scan(diagnostics, shouldCancel)
                "linux" -> LinuxScanner(support).scan(diagnostics, shouldCancel)
                else -> {
                    notification.sendError(MessageBundle.message("notification.settings.scan.unsupported.os", osName))
                    emptyList()
                }
            }

        platformInstalls.forEach { installedVersions.putIfAbsent(it.path, it) }
        support.getConfiguredRootBlenderInstalls(diagnostics, shouldCancel).forEach {
            installedVersions.putIfAbsent(it.path, it)
        }

        support.ensureActive(shouldCancel)
        PluginConfig.getInstance().setDetectedBlenderInstalls(installedVersions.values.toList())
        notifyCriticalScanFeedback(installedVersions.size, diagnostics)
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
}
