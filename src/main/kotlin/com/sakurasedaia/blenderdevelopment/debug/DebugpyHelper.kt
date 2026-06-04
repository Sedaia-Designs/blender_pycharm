package com.sakurasedaia.blenderdevelopment.debug

import com.sakurasedaia.blenderdevelopment.logging.ErrorTypes

sealed class DebugpyResult {
    data class Success(
        val extensionName: String,
        val blenderVersion: String,
        val blenderExe: String,
        val server: String,
        val port: Int,
        val blenderProcessId: Long? = null,
    ) : DebugpyResult()
    data class Failure(val code: ErrorTypes) : DebugpyResult()
}

class DebugpyHelper {
    fun isDebugpyBackend(): Boolean {
        /**
         * Will return the debugpy.dap.is.enable registry key, and ensure it is set to Debugpy
         */
        
        return false
    }
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
    
    fun stopServer(serverStatus: DebugpyResult.Success) {
        /**
         * Assumes Debugpy server is running and tries to kill it, if no server exists will throw an error
         *
         * Uses information structured via the DebugServerStatus data class to close the server
         */
    }
    
    fun sendReloadCommand(serverStatus: DebugpyResult.Success) {
        /**
         * Sends a reload command to the debugger instance connected to the Blender executable.
         */
    }
    
    class Send {
        fun reload() {
            // Sends a JSON file which initiates an extension reload
        }
        
        fun disconnect() {
            // Disconnects the Blender Instance from the Debugger and IDE.
        }
    }
}