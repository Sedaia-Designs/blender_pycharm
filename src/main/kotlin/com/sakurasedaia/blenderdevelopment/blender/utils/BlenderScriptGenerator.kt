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

package com.sakurasedaia.blenderdevelopment.blender.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/** Configuration for the generated Blender startup script and its sidecar JSON file. */
data class BlenderStartupScriptOptions(
    val reloadPort: Int,
    val reloadServerHost: String = "127.0.0.1",
    val repositoryName: String = "blender_pycharm",
    val repositoryPath: Path? = null,
    val extensionName: String? = null,
    val debugEnabled: Boolean = false,
    val debugHost: String = "127.0.0.1",
    val debugPort: Int = 5678,
    val connectMaxRetries: Int = 5,
    val connectRetryDelaySeconds: Double = 1.0,
    val printEnvironment: Boolean = true,
)

/** Files generated for a Blender startup run. */
data class BlenderStartupScriptFiles(
    val entryScriptPath: Path,
    val bootstrapScriptPath: Path,
    val configPath: Path,
)

@Service
class BlenderScriptGenerator {
    fun createStartupScripts(options: BlenderStartupScriptOptions): BlenderStartupScriptFiles {
        require(options.reloadPort in 1..65535) { "Reload port must be between 1 and 65535." }
        require(options.debugPort in 1..65535) { "Debug port must be between 1 and 65535." }
        require(options.connectMaxRetries > 0) { "Connection retry count must be positive." }
        require(options.connectRetryDelaySeconds >= 0.0) { "Connection retry delay cannot be negative." }

        val scratchDir = Path.of(PathManager.getConfigPath(), "scratches", "blender-development")
        if (!scratchDir.exists()) {
            scratchDir.createDirectories()
        }

        val runId = buildRunId(options)
        val bootstrapPath = scratchDir.resolve("blender_pycharm_bootstrap.py")
        val configPath = scratchDir.resolve("blender_start_$runId.json")
        val entryScriptPath = scratchDir.resolve("blender_start_$runId.py")

        Files.writeString(bootstrapPath, BlenderScriptTemplates.loadBootstrapScript(), StandardCharsets.UTF_8)
        Files.writeString(configPath, renderConfig(options), StandardCharsets.UTF_8)
        Files.writeString(
            entryScriptPath,
            BlenderScriptTemplates.renderEntryScript(configPath, bootstrapPath),
            StandardCharsets.UTF_8,
        )

        return BlenderStartupScriptFiles(
            entryScriptPath = entryScriptPath,
            bootstrapScriptPath = bootstrapPath,
            configPath = configPath,
        )
    }

    fun createStartupScript(
        port: Int,
        repoDir: Path?,
        extensionName: String?,
        isDebugMode: Boolean = false,
    ): Path {
        return createStartupScripts(
            BlenderStartupScriptOptions(
                reloadPort = port,
                repositoryPath = repoDir,
                extensionName = extensionName,
                debugEnabled = isDebugMode,
            )
        ).entryScriptPath
    }

    fun cleanupStartupScripts(files: BlenderStartupScriptFiles?) {
        if (files == null) return
        cleanupStartupScript(files.entryScriptPath)
        cleanupStartupScript(files.configPath)
    }

    fun cleanupStartupScript(scriptPath: Path?) {
        if (scriptPath == null) return
        try {
            Files.deleteIfExists(scriptPath)
        } catch (_: Exception) {
            // Best effort cleanup: Blender or the OS may still hold the generated file open.
        }
    }

    private fun buildRunId(options: BlenderStartupScriptOptions): String {
        val extension = options.extensionName
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "session"
        return "${options.reloadPort}_$extension"
    }

    private fun renderConfig(options: BlenderStartupScriptOptions): String {
        return """
            {
              "repository": {
                "name": ${jsonString(options.repositoryName)},
                "path": ${jsonString(options.repositoryPath?.toAbsolutePath()?.normalize()?.toString())}
              },
              "extension": {
                "name": ${jsonString(options.extensionName?.takeIf { it.isNotBlank() })}
              },
              "reload": {
                "server": ${jsonString(options.reloadServerHost)},
                "port": ${options.reloadPort},
                "max_retries": ${options.connectMaxRetries},
                "retry_delay_seconds": ${options.connectRetryDelaySeconds}
              },
              "debugpy": {
                "enabled": ${options.debugEnabled},
                "host": ${jsonString(options.debugHost)},
                "port": ${options.debugPort}
              },
              "logging": {
                "print_environment": ${options.printEnvironment}
              }
            }
        """.trimIndent()
    }

    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        val escaped = buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
        return "\"$escaped\""
    }

    companion object {
        fun getInstance(): BlenderScriptGenerator {
            return ApplicationManager.getApplication().getService(BlenderScriptGenerator::class.java)
        }
    }
}
