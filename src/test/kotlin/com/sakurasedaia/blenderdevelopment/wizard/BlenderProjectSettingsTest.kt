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

package com.sakurasedaia.blenderdevelopment.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests Blender form state and its immutable generation payload. */
class BlenderProjectSettingsTest {
    /** Verifies that automatic stub installation is enabled for new projects. */
    @Test
    fun `stub installation defaults to enabled`() {
        assertTrue(BlenderProjectSettings("Sample").installBlenderApiStubs)
    }

    /** Verifies that project-name changes derive an add-on ID until the user customizes it. */
    @Test
    fun `project name synchronizes default manifest id until customized`() {
        val settings = BlenderProjectSettings("Initial Project")

        settings.updateProjectName("My  Blender-Project")
        assertEquals("My_Blender_Project", settings.manifestId)

        settings.manifestId = "custom_addon"
        settings.markManifestIdCustomized()
        settings.updateProjectName("Renamed Project")

        assertEquals("Renamed Project", settings.projectName)
        assertEquals("custom_addon", settings.manifestId)
    }

    /** Verifies that the Python recommendation follows the selected target Blender version. */
    @Test
    fun `recommended Python version follows target Blender selection`() {
        val settings = BlenderProjectSettings("Sample")

        assertEquals("Recommended Python version: 3.11.7", settings.recommendedPythonVersionCommentProperty.get())

        settings.blenderVersion = "4.5"
        assertEquals("Recommended Python version: 3.11.9", settings.recommendedPythonVersionCommentProperty.get())

        settings.blenderVersion = "5.2"
        assertEquals("Recommended Python version: 3.13.13", settings.recommendedPythonVersionCommentProperty.get())
    }

    /** Verifies that every editable setting is transferred to the scaffolding manifest. */
    @Test
    fun `settings convert to complete manifest payload`() {
        val settings = BlenderProjectSettings("Sample Project").apply {
            authorName = "Sakura"
            description = "Example"
            extensionVersion = "1.2.3"
            blenderVersion = "4.5"
            addExampleCode = false
            installBlenderApiStubs = false
            manifestId = "sample_project"
            manifestExtensionType = BlenderProjectGenerator.PROJECT_TYPE_EXTENSION
            manifestLicense = "SPDX:GPL-3.0-or-later"
            manifestWebsiteLink = "https://example.com"
            manifestTags = "Animation, Rigging, , Utilities"
            manifestMinBlenderVersion = "4.2"
            manifestMaxBlenderVersion = "4.5"
            manifestFilesPermission = "Read assets"
            manifestNetworkPermission = "Download metadata"
            manifestClipboardPermission = "Copy values"
            manifestCameraPermission = "Capture reference"
            manifestMicrophonePermission = "Record reference"
        }

        val manifest = settings.toManifest("/projects/sample", isGitInitialized = true)

        assertEquals("Sample Project", manifest.name)
        assertEquals("/projects/sample", manifest.path)
        assertEquals("Example", manifest.description)
        assertEquals("1.2.3", manifest.extensionVersion)
        assertEquals("4.5", manifest.blenderVersion)
        assertFalse(manifest.addExampleCode)
        assertFalse(manifest.installBlenderApiStubs)
        assertTrue(manifest.isGitInitialized)
        assertEquals("Sakura", manifest.author)
        assertEquals("sample_project", manifest.extensionId)
        assertEquals("4.2", manifest.minBlenderVersion)
        assertEquals("4.5", manifest.maxBlenderVersion)
        assertEquals(listOf("Animation", "Rigging", "Utilities"), manifest.tags)
        assertEquals("Read assets", manifest.filesPermission)
        assertEquals("Download metadata", manifest.networkPermission)
        assertEquals("Copy values", manifest.clipboardPermission)
        assertEquals("Capture reference", manifest.cameraPermission)
        assertEquals("Record reference", manifest.microphonePermission)
    }

    /** Verifies that omitting a maximum Blender version produces an open-ended manifest range. */
    @Test
    fun `missing maximum version remains open ended`() {
        val settings = BlenderProjectSettings("Sample")

        assertNull(settings.maximumBlenderVersion)
        assertEquals("", settings.toManifest("/projects/sample", false).maxBlenderVersion)
    }
}
