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

package com.sakurasedaia.blenderdevelopment.util

import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.notExists

internal object BlenderBootstrapScriptCleanup {
  private const val SCRIPT_PREFIX = "blender_runtime_launch_"
  private const val SCRIPT_EXTENSION = "py"
  private val STALE_SCRIPT_MAX_AGE: Duration = Duration.ofHours(24)

  fun newScriptFileName(): String = "${SCRIPT_PREFIX}${UUID.randomUUID().toString().replace("-", "")}.$SCRIPT_EXTENSION"

  fun cleanupScript(path: Path, debugLog: (String) -> Unit, warnLog: (String, Throwable) -> Unit) {
    if (!isManagedBootstrapScript(path) || path.notExists()) {
      return
    }

    runCatching { Files.deleteIfExists(path) }
      .onFailure { error ->
        warnLog(ErrorTypes.BOOTSTRAP_SCRIPT_DELETE_FAILED.format(path.toAbsolutePath()), error)
      }
      .onSuccess { deleted ->
        if (deleted) {
          debugLog("Deleted bootstrap script `${path.toAbsolutePath()}`.")
        }
      }
  }

  fun cleanupStaleScripts(
    directory: Path,
    debugLog: (String) -> Unit,
    warnLog: (String, Throwable) -> Unit,
  ) {
    if (directory.notExists()) {
      return
    }

    val staleBefore = Instant.now().minus(STALE_SCRIPT_MAX_AGE)
    runCatching {
      Files.list(directory).use { entries ->
        entries.filter { isManagedBootstrapScript(it) && isStale(it, staleBefore) }
          .forEach { cleanupScript(it, debugLog, warnLog) }
      }
    }.onFailure { error ->
      warnLog(ErrorTypes.BOOTSTRAP_SCRIPT_SCAN_FAILED.format(directory.toAbsolutePath()), error)
    }
  }

  private fun isManagedBootstrapScript(path: Path): Boolean {
    return path.isRegularFile()
      && path.extension == SCRIPT_EXTENSION
      && path.name.startsWith(SCRIPT_PREFIX)
      && path.fileSize() > 0L
  }

  private fun isStale(path: Path, staleBefore: Instant): Boolean {
    val lastModifiedTime: FileTime = runCatching { path.getLastModifiedTime() }.getOrNull() ?: return false
    return lastModifiedTime.toInstant().isBefore(staleBefore)
  }
}
