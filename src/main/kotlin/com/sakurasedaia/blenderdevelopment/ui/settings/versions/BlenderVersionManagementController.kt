package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.intellij.openapi.Disposable
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import java.nio.file.Path

internal class BlenderVersionManagementController(
    private val view: BlenderVersionManagementView,
    private val refreshVersions: (((Result<List<BlenderVersion>>) -> Unit) -> Unit),
    private val scanInstallations: (((Result<List<PluginConfig.BlendInstallInfo>>) -> Unit) -> Unit),
    private val clearVersionCache: () -> Result<Unit>,
    private val installVersion: (BlenderVersion, (Result<Path>) -> Unit) -> Unit,
    private val deleteVersion: (BlenderVersion, (Result<Boolean>) -> Unit) -> Unit,
    private val lastRefreshedEpochMillis: () -> Long,
    private val isCompatible: (String) -> Boolean = SystemInfo::isOSCompatible,
) : Disposable {
    private var versions: List<BlenderVersion> = BlenderVersions.LIST
    private var installs: List<PluginConfig.BlendInstallInfo> = emptyList()
    private var state =
        BlenderVersionManagementState(
            rows = emptyList(),
            selectedVersion = null,
            lastRefreshedEpochMillis = 0,
            operation = null,
            isTableEnabled = true,
            isInstallEnabled = false,
            isDeleteEnabled = false,
            isScanEnabled = false,
            isRefreshVersionCacheEnabled = false,
            isClearVersionCacheEnabled = false,
        )
    private var generation = 0L
    private var isDisposed = false

    init {
        view.setOnSelectionChanged(::selectVersion)
        view.setOnInstallRequested(::install)
        view.setOnDeleteRequested(::delete)
        view.setOnRefreshRequested(::refresh)
        view.setOnScanRequested(::scan)
        view.setOnClearCacheRequested(::clearCache)
        view.render(state)
    }

    fun reset(
        versions: List<BlenderVersion>,
        installs: List<PluginConfig.BlendInstallInfo>,
        lastRefreshedEpochMillis: Long,
    ) {
        if (isDisposed) return
        generation++
        this.versions = versions
        this.installs = installs
        val rows = BlenderVersionSettingsRowMapper.map(versions, installs)
        updateState {
            copy(
                rows = rows,
                selectedVersion =
                    selectedVersion?.takeIf { selected ->
                        rows.any { it.version.blMajorMinor == selected.blMajorMinor }
                    } ?: rows.firstOrNull()?.version,
                lastRefreshedEpochMillis = lastRefreshedEpochMillis,
                operation = null,
            )
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        generation++
        view.clearCallbacks()
    }

    private fun selectVersion(version: BlenderVersion?) {
        if (isDisposed || state.operation != null) return
        updateState { copy(selectedVersion = version) }
    }

    private fun refresh() {
        val operationGeneration = beginOperation(BlenderVersionManagementState.Operation.REFRESHING) ?: return
        refreshVersions { result ->
            if (!accepts(operationGeneration)) return@refreshVersions
            result.onSuccess { refreshedVersions ->
                versions = refreshedVersions
                updateState {
                    val rows = BlenderVersionSettingsRowMapper.map(refreshedVersions, installs)
                    copy(
                        rows = rows,
                        selectedVersion =
                            selectedVersion?.takeIf { selected ->
                                rows.any { it.version.blMajorMinor == selected.blMajorMinor }
                            } ?: rows.firstOrNull()?.version,
                    )
                }
            }
            finishOperation(operationGeneration) {
                copy(lastRefreshedEpochMillis = lastRefreshedEpochMillis())
            }
        }
    }

    private fun scan() {
        val operationGeneration = beginOperation(BlenderVersionManagementState.Operation.SCANNING) ?: return
        scanInstallations { result ->
            if (!accepts(operationGeneration)) return@scanInstallations
            result.onSuccess { detectedInstalls ->
                installs = detectedInstalls
                updateState {
                    val rows = BlenderVersionSettingsRowMapper.map(versions, detectedInstalls)
                    copy(
                        rows = rows,
                        selectedVersion =
                            selectedVersion?.takeIf { selected ->
                                rows.any { it.version.blMajorMinor == selected.blMajorMinor }
                            } ?: rows.firstOrNull()?.version,
                    )
                }
            }
            finishOperation(operationGeneration)
        }
    }

    private fun clearCache() {
        val operationGeneration = beginOperation(BlenderVersionManagementState.Operation.CLEARING_CACHE) ?: return
        val result = clearVersionCache()
        if (!accepts(operationGeneration)) return
        if (result.isSuccess) {
            versions = BlenderVersions.LIST
            updateState {
                copy(
                    rows = BlenderVersionSettingsRowMapper.map(versions, installs),
                    lastRefreshedEpochMillis = 0,
                )
            }
        }
        finishOperation(operationGeneration)
    }

    private fun install(version: BlenderVersion) {
        if (!state.isInstallEnabled || state.selectedVersion?.blMajorMinor != version.blMajorMinor) return
        val operationGeneration = beginOperation(BlenderVersionManagementState.Operation.INSTALLING) ?: return
        installVersion(version) { result ->
            if (!accepts(operationGeneration)) return@installVersion
            if (result.isSuccess) {
                finishOperation(operationGeneration) {
                    copy(rows = BlenderVersionSettingsRowMapper.markInstalled(rows, version))
                }
                scan()
            } else {
                finishOperation(operationGeneration)
            }
        }
    }

    private fun delete(version: BlenderVersion) {
        if (!state.isDeleteEnabled || state.selectedVersion?.blMajorMinor != version.blMajorMinor) return
        val operationGeneration = beginOperation(BlenderVersionManagementState.Operation.DELETING) ?: return
        deleteVersion(version) { result ->
            if (!accepts(operationGeneration)) return@deleteVersion
            finishOperation(operationGeneration)
            if (result.isSuccess) {
                scan()
            }
        }
    }

    private fun beginOperation(operation: BlenderVersionManagementState.Operation): Long? {
        if (isDisposed || state.operation != null) return null
        val operationGeneration = ++generation
        updateState { copy(operation = operation) }
        return operationGeneration
    }

    private fun finishOperation(
        operationGeneration: Long,
        transform: BlenderVersionManagementState.() -> BlenderVersionManagementState = { this },
    ) {
        if (!accepts(operationGeneration)) return
        updateState { transform().copy(operation = null) }
    }

    private fun accepts(operationGeneration: Long): Boolean = !isDisposed && generation == operationGeneration

    private fun updateState(transform: BlenderVersionManagementState.() -> BlenderVersionManagementState) {
        if (isDisposed) return
        val updated = state.transform()
        val selectedRow =
            updated.rows.firstOrNull {
                it.version.blMajorMinor == updated.selectedVersion?.blMajorMinor
            }
        val isIdle = updated.operation == null
        state =
            updated.copy(
                isTableEnabled = isIdle,
                isInstallEnabled =
                    isIdle && selectedRow != null && !selectedRow.isInstalled && isCompatible(selectedRow.version.blMajorMinor),
                isDeleteEnabled = isIdle && selectedRow?.isInstalled == true,
                isScanEnabled = isIdle,
                isRefreshVersionCacheEnabled = isIdle,
                isClearVersionCacheEnabled = isIdle,
            )
        view.render(state)
    }
}
