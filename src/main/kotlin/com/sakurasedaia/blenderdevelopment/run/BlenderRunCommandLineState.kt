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

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle

/** Command-line state placeholder for Blender run configuration execution. */
class BlenderRunCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: BlenderRunConfiguration,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        throw ExecutionException(
            MessageBundle.message("run.configuration.execution.not.implemented", configuration.name)
        )
    }
}

