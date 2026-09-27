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

package com.sakurasedaia.blenderdevelopment.lib

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlenderManifestTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `reads required nested and optional manifest values`() {
        val manifestFile = temporaryFolder.newFile("blender_manifest.toml")
        manifestFile.writeText(
            """
            schema_version = "1.0.0"
            id = "sample_extension"
            version = "1.2.3"
            name = "Sample Extension"
            tagline = "Exercises manifest parsing"
            maintainer = "Example Maintainer"
            type = "add-on"
            blender_version_min = "4.2.0"
            license = ["SPDX:GPL-3.0-or-later"]
            tags = ["Development"]
            unknown_future_key = "ignored"

            [permissions]
            network = "Checks for updates"

            [build]
            paths_exclude_pattern = ["__pycache__/"]
            """
                .trimIndent()
        )

        val manifest = BlenderManifest(manifestFile.path)

        assertEquals("sample_extension", manifest.id)
        assertEquals(listOf("Development"), manifest.tags)
        assertEquals("Checks for updates", manifest.permissions?.network)
        assertEquals(listOf("__pycache__/"), manifest.build?.paths_exclude_pattern)
    }

    @Test
    fun `rejects malformed manifest content`() {
        val manifestFile = temporaryFolder.newFile("blender_manifest.toml")
        manifestFile.writeText("id = [")

        assertThrows(SerializationException::class.java) {
            BlenderManifest(manifestFile.path)
        }
    }
}
