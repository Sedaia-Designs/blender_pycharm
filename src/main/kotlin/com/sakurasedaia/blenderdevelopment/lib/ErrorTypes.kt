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
 * Canonical plugin error codes used by logging and diagnostics.
 *
 * @param message Human-readable error message for this error code.
 */
enum class ErrorTypes(val message: String) {
    UNSUPPORTED_OS("[BL-001]: User's OS is not a compatible type, Supported Operating Systems: Windows, MacOS, and Linux (Or alternate Linux Kernel Fork)"),
    BLENDER_LAUNCH_ERROR("[BL-002]: Failed to launch Blender"),
    ARCHIVE_EXTRACTION_ERROR("[BL-003]: Failed to extract archive"),
    UNKNOWN_DOWNLOAD_URL("[BL-004]: Could not get the download URL for that version.")
    ;

    /**
     * Returns the human-readable message for display/logging.
     *
     * @return stable user-facing error text for this enum value.
     */
    override fun toString(): String = message
}
