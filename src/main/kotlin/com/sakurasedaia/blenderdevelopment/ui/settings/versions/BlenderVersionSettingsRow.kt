package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion

internal data class BlenderVersionSettingsRow(
    val version: BlenderVersion,
    val pythonVersion: String,
    val installStatus: String,
    val isInstalled: Boolean,
)
