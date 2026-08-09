package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle

internal object BlenderVersionSettingsRowMapper {
  fun map(
    versions: List<BlenderVersion>,
    installs: List<PluginConfig.BlendInstallInfo>,
  ): List<BlenderVersionSettingsRow> {
    val installsByMinor = installs.associateBy { BlenderVersions.normalizeVersion(it.version) }
    return versions.map { version ->
      val installed = installsByMinor[version.blMajorMinor]
      BlenderVersionSettingsRow(
        version = version,
        pythonVersion = installed?.let { version.pyVersion.takeIf(String::isNotBlank) } ?: "—",
        installStatus = installed?.let {
          MessageBundle.message("ui.settings.group.versions.status.installed", it.version)
        } ?: MessageBundle.message("ui.settings.group.versions.status.not-detected"),
        isInstalled = installed != null,
      )
    }
  }

  fun markInstalled(
    rows: List<BlenderVersionSettingsRow>,
    version: BlenderVersion,
  ): List<BlenderVersionSettingsRow> = rows.map { row ->
    if (row.version.blMajorMinor == version.blMajorMinor) {
      row.copy(
        pythonVersion = version.pyVersion.takeIf(String::isNotBlank) ?: "—",
        installStatus = MessageBundle.message("ui.settings.group.versions.status.installed", version.blVersion),
        isInstalled = true,
      )
    }
    else {
      row
    }
  }
}
