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

import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** Resource-backed Python scripts used to start Blender integration code. */
object BlenderScriptTemplates {
    const val ENTRY_TEMPLATE_RESOURCE = "blender/scripts/blender_pycharm_entry.py.ft"
    const val BOOTSTRAP_RESOURCE = "blender/scripts/blender_pycharm_bootstrap.py"

    fun renderEntryScript(configPath: Path, bootstrapPath: Path): String {
        return loadResource(ENTRY_TEMPLATE_RESOURCE)
            .replace("__CONFIG_PATH_LITERAL__", pythonStringLiteral(configPath.toAbsolutePath().toString()))
            .replace("__BOOTSTRAP_PATH_LITERAL__", pythonStringLiteral(bootstrapPath.toAbsolutePath().toString()))
    }

    fun loadBootstrapScript(): String = loadResource(BOOTSTRAP_RESOURCE)

    private fun loadResource(resourcePath: String): String {
        val stream = BlenderScriptTemplates::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: error("Missing Blender script resource: $resourcePath")
        return stream.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
    }

    private fun pythonStringLiteral(value: String): String {
        val escaped = buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\'' -> append("\\'")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
        return "'$escaped'"
    }
}
