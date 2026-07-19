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

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal object BlenderRuntimeResources {
  private const val PLUGIN_ID = "com.sakurasedaia.BlenderDevelopment"
  private const val RUNTIME_ARCHIVE_RESOURCE = "blender-runtime/blender-runtime.zip"
  private const val SETTINGS_RUNTIME_DIRECTORY = "BlenderDevelopment/DebugRunner"
  private const val RUNTIME_VERSION_MARKER = ".runtime.version"

  fun ensureRuntimeExtracted(): Path {
    val debugRunnerDirectory = resolveDebugRunnerDirectory()
    val runtimeDirectory = debugRunnerDirectory.resolve("runtime")
    val includeDirectory = runtimeDirectory.resolve("include")
    val runtimeVersion = resolvePluginVersion()

    if (isRuntimeCurrent(runtimeDirectory, includeDirectory, runtimeVersion)) {
      return includeDirectory
    }

    if (runtimeDirectory.exists()) {
      deleteRecursively(runtimeDirectory)
    }
    Files.createDirectories(runtimeDirectory)
    extractArchive(runtimeDirectory)
    Files.writeString(runtimeDirectory.resolve(RUNTIME_VERSION_MARKER), runtimeVersion)

    return includeDirectory
  }

  private fun resolveDebugRunnerDirectory(): Path {
    val optionsPath = PathManager.getOptionsPath()
    require(optionsPath.isNotBlank()) {
      MessageBundle.message("run.configuration.blender.launch.error.options.path.empty")
    }
    return Path.of(optionsPath).resolve(SETTINGS_RUNTIME_DIRECTORY)
  }

  private fun resolvePluginVersion(): String {
    return PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "dev"
  }

  private fun isRuntimeCurrent(runtimeDirectory: Path, includeDirectory: Path, runtimeVersion: String): Boolean {
    if (!runtimeDirectory.exists() || !includeDirectory.isDirectory()) {
      return false
    }

    val markerPath = runtimeDirectory.resolve(RUNTIME_VERSION_MARKER)
    if (!markerPath.exists()) {
      return false
    }

    return runCatching { Files.readString(markerPath).trim() }
      .getOrNull() == runtimeVersion
  }

  private fun extractArchive(destinationDirectory: Path) {
    val archiveStream = PluginResources::class.java.classLoader.getResourceAsStream(RUNTIME_ARCHIVE_RESOURCE)
      ?: throw IllegalStateException(
        MessageBundle.message("run.configuration.blender.launch.error.runtime.archive.missing", RUNTIME_ARCHIVE_RESOURCE)
      )

    archiveStream.use { stream ->
      ZipInputStream(stream).use { zipInput ->
        var entry = zipInput.nextEntry
        while (entry != null) {
          val destinationPath = destinationDirectory.resolve(entry.name).normalize()
          require(destinationPath.startsWith(destinationDirectory)) {
            MessageBundle.message("run.configuration.blender.launch.error.runtime.archive.invalid.entry", entry.name)
          }

          if (entry.isDirectory || entry.name.endsWith("/")) {
            Files.createDirectories(destinationPath)
          } else {
            Files.createDirectories(destinationPath.parent)
            Files.copy(zipInput, destinationPath)
          }

          zipInput.closeEntry()
          entry = zipInput.nextEntry
        }
      }
    }
  }

  private fun deleteRecursively(path: Path) {
    Files.walk(path).use { stream ->
      stream.sorted(Comparator.reverseOrder())
        .forEach { Files.deleteIfExists(it) }
    }
  }
}
