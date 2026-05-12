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

package com.sakurasedaia.blenderdevelopment.model

enum class ProgressType {
    NONE,
    DOWNLOAD,
    EXTRACT,
    LINTER,
    SANDBOX
}

data class DownloadProgress(
    val isDownloading: Boolean = false,
    val progress: Double = 0.0,
    val statusText: String? = null,
    val version: String? = null,
    val type: ProgressType = ProgressType.NONE
) {
    companion object {
        val None = DownloadProgress()
    }
}
