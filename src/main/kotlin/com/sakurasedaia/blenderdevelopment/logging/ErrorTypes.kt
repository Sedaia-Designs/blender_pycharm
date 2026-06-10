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

package com.sakurasedaia.blenderdevelopment.logging

/** Canonical plugin error codes used by logging and diagnostics. */
enum class ErrorTypes(val message: String) {
    UNSUPPORTED_OS("[BL-001]: User's OS is not a compatible type, Supported Operating Systems: Windows, MacOS, and Linux (Or alternate Linux Kernel Fork)"),
    INVALID_DAP_CONFIG("[BL-002]: INVALID_DAP_CONFIG is not set to debugpy, could not start process"),
    ARCHIVE_FORMAT_UNSUPPORTED("[BL-003]: Archive Format not supported"),
    NOT_BLENDER_BUNDLE("[BL-004]: Bundle provided is not a Portable Blender bundle"),
    UNSUPPORTED_ARCH("[BL-005]: Unsupported CPU Architecture"),
    DOWNLOAD_ERROR("[BL-006]: Failed to download Blender bundle"),
    DMG_EXTRACTION_FAILED("[BL-007]: Failed to extract DMG Bundle"),
    ;

    /**
     * Returns the human-readable message for display/logging.
     *
     * @return stable user-facing error text for this enum value.
     */
    override fun toString(): String = message
}
