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

package com.sakurasedaia.blenderdevelopment.debug

import com.sakurasedaia.blenderdevelopment.logging.ErrorTypes

/** Represents the result of starting or interacting with a debugpy session. */
sealed class DebugpyResult {
    /** Successful debugpy session metadata. */
    data class Success(
        val extensionName: String,
        val blenderVersion: String,
        val blenderExe: String,
        val server: String,
        val port: Int,
        val blenderProcessId: Long? = null,
    ) : DebugpyResult()

    /** Failure reason when debugpy startup preconditions are not met. */
    data class Failure(val code: ErrorTypes) : DebugpyResult()
}

/** Helper for validating and controlling debugpy lifecycle actions. */
class DebugpyHelper {
    /**
     * Returns whether the plugin is configured to use debugpy as the DAP backend.
     *
     * @return `true` when debugpy is the active backend; otherwise `false`.
     */
    fun isDebugpyBackend(): Boolean {
        /**
         * Will return the debugpy.dap.is.enable registry key, and ensure it is set to Debugpy
         */
        
        return false
    }

    
    /**
     * Starts a debugpy server for the given extension and returns session metadata.
     *
     * @param extensionName add-on or extension identifier to attach.
     * @return [DebugpyResult.Success] on success, otherwise [DebugpyResult.Failure].
     */
    fun startServer(extensionName: String): DebugpyResult {
        /**
         * This function starts the Debugpy server, first by checking to ensure the user is actually using the debugpy
         */
        if (! isDebugpyBackend()) {
            return DebugpyResult.Failure(ErrorTypes.INVALID_DAP_CONFIG)
        }
        
        // Temporary testing port and server
        val server = "10.100.1.123"
        val port = 5678 // Default port for debugpy
        
        
        
        return DebugpyResult.Success(
            extensionName = extensionName,
            blenderVersion="",
            blenderExe="",
            server="$server:$port", // Using an example server
            port=port // Separate port field provided for inputs that specifically call for the port
        )
    }

    
    /**
     * Stops an active debugpy server.
     *
     * @param serverStatus running server metadata.
     * @return `Unit`.
     */
    fun stopServer(serverStatus: DebugpyResult.Success) {
        /**
         * Assumes Debugpy server is running and tries to kill it, if no server exists will throw an error
         *
         * Uses information structured via the DebugServerStatus data class to close the server
         */
    }

    
    /**
     * Sends a reload command to an active debugpy session.
     *
     * @param serverStatus running server metadata.
     * @return `Unit`.
     */
    fun sendReloadCommand(serverStatus: DebugpyResult.Success) {
        /**
         * Sends a reload command to the debugger instance connected to the Blender executable.
         */
    }

    
    /** Thin command facade for manual debugpy command dispatch. */
    class Send {
        /**
         * Sends a reload instruction to Blender through the debug channel.
         *
         * @return `Unit`.
         */
        fun reload() {
            // Sends a JSON file which initiates an extension reload
        }

        /**
         * Disconnects the active Blender debug session.
         *
         * @return `Unit`.
         */
        fun disconnect() {
            // Disconnects the Blender Instance from the Debugger and IDE.
        }
    }
}
