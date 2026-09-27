package com.sakurasedaia.blenderdevelopment.ui.settings

import com.sakurasedaia.blenderdevelopment.state.PluginConfig

internal class BlenderSettingsController(
    private val view: BlenderSettingsView,
    private val config: PluginConfig,
) {
    fun reset() {
        view.renderForm(readConfig())
    }

    fun isModified(): Boolean = view.readForm() != readConfig()

    fun apply() {
        val form = view.readForm()
        config.setBlenderInstallPath(form.blenderInstallPath)
        config.setCodeCompletionPath(form.codeCompletionPath)
        config.setLogPath(form.logPath)
        config.setDownloadPath(form.downloadPath)
        config.setClearDownloadsAfterInstall(form.clearDownloadsAfterInstall)
        config.setMinimumBlenderVersion(form.minimumBlenderVersion)
        config.setGlobalEnvironmentVariables(form.globalEnvironmentVariables)
    }

    private fun readConfig(): BlenderSettingsForm =
        BlenderSettingsForm(
            blenderInstallPath = config.getBlenderInstallPath(),
            codeCompletionPath = config.getCodeCompletionPath(),
            logPath = config.getLogPath(),
            downloadPath = config.getDownloadPath(),
            clearDownloadsAfterInstall = config.getClearDownloadsAfterInstall(),
            minimumBlenderVersion = config.getMinimumBlenderVersion(),
            globalEnvironmentVariables = config.getGlobalEnvironmentVariables(),
        )
}
