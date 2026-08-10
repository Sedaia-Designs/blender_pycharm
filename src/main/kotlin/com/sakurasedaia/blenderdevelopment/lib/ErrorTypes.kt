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

package com.sakurasedaia.blenderdevelopment.lib

/**
 * Represents various error types and their corresponding messages used within the application.
 * Each enum value defines a unique error code and a human-readable message for display or logging purposes.
 */
enum class ErrorTypes(private val message: String) {
    UNSUPPORTED_OS(
        "[BL-001]: User's OS is not a compatible type, Supported Operating Systems: " +
            "Windows, MacOS, and Linux (Or alternate Linux Kernel Fork)",
    ),
    BLENDER_LAUNCH_ERROR("[BL-002]: Failed to launch Blender"),
    ARCHIVE_EXTRACTION_ERROR("[BL-003]: Failed to extract archive"),
    UNKNOWN_DOWNLOAD_URL("[BL-004]: Could not get the download URL for that version."),

    // Process Execution Failures [BL-100]
    PROCESS_EXECUTION_FAILED("[BL-100]: Failed to execute `{0}`"),
    PROCESS_EXECUTION_INTERRUPTED("[BL-101]: Interrupted while executing `{0}`"),

    // Blender Runtime Failures [BL-200]
    RUNTIME_PAYLOAD_HANDLING_FAILED("[BL-200]: Failed to handle Blender runtime payload."),
    SETUP_PAYLOAD_MISSING_IDENTIFIER("[BL-201]: Blender setup payload is missing identifier: {0}"),
    SETUP_PAYLOAD_UNKNOWN_SESSION("[BL-202]: Blender setup payload received for unknown session identifier `{0}`."),
    RUNTIME_BOOTSTRAP_FAILED("[BL-203]: Blender runtime bootstrap reported failure: {0}"),
    RUNTIME_BOOTSTRAP_FAILED_WITH_DETAILS("[BL-204]: Blender runtime bootstrap reported failure: {0} ({1})"),
    DEBUG_ATTACH_FAILED("[BL-205]: Failed to attach Python debugger to Blender runtime session `{0}`."),
    RUNTIME_RELOAD_MISSING_ADDON_DIRECTORIES(
        "[BL-206]: Skipped Blender runtime reload command because no configured add-on directories are available.",
    ),
    RUNTIME_COMMAND_MISSING_SESSION("[BL-207]: Skipped Blender runtime command `{0}` because no active session is available."),
    RUNTIME_COMMAND_INVALID_PORT("[BL-208]: Skipped Blender runtime command `{0}` because session `{1}` reported invalid port {2}."),
    RUNTIME_COMMAND_REJECTED("[BL-209]: Blender runtime command `{0}` was rejected by session `{1}` with HTTP {2}: {3}"),
    RUNTIME_COMMAND_SEND_FAILED("[BL-210]: Failed to send Blender runtime command `{0}`."),

    // Install Scanner Failures [BL-300]
    INSTALL_SCAN_FAIL_GENERIC("[BL-300]: User-initiated Blender installation scan failed."),
    INSTALL_SCAN_DEADLINE_EXCEEDED("[BL-301]: User-initiated Blender installation scan exceeded its deadline."),
    INSTALL_VERSION_PROBE_FAILED("[BL-302]: Version probe failed for `{0}`"),
    INSTALL_SCAN_NO_INSTALLS("[BL-303]: No Blender installations detected on {0}."),
    INSTALL_SCAN_NO_INSTALLS_WITH_SKIPPED_ROOTS(
        "[BL-304]: No Blender installations detected on {0}. Skipped {1} inaccessible install root(s).",
    ),

    // Configuration and Bootstrap Failures [BL-400]
    VERSION_CACHE_REFRESH_FAILED("[BL-400]: Failed to refresh the Blender version cache."),
    BOOTSTRAP_CLEANUP_FAILED("[BL-401]: Failed to run stale bootstrap cleanup."),
    BOOTSTRAP_SCRIPT_DELETE_FAILED("[BL-402]: Failed to delete bootstrap script at `{0}`."),
    BOOTSTRAP_SCRIPT_SCAN_FAILED("[BL-403]: Failed while scanning stale bootstrap scripts in `{0}`."),
    PROJECT_GENERATION_FAILED("[BL-404]: Project generation failed for {0} at {1}"),

    // Stub Management Failures [BL-500]
    STUB_INTERPRETER_MISSING("[BL-500]: Python interpreter is unavailable for Blender stub management."),
    STUB_OPERATION_FAILED("[BL-501]: Unexpected Blender stub operation failure: {0}"),
    STUB_VERSION_UNSUPPORTED("[BL-502]: Blender version {0} does not have a supported stub package."),
    STUB_UNINSTALL_FAILED("[BL-503]: Failed to uninstall Blender stub package {0}: {1}"),
    STUB_METADATA_FAILED("[BL-504]: Failed to retrieve metadata for Blender stub package {0}: {1}"),
    STUB_INSTALL_FAILED("[BL-505]: Failed to install Blender stub package {0}: {1}"),

    // Notification Failures [BL-600]
    NOTIFICATION_ERROR("[BL-600]: Error notification displayed: {0}"),
    ;

    /**
     * Substitutes indexed placeholders such as `{0}` and `{1}` with the supplied parameters.
     *
     * @param parameters values substituted into the message template by index.
     * @return formatted error text containing the stable error code.
     */
    fun format(vararg parameters: Any?): String = PLACEHOLDER_PATTERN.replace(message) { match ->
        val index = match.groupValues[1].toInt()
        if (index in parameters.indices) parameters[index].toString() else match.value
    }

    /**
     * Returns the human-readable message for display/logging.
     *
     * @return stable user-facing error text for this enum value.
     */
    override fun toString(): String = message

    private companion object {
        val PLACEHOLDER_PATTERN = Regex("\\{(\\d+)}")
    }
}
