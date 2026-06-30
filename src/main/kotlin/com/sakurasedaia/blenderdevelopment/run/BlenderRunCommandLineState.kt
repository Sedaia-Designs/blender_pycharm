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

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import java.io.OutputStream

/** Temporarily disabled run state while Blender process flow is being rewritten. */
class BlenderRunCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: BlenderRunConfiguration,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val project = environment.project
        PluginLogger.warn(
            project,
            "Run configuration '${configuration.name}' is temporarily disabled while Blender process flow is being rewritten."
        )

        val handler = object : ProcessHandler() {
            override fun destroyProcessImpl() = Unit

            override fun detachProcessImpl() = notifyProcessDetached()

            override fun detachIsDefault(): Boolean = false

            override fun getProcessInput(): OutputStream? = null

            fun terminateDisabledState() {
                notifyProcessTerminated(0)
            }
        }

        handler.startNotify()
        handler.terminateDisabledState()

        return handler
    }
}
