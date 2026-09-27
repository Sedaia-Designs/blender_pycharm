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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Validates a resolved Blender startup-file path without depending on Swing. */
internal object BlendFileToOpenValidator {
    /** Returns the presentation state for a resolved Blender startup-file path. */
    fun validate(value: String): BlendFileValidation {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return BlendFileValidation.NONE

        val path =
            try {
                Path.of(trimmed)
            } catch (_: InvalidPathException) {
                return BlendFileValidation.INVALID_PATH
            }
        if (path.fileName?.toString()?.endsWith(".blend", ignoreCase = true) != true) {
            return BlendFileValidation.WRONG_EXTENSION
        }
        if (!Files.exists(path)) return BlendFileValidation.MISSING
        if (!Files.isRegularFile(path)) return BlendFileValidation.NOT_FILE
        return BlendFileValidation.NONE
    }
}
