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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Attribute
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.lib.services.ScrapeBlenderVersionLists
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Application-level persisted configuration for global Blender plugin settings. */
@Service(Service.Level.APP)
@State(name = "PluginConfig", storages = [Storage("blender_pycharm.config.xml")])
class PluginConfig(private val coroutineScope: CoroutineScope) : PersistentStateComponent<PluginConfig.PluginState> {
  /** Descriptor for an installed Blender instance discovered on disk. */
  data class BlendInstallInfo(
      @Attribute var name: String = "",
      @Attribute var version: String = "",
      @Attribute var path: String = "",
  )

  /** Time units supported by the automatic Blender version refresh interval. */
  enum class TimeUnits {
    SECOND,
    MINUTE,
    HOUR,
    DAY,
    WEEK,
    MONTH,
  }

  /**
   * Persisted schedule for checking Blender's release index.
   *
   * @property interval positive quantity of [intervalType] between checks.
   * @property intervalType unit used by [interval].
   * @property lastCheckedEpochMillis time of the last successful check, or zero when never checked.
   */
  data class UpdateChecked(
      var interval: Int = 1,
      var intervalType: TimeUnits = TimeUnits.WEEK,
      var lastCheckedEpochMillis: Long = 0,
  )

  /** Persisted application-scoped settings for the plugin. */
  data class PluginState(
      // Temporary example setting will be filled out later with proper settings
      var blenderInstallPath: String =
          "${PathManager.getSystemPath()}/BlenderExtensions/Applications/", // Portable Blender application bundles
      // TODO(V1): Connect this legacy stub path to installation behavior or remove it from persisted state.
      var bpyApiInstallPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/CodeCompletion/", // Installs for Fake-Bpy-Module
      var downloadPath: String = "${PathManager.getSystemPath()}/BlenderExtensions/Downloads/",
      var clearDownloadsAfterInstall: Boolean = true,
      // TODO(V1): Enforce this cache limit after downloads or remove the unsupported setting.
      var downloadCacheMaxSize: Int = 2,
      var logPath: String =
          "${PathManager.getLogPath()}/BlenderExtensions/", // TODO: Change the default to the same path as the Intellij `idea.log` file,
      // but save alongside in a `blender-development.log` file.
      var detectedBlender: List<BlendInstallInfo> = mutableListOf(),
      var minimumBlenderVersion: String = DEFAULT_MINIMUM_BLENDER_VERSION,
      var globalEnvironmentVariables: Map<String, String> = emptyMap(),
      var blenderUpdateCheck: UpdateChecked = UpdateChecked(),
  ) {
    fun toSnapshot(): PluginSnapshot {
      return PluginSnapshot(
          blenderInstallPath = blenderInstallPath,
          codeCompletionPath = bpyApiInstallPath,
          downloadPath = downloadPath,
          clearDownloadsAfterInstall = clearDownloadsAfterInstall,
          downloadCacheMaxSize = downloadCacheMaxSize,
          logPath = logPath,
          detectedBlenderInstalls = detectedBlender.map(BlendInstallInfo::copy),
          minimumBlenderVersion = minimumBlenderVersion.takeIf(::isValidMinorVersion) ?: DEFAULT_MINIMUM_BLENDER_VERSION,
          globalEnvironmentVariables = globalEnvironmentVariables.toMap(),
          blenderUpdateCheck =
              BlenderUpdateCheckSnapshot(
                  interval = blenderUpdateCheck.interval,
                  intervalType = blenderUpdateCheck.intervalType,
                  lastCheckedEpochMillis = blenderUpdateCheck.lastCheckedEpochMillis,
              ),
      )
    }
  }

  /** Immutable observable Blender release refresh schedule. */
  data class BlenderUpdateCheckSnapshot(
      val interval: Int,
      val intervalType: TimeUnits,
      val lastCheckedEpochMillis: Long,
  )

  /** Immutable observable application-level state */
  data class PluginSnapshot(
      val blenderInstallPath: String,
      val codeCompletionPath: String,
      val downloadPath: String,
      val clearDownloadsAfterInstall: Boolean,
      val downloadCacheMaxSize: Int,
      val logPath: String,
      val detectedBlenderInstalls: List<BlendInstallInfo>,
      val minimumBlenderVersion: String = "4.2", // Local only, not shown to UI
      val globalEnvironmentVariables: Map<String, String>,
      val blenderUpdateCheck: BlenderUpdateCheckSnapshot,
  )

  private var blenderUpdateCheckJob: Job? = null
  private var state: PluginState = PluginState()

  private var mutableStateFlow: MutableStateFlow<PluginSnapshot> = MutableStateFlow(state.toSnapshot())

  val stateFlow: StateFlow<PluginSnapshot> = mutableStateFlow.asStateFlow()

  // TODO(V1): Serialize state mutations from UI and background jobs so concurrent updates cannot overwrite one another.
  private inline fun updateState(update: PluginState.() -> Unit) {
    state.update()
    publishState()
  }

  private fun publishState() {
    mutableStateFlow.value = state.toSnapshot()
  }

  /**
   * Sets the path to the Blender application bundle.
   *
   * @param path path to the Blender application bundle.
   */
  fun setBlenderInstallPath(path: String) {
    updateState { blenderInstallPath = path }
  }

  /** Returns the configured folder used to store Blender application bundles. */
  fun getBlenderInstallPath(): String = state.blenderInstallPath

  /**
   * Sets the path to the Fake-Bpy-Module installation.
   *
   * @param path path to the Fake-Bpy-Module installation.
   */
  fun setCodeCompletionPath(path: String) {
    updateState { bpyApiInstallPath = path }
  }

  /** Returns the configured installation folder for Fake-Bpy-Module stubs. */
  fun getCodeCompletionPath(): String = state.bpyApiInstallPath

  /**
   * Sets the path to the log file.
   *
   * @param path path to the log file.
   */
  fun setLogPath(path: String) {
    updateState { logPath = path }
  }

  /** Returns the configured directory for plugin log files. */
  fun getLogPath(): String = state.logPath

  /**
   * Sets the path to the download folder.
   *
   * @param path path to the download folder.
   */
  fun setDownloadPath(path: String) {
    updateState { downloadPath = path }
  }

  /** Returns the configured directory used for downloaded artifacts. */
  fun getDownloadPath(): String = state.downloadPath

  /**
   * Sets whether to clear the download folder after installation.
   *
   * @param clear true to clear the download folder after installation, false otherwise.
   */
  fun setClearDownloadsAfterInstall(clear: Boolean) {
    updateState { clearDownloadsAfterInstall = clear }
  }

  /** Returns whether downloads are deleted automatically after installation. */
  fun getClearDownloadsAfterInstall(): Boolean = state.clearDownloadsAfterInstall

  /**
   * Sets the maximum download cache size in megabytes.
   *
   * @param size maximum cache size in megabytes.
   */
  fun setDownloadCacheSize(size: Int) {
    updateState { downloadCacheMaxSize = size }
  }

  /** Returns the configured maximum download cache size in gigabytes. */
  fun getDownloadCacheSize(): Int = state.downloadCacheMaxSize

  /**
   * Stores the most recent discovered Blender installations.
   *
   * @param installs discovered Blender installations.
   */
  fun setDetectedBlenderInstalls(installs: List<BlendInstallInfo>) {
    updateState { detectedBlender = installs.map(BlendInstallInfo::copy) }
  }

  /** Returns the cached list of discovered Blender installations. */
  fun getDetectedBlenderInstalls(): List<BlendInstallInfo> = state.detectedBlender.map(BlendInstallInfo::copy)

  /**
   * Sets the oldest Blender minor release included in online version discovery.
   *
   * @param version Blender version in `major.minor` format.
   */
  fun setMinimumBlenderVersion(version: String) {
    require(isValidMinorVersion(version)) { "Minimum Blender version must use major.minor format" }
    updateState { minimumBlenderVersion = version }
  }

  /** Returns the oldest Blender minor release included in online version discovery. */
  fun getMinimumBlenderVersion(): String = state.minimumBlenderVersion.takeIf(::isValidMinorVersion) ?: DEFAULT_MINIMUM_BLENDER_VERSION

  /**
   * Sets the Global Environment Variables
   *
   * @param
   */
  fun setGlobalEnvironmentVariables(variables: Map<String, String>) {
    updateState { globalEnvironmentVariables = variables.toMap() }
  }

  /** Returns the list of Environment Variables */
  fun getGlobalEnvironmentVariables(): Map<String, String> = state.globalEnvironmentVariables

  /** Returns the persisted Blender release refresh schedule. */
  // TODO(V1): Return a defensive copy so callers cannot mutate persisted state without validation or publication.
  fun getBlenderUpdateCheck(): UpdateChecked = state.blenderUpdateCheck

  /**
   * Replaces the Blender release refresh schedule.
   *
   * @param updateCheck schedule containing a positive interval.
   */
  fun setBlenderUpdateCheck(updateCheck: UpdateChecked) {
    require(updateCheck.interval > 0) { "Blender update check interval must be positive" }
    updateState {
      blenderUpdateCheck = updateCheck.copy()
    }
    restartBlenderUpdateTimerIfRunning()
  }

  internal fun markBlenderUpdateChecked(newEpochMillis: Long = System.currentTimeMillis()) {
    updateState {
      blenderUpdateCheck = blenderUpdateCheck.copy(lastCheckedEpochMillis = newEpochMillis)
    }
  }

  internal fun isBlenderUpdateCheckDue(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
      millisUntilNextBlenderUpdateCheck(nowEpochMillis) == 0L

  internal fun millisUntilNextBlenderUpdateCheck(nowEpochMillis: Long = System.currentTimeMillis()): Long {
    val updateCheck = state.blenderUpdateCheck
    if (updateCheck.lastCheckedEpochMillis <= 0) return 0

    val elapsed = (nowEpochMillis - updateCheck.lastCheckedEpochMillis).coerceAtLeast(0)
    return (updateCheck.intervalMillis() - elapsed).coerceAtLeast(0)
  }

  internal fun millisUntilNextBlenderVersionRefresh(nowEpochMillis: Long = System.currentTimeMillis()): Long =
      if (BlenderVersionCache.getInstance().hasCachedVersions()) {
        millisUntilNextBlenderUpdateCheck(nowEpochMillis)
      } else {
        0L
      }

  @Synchronized
  internal fun startBlenderUpdateTimer() {
    if (blenderUpdateCheckJob?.isActive == true) return

    blenderUpdateCheckJob =
        coroutineScope.launch(CoroutineName("Blender version update checker")) {
          while (isActive) {
            delay(millisUntilNextBlenderVersionRefresh().milliseconds)
            val logger = pluginLogger()
            try {
              logger.log("Starting scheduled Blender version cache refresh.")
              ScrapeBlenderVersionLists.getInstance().refreshVersionCache()
              markBlenderUpdateChecked()
              logger.log("Scheduled Blender version cache refresh completed.")
            } catch (error: CancellationException) {
              throw error
            } catch (error: Exception) {
              logger.warn(ErrorTypes.VERSION_CACHE_REFRESH_FAILED.toString(), error)
              delay(UPDATE_CHECK_RETRY_DELAY_MILLIS.milliseconds)
            }
          }
        }
  }

  private fun pluginLogger(): PluginLogger {
    return PluginLogger.getInstance()
  }

  @Synchronized
  private fun restartBlenderUpdateTimerIfRunning() {
    if (blenderUpdateCheckJob == null) return
    blenderUpdateCheckJob?.cancel()
    blenderUpdateCheckJob = null
    startBlenderUpdateTimer()
  }

  private fun UpdateChecked.intervalMillis(): Long {
    val unit =
        when (intervalType) {
          TimeUnits.SECOND -> Duration.ofSeconds(1)
          TimeUnits.MINUTE -> Duration.ofMinutes(1)
          TimeUnits.HOUR -> Duration.ofHours(1)
          TimeUnits.DAY -> Duration.ofDays(1)
          TimeUnits.WEEK -> Duration.ofDays(7)
          TimeUnits.MONTH -> Duration.ofDays(30)
        }
    return unit.toMillis() * interval.coerceAtLeast(1)
  }

  /**
   * Forces initialization of persisted plugin settings.
   *
   * @return currently loaded plugin state.
   */
  fun loadPluginState(): PluginState = state

  /** Returns the current persisted state payload. */
  override fun getState(): PluginState = state

  /**
   * Replaces the current persisted state with deserialized storage data.
   *
   * @param state deserialized plugin state from persistent storage.
   */
  override fun loadState(state: PluginState) {
    this.state = state
    publishState()
  }

  companion object {
    private const val DEFAULT_MINIMUM_BLENDER_VERSION = "4.2"
    private val UPDATE_CHECK_RETRY_DELAY_MILLIS = Duration.ofHours(1).toMillis()
    private val MINOR_VERSION_PATTERN = Regex("^\\d+\\.\\d+$")

    internal fun isValidMinorVersion(version: String): Boolean = MINOR_VERSION_PATTERN.matches(version)

    /**
     * Returns the global plugin configuration service.
     *
     * @return application-level [PluginConfig] service.
     */
    fun getInstance(): PluginConfig = ApplicationManager.getApplication().getService(PluginConfig::class.java)
  }
}

// State managed by com.sakurasedaia.blenderdevelopment.ui.settings.BlenderSettingsFactory
