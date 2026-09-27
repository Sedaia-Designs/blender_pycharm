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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.state

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.util.PythonModuleNameValidator
import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import dev.eav.tomlkt.Toml
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Project-level persisted configuration for Blender project settings. */
@Service(Service.Level.PROJECT)
@State(
    name = "BlenderExtensionManifest",
    storages = [Storage($$"$PROJECT_CONFIG_DIR$/blender-workspace.xml")],
)
class ProjectConfig(private val project: Project, private val coroutineScope: CoroutineScope) :
    PersistentStateComponent<ProjectConfig.ProjectState> {
    /** Available log levels according to Blender Documentation */
    enum class BlenderLogLevel {
        FATAL,
        ERROR,
        WARNING,
        INFO,
        DEBUG,
        TRACE,
    }

    /** Persisted project-scoped settings stored in project-level configuration. */
    data class ProjectState(
        var blenderPath: String = "",
        var installedStubRequirement: String = "",
        var addonSymlinkName: String = "",
        var sourceFolder: String = "src/",
        var runArguments: String = "",
        var blenderLogLevel: String = BlenderLogLevel.DEBUG.name,
        var reloadOnSave: Boolean = true,
        var justMyCode: Boolean = true,
        var extensionsRepository: String = "pycharm_blender",
        var environmentVariables: Map<String, String> = emptyMap(),
        var scriptDirectories: List<String>? = null,
    )

    /** Immutable observable snapshot of project-scoped Blender configuration. */
    data class ProjectSnapshot(
        val blenderPath: String,
        val installedStubRequirement: String,
        val addonSymlinkName: String,
        val sourceFolder: String,
        val runArguments: String,
        val blenderLogLevel: BlenderLogLevel,
        val reloadOnSave: Boolean,
        val justMyCode: Boolean,
        val extensionsRepository: String,
        val environmentVariables: Map<String, String>,
        val scriptDirectories: List<String>?,
        val workspaceConfigEnabled: Boolean,
    )

    @Serializable
    internal data class WorkspaceState(
        @SerialName("addon_symlink_name") val addonSymlinkName: String? = null,
        @SerialName("source_folder") val sourceFolder: String? = null,
        @SerialName("run_arguments") val runArguments: String? = null,
        @SerialName("blender_log_level") val blenderLogLevel: String? = null,
        @SerialName("reload_on_save") val reloadOnSave: Boolean? = null,
        @SerialName("just_my_code") val justMyCode: Boolean? = null,
        @SerialName("extensions_repository") val extensionsRepository: String? = null,
    )

    private val stateLock = Any()
    private val workspaceWriteMutex = Mutex()
    private val logger = PluginLogger.getInstance(project)
    private var localState: ProjectState = ProjectState()
    private var workspaceState: WorkspaceState? = null
    private val mutableStateFlow = MutableStateFlow(effectiveState().toSnapshot())

    private fun toAbsoluteProjectPath(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("~/") || trimmed == "~") {
            return SystemInfo().userHomeDir.resolve(trimmed.removePrefix("~/")).normalize().toString()
        }

        val path = Path.of(trimmed)
        if (path.isAbsolute) return path.normalize().toString()

        val projectPath = project.basePath ?: return path.normalize().toString()
        return Path.of(projectPath).resolve(path).normalize().toString()
    }

    /** Read-only stream of current project configuration snapshots. */
    val stateFlow: StateFlow<ProjectSnapshot> = mutableStateFlow.asStateFlow()

    /**
     * Stores the Blender executable path.
     *
     * @param path Blender executable path.
     * @return `Unit`.
     */
    fun setBlenderPath(path: String) {
        updateLocalState { blenderPath = toAbsoluteProjectPath(path) }
    }

    /** Returns the configured Blender executable path for this project. */
    fun getBlenderPath(): String = effectiveState().blenderPath

    /**
     * Stores the exact linting-stub requirement installed for this project.
     *
     * @param requirement installed version-specific package requirement, or an empty value.
     */
    fun setInstalledStubRequirement(requirement: String) {
        updateLocalState { installedStubRequirement = requirement }
    }

    /** Returns the exact linting-stub requirement last installed by the plugin. */
    fun getInstalledStubRequirement(): String = effectiveState().installedStubRequirement

    /**
     * Stores the add-on symlink name.
     *
     * @param name add-on symlink name.
     */
    fun setAddonSymlinkName(name: String) {
        val normalized = normalizeAddonSymlinkName(name)
        if (!PythonModuleNameValidator.isValid(normalized)) {
            return
        }
        updatePortableState { addonSymlinkName = normalized }
    }

    /** Returns the configured add-on symlink name. */
    fun getAddonSymlinkName(): String = effectiveState().addonSymlinkName

    private fun normalizeAddonSymlinkName(name: String): String {
        return name.trim().replace(SYMLINK_NAME_SEPARATOR_REGEX, "_")
    }

    /**
     * Stores the source folder path.
     *
     * @param path source folder path.
     */
    fun setSourceFolder(path: String) {
        updatePortableState { sourceFolder = path }
    }

    /** Returns the configured source folder path used by project workflows. */
    fun getSourceFolder(): String = effectiveState().sourceFolder

    /**
     * Stores the run arguments used by the Blender run configuration.
     *
     * @param arguments run arguments string.
     */
    fun setRunArguments(arguments: String) {
        updatePortableState { runArguments = arguments }
    }

    /** Returns the stored Blender run arguments string. */
    fun getRunArguments(): String = effectiveState().runArguments

    /**
     * Stores the configured Blender log level used by launch workflows.
     *
     * @param logLevel selected log level.
     */
    fun setBlenderLogLevel(logLevel: BlenderLogLevel) {
        updatePortableState { blenderLogLevel = logLevel.name }
    }

    /** Returns the configured Blender log level. */
    fun getBlenderLogLevel(): BlenderLogLevel {
        return BlenderLogLevel.entries.firstOrNull { it.name == effectiveState().blenderLogLevel } ?: BlenderLogLevel.INFO
    }

    /**
     * Stores whether add-ons should reload automatically on save.
     *
     * @param reload true to reload on save, false otherwise.
     */
    fun setReloadOnSave(reload: Boolean) {
        updatePortableState { reloadOnSave = reload }
    }

    /** Returns whether add-ons should reload automatically on save. */
    fun getReloadOnSave(): Boolean = effectiveState().reloadOnSave

    /**
     * Stores whether debugger behavior should prioritize project code only.
     *
     * @param justMyCode true to focus on project code, false otherwise.
     */
    fun setJustMyCode(justMyCode: Boolean) {
        updatePortableState { this.justMyCode = justMyCode }
    }

    /** Returns whether debugger behavior is configured as just-my-code. */
    fun getJustMyCode(): Boolean = effectiveState().justMyCode

    /**
     * Stores the configured Blender extensions repository path or URL.
     *
     * @param repository extensions repository path or URL.
     */
    fun setExtensionsRepository(repository: String) {
        val normalizedRepository = repository.trim()
        if (!PythonModuleNameValidator.isValid(normalizedRepository)) {
            return
        }
        updatePortableState { extensionsRepository = normalizedRepository }
    }

    /** Returns the configured Blender extensions repository path or URL. */
    fun getExtensionsRepository(): String = effectiveState().extensionsRepository

    /**
     * Stores project-scoped environment variables for Blender runtime workflows.
     *
     * @param variables environment variables map.
     */
    fun setEnvironmentVariables(variables: Map<String, String>) {
        updateLocalState { environmentVariables = variables.toMap() }
    }

    /** Returns project-scoped environment variables for Blender runtime workflows. */
    fun getEnvironmentVariables(): Map<String, String> = effectiveState().environmentVariables

    /**
     * Stores optional script directories used by runtime workflows.
     *
     * @param directories optional list of script directory paths.
     */
    fun setScriptDirectories(directories: List<String>?) {
        updateLocalState {
            scriptDirectories = directories?.map(::toAbsoluteProjectPath)?.filter(String::isNotBlank)
        }
    }

    /** Returns optional script directories used by runtime workflows. */
    fun getScriptDirectories(): List<String>? = effectiveState().scriptDirectories

    /**
     * Forces initialization of persisted workspace settings for this project.
     *
     * This should be called during project startup to ensure the state from the project-level workspace file is loaded before UI and run
     * flows use it.
     *
     * @return currently loaded project state.
     */
    suspend fun loadWorkspaceState(): ProjectState {
        val path = workspaceConfigPath() ?: return effectiveState()
        val loadedState =
            withContext(Dispatchers.IO) {
                if (!path.exists()) {
                    null
                } else {
                    runCatching { TOML.decodeFromString<WorkspaceState>(path.readText()) }
                        .onFailure { logger.warn(ErrorTypes.WORKSPACE_CONFIG_LOAD_FAILED.format(path), it) }
                        .getOrNull()
                }
            }
        return synchronized(stateLock) {
            workspaceState = loadedState
            publishState()
            effectiveState()
        }
    }

    /**
     * Creates or replaces the portable workspace configuration using the current effective project settings.
     *
     * Machine-specific values remain in IntelliJ's project-level XML storage. After this method succeeds, subsequent portable setting
     * changes are written to the workspace file automatically.
     *
     * @return path to the written workspace configuration file.
     */
    suspend fun saveWorkspaceState(): Path {
        val path = workspaceConfigPath() ?: throw IllegalStateException(ErrorTypes.WORKSPACE_CONFIG_PROJECT_PATH_MISSING.toString())
        workspaceWriteMutex.withLock {
            val portableState = synchronized(stateLock) { effectiveState().toWorkspaceState() }
            writeWorkspaceState(path, portableState)
            synchronized(stateLock) {
                workspaceState = portableState
                publishState()
            }
        }
        return path
    }

    /**
     * Loads workspace state from persistent storage.
     *
     * @param p0 deserialized state payload.
     */
    override fun loadState(p0: ProjectState) {
        synchronized(stateLock) {
            localState =
                p0.copy(
                    environmentVariables = p0.environmentVariables.toMap(),
                    scriptDirectories = p0.scriptDirectories?.toList(),
                )
            workspaceState = null
            publishState()
        }
    }

    /** Returns the current persisted workspace state payload. */
    override fun getState(): ProjectState = synchronized(stateLock) { localState.copyState() }

    private inline fun updateLocalState(update: ProjectState.() -> Unit) {
        synchronized(stateLock) {
            localState.update()
            publishState()
        }
    }

    private inline fun updatePortableState(update: ProjectState.() -> Unit) {
        val shouldSaveWorkspace =
            synchronized(stateLock) {
                if (workspaceState == null) {
                    localState.update()
                } else {
                    workspaceState = effectiveState().apply(update).toWorkspaceState()
                }
                publishState()
                workspaceState != null
            }
        if (shouldSaveWorkspace) scheduleWorkspaceSave()
    }

    private fun publishState() {
        mutableStateFlow.value = effectiveState().toSnapshot()
    }

    private fun effectiveState(): ProjectState {
        val workspace = workspaceState ?: return localState.copyState()
        return localState.copy(
            addonSymlinkName = workspace.addonSymlinkName ?: localState.addonSymlinkName,
            sourceFolder = workspace.sourceFolder ?: localState.sourceFolder,
            runArguments = workspace.runArguments ?: localState.runArguments,
            blenderLogLevel = workspace.blenderLogLevel ?: localState.blenderLogLevel,
            reloadOnSave = workspace.reloadOnSave ?: localState.reloadOnSave,
            justMyCode = workspace.justMyCode ?: localState.justMyCode,
            extensionsRepository = workspace.extensionsRepository ?: localState.extensionsRepository,
            environmentVariables = localState.environmentVariables.toMap(),
            scriptDirectories = localState.scriptDirectories?.toList(),
        )
    }

    private fun scheduleWorkspaceSave() {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching { saveWorkspaceState() }
                .onFailure { error -> logger.warn(ErrorTypes.WORKSPACE_CONFIG_SAVE_FAILED.format(workspaceConfigPath()), error) }
        }
    }

    private suspend fun writeWorkspaceState(path: Path, state: WorkspaceState) {
        withContext(Dispatchers.IO) {
            val temporaryPath = path.resolveSibling("${path.fileName}.tmp")
            try {
                temporaryPath.writeText(TOML.encodeToString(state))
                try {
                    Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporaryPath)
            }
        }
    }

    private fun workspaceConfigPath(): Path? = project.basePath?.let(Path::of)?.resolve(WORKSPACE_CONFIG_FILE_NAME)

    private fun ProjectState.copyState(): ProjectState =
        copy(
            environmentVariables = environmentVariables.toMap(),
            scriptDirectories = scriptDirectories?.toList(),
        )

    private fun ProjectState.toWorkspaceState(): WorkspaceState =
        WorkspaceState(
            addonSymlinkName = addonSymlinkName,
            sourceFolder = sourceFolder,
            runArguments = runArguments,
            blenderLogLevel = blenderLogLevel,
            reloadOnSave = reloadOnSave,
            justMyCode = justMyCode,
            extensionsRepository = extensionsRepository,
        )

    private fun ProjectState.toSnapshot(): ProjectSnapshot {
        return ProjectSnapshot(
            blenderPath = blenderPath,
            installedStubRequirement = installedStubRequirement,
            addonSymlinkName = addonSymlinkName,
            sourceFolder = sourceFolder,
            runArguments = runArguments,
            blenderLogLevel = BlenderLogLevel.entries.firstOrNull { it.name == blenderLogLevel } ?: BlenderLogLevel.INFO,
            reloadOnSave = reloadOnSave,
            justMyCode = justMyCode,
            extensionsRepository = extensionsRepository,
            environmentVariables = environmentVariables.toMap(),
            scriptDirectories = scriptDirectories?.toList(),
            workspaceConfigEnabled = workspaceState != null,
        )
    }

    companion object {
        private const val WORKSPACE_CONFIG_FILE_NAME = "blender-workspace.toml"
        private val SYMLINK_NAME_SEPARATOR_REGEX = Regex("[\\s-]+")
        private val TOML = Toml { ignoreUnknownKeys = true }

        /**
         * Returns this configuration service for the given project.
         *
         * @param project target project.
         * @return project-level [ProjectConfig] service.
         */
        fun getInstance(project: Project): ProjectConfig = project.service()
    }
}

// State managed by com.sakurasedaia.blenderdevelopment.ui.toolwindow.BlenderToolWindowFactory
