package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion

internal data class BlenderVersionManagementState(
    val rows: List<BlenderVersionSettingsRow>,
    val selectedVersion: BlenderVersion?,
    val lastRefreshedEpochMillis: Long,
    val operation: Operation?,
    val isTableEnabled: Boolean,
    val isInstallEnabled: Boolean,
    val isDeleteEnabled: Boolean,
    val isScanEnabled: Boolean,
    val isRefreshVersionCacheEnabled: Boolean,
    val isClearVersionCacheEnabled: Boolean,
) {

  internal enum class Operation {
    REFRESHING,
    SCANNING,
    INSTALLING,
    DELETING,
    CLEARING_CACHE,
  }
}
