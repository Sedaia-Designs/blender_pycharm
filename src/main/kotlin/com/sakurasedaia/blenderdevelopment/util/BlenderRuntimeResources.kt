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

import com.intellij.openapi.application.PathManager
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal object BlenderRuntimeResources {
  private const val PLUGIN_METADATA_RESOURCE = "blender-development.properties"
  private const val PLUGIN_VERSION_PROPERTY = "plugin.version"
  private const val RUNTIME_ARCHIVE_RESOURCE = "blender-runtime/blender-runtime.zip"
  private const val SETTINGS_RUNTIME_DIRECTORY = "BlenderDevelopment/DebugRunner"
  private const val RUNTIME_VERSION_MARKER = ".runtime.version"

  fun ensureRuntimeExtracted(): Path {
    val debugRunnerDirectory = resolveDebugRunnerDirectory()
    val runtimeDirectory = debugRunnerDirectory.resolve("runtime")
    val includeDirectory = runtimeDirectory.resolve("include")
    val runtimeFingerprint = resolveRuntimeFingerprint()

    if (isRuntimeCurrent(runtimeDirectory, includeDirectory, runtimeFingerprint)) {
      return includeDirectory
    }

    if (runtimeDirectory.exists()) {
      deleteRecursively(runtimeDirectory)
    }
    Files.createDirectories(runtimeDirectory)
    extractArchive(runtimeDirectory)
    Files.writeString(runtimeDirectory.resolve(RUNTIME_VERSION_MARKER), runtimeFingerprint)

    return includeDirectory
  }

  private fun resolveDebugRunnerDirectory(): Path {
    val optionsPath = PathManager.getOptionsPath()
    require(optionsPath.isNotBlank()) {
      MessageBundle.message("run.configuration.blender.error.options.path.empty")
    }
    return Path.of(optionsPath).resolve(SETTINGS_RUNTIME_DIRECTORY)
  }

  internal fun resolvePluginVersion(): String {
    val properties = Properties()
    val resource = BlenderRuntimeResources::class.java.classLoader.getResourceAsStream(PLUGIN_METADATA_RESOURCE) ?: return "dev"
    resource.use(properties::load)
    return properties.getProperty(PLUGIN_VERSION_PROPERTY)?.takeIf(String::isNotBlank) ?: "dev"
  }

  private fun resolveRuntimeFingerprint(): String {
    val pluginVersion = resolvePluginVersion()
    val archiveHash = computeRuntimeArchiveSha256()
    return "$pluginVersion:$archiveHash"
  }

  private fun computeRuntimeArchiveSha256(): String {
    val archiveStream =
        PluginResources::class.java.classLoader.getResourceAsStream(RUNTIME_ARCHIVE_RESOURCE)
            ?: throw IllegalStateException(
                MessageBundle.message("run.configuration.blender.error.runtime.archive.missing", RUNTIME_ARCHIVE_RESOURCE)
            )
    val digest = MessageDigest.getInstance("SHA-256")
    archiveStream.use { stream ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = stream.read(buffer)
        if (read < 0) {
          break
        }
        if (read > 0) {
          digest.update(buffer, 0, read)
        }
      }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
  }

  private fun isRuntimeCurrent(runtimeDirectory: Path, includeDirectory: Path, runtimeFingerprint: String): Boolean {
    if (!runtimeDirectory.exists() || !includeDirectory.isDirectory()) {
      return false
    }

    val markerPath = runtimeDirectory.resolve(RUNTIME_VERSION_MARKER)
    if (!markerPath.exists()) {
      return false
    }

    return runCatching { Files.readString(markerPath).trim() }.getOrNull() == runtimeFingerprint
  }

  private fun extractArchive(destinationDirectory: Path) {
    val archiveStream =
        PluginResources::class.java.classLoader.getResourceAsStream(RUNTIME_ARCHIVE_RESOURCE)
            ?: throw IllegalStateException(
                MessageBundle.message("run.configuration.blender.error.runtime.archive.missing", RUNTIME_ARCHIVE_RESOURCE)
            )

    archiveStream.use { stream ->
      ZipInputStream(stream).use { zipInput ->
        var entry = zipInput.nextEntry
        while (entry != null) {
          val destinationPath = destinationDirectory.resolve(entry.name).normalize()
          require(destinationPath.startsWith(destinationDirectory)) {
            MessageBundle.message("run.configuration.blender.error.runtime.archive.invalid.entry", entry.name)
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
      stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
  }
}
