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

package com.sakurasedaia.blenderdevelopment.core

import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import java.nio.file.Path

/** Builds ordered argument lists for Blender's mutually exclusive launch modes. */
internal object BlenderLaunchArguments {
    /** Builds arguments for executing a Python script after shared Blender options. */
    fun python(
        logLevel: BlenderLogLevel,
        workspaceArguments: List<String>,
        scriptPath: Path,
        additionalArguments: List<String> = emptyList(),
        blendFileToOpen: String? = null,
    ): List<String> {
        return buildList {
            addAll(buildLogArguments(logLevel))
            addAll(workspaceArguments)
            blendFileToOpen?.takeIf(String::isNotBlank)?.let(::add)
            addAll(listOf("--python", scriptPath.toString()))
            addAll(additionalArguments)
        }
    }

    /** Builds arguments for a Blender command, which consumes every argument after `--command`. */
    fun command(logLevel: BlenderLogLevel, commandArguments: List<String>): List<String> {
        require(commandArguments.isNotEmpty() && commandArguments.first().isNotBlank()) { "A Blender command is required." }
        require(commandArguments.first() != "--command" && commandArguments.first() != "-c") {
            "Command arguments must not include the --command option."
        }
        return buildLogArguments(logLevel) + listOf("--command") + commandArguments
    }

    private fun buildLogArguments(logLevel: BlenderLogLevel): List<String> {
        val level =
            when (logLevel) {
                BlenderLogLevel.FATAL -> "fatal"
                BlenderLogLevel.ERROR -> "error"
                BlenderLogLevel.WARNING -> "warning"
                BlenderLogLevel.INFO -> "info"
                BlenderLogLevel.DEBUG -> "debug"
                BlenderLogLevel.TRACE -> "trace"
            }
        return listOf("--log-level", level)
    }
}
