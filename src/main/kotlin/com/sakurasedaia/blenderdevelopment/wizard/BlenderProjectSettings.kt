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

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle

/** Blender-specific state used by PyCharm's native Python project generator. */
class BlenderProjectSettings(
    initialProjectName: String = MessageBundle.message("ui.project.wizard.default.project.name"),
) : PyV3ProjectTypeSpecificSettings {
    private val propertyGraph = PropertyGraph("BlenderProjectSettings")
    private var isManifestIdCustomized = false

    val projectNameProperty: GraphProperty<String> = propertyGraph.property(initialProjectName)
    var projectName: String by projectNameProperty

    val authorNameProperty: GraphProperty<String> = propertyGraph.property(System.getProperty("user.name", ""))
    var authorName: String by authorNameProperty

    val descriptionProperty: GraphProperty<String> = propertyGraph.property("")
    var description: String by descriptionProperty

    val extensionVersionProperty: GraphProperty<String> = propertyGraph.property("0.0.0")
    var extensionVersion: String by extensionVersionProperty

    val blenderVersionProperty: GraphProperty<String> = propertyGraph.property(BlenderVersions.LIST.first().blMajorMinor)
    var blenderVersion: String by blenderVersionProperty

    val addExampleCodeProperty: GraphProperty<Boolean> = propertyGraph.property(true)
    var addExampleCode: Boolean by addExampleCodeProperty

    val manifestIdProperty: GraphProperty<String> = propertyGraph.property(normalizeModuleName(initialProjectName))
    var manifestId: String by manifestIdProperty

    val manifestExtensionTypeProperty: GraphProperty<String> =
        propertyGraph.property(BlenderProjectGenerator.PROJECT_TYPE_EXTENSION)
    var manifestExtensionType: String by manifestExtensionTypeProperty

    val manifestLicenseProperty: GraphProperty<String> = propertyGraph.property("SPDX:GPL-3.0-or-later")
    var manifestLicense: String by manifestLicenseProperty

    val manifestWebsiteLinkProperty: GraphProperty<String> = propertyGraph.property("")
    var manifestWebsiteLink: String by manifestWebsiteLinkProperty

    val manifestTagsProperty: GraphProperty<String> = propertyGraph.property("")
    var manifestTags: String by manifestTagsProperty

    val manifestMinBlenderVersionProperty: GraphProperty<String> =
        propertyGraph.property(BlenderVersions.LIST.first().blMajorMinor)
    var manifestMinBlenderVersion: String by manifestMinBlenderVersionProperty

    val manifestMaxBlenderVersionProperty: GraphProperty<String> =
        propertyGraph.property(MessageBundle.message("ui.project.wizard.option.none"))
    var manifestMaxBlenderVersion: String by manifestMaxBlenderVersionProperty

    /** Selected maximum Blender version, or `null` when the range is open-ended. */
    val maximumBlenderVersion: String?
        get() = manifestMaxBlenderVersion.takeUnless {
            it == MessageBundle.message("ui.project.wizard.option.none")
        }

    val manifestFilesPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    var manifestFilesPermission: String by manifestFilesPermissionProperty

    val manifestNetworkPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    var manifestNetworkPermission: String by manifestNetworkPermissionProperty

    val manifestClipboardPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    var manifestClipboardPermission: String by manifestClipboardPermissionProperty

    val manifestCameraPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    var manifestCameraPermission: String by manifestCameraPermissionProperty

    val manifestMicrophonePermissionProperty: GraphProperty<String> = propertyGraph.property("")
    var manifestMicrophonePermission: String by manifestMicrophonePermissionProperty

    /**
     * Updates the generated project name and keeps the add-on ID synchronized until it is customized.
     *
     * @param newProjectName current project-directory name from PyCharm.
     */
    fun updateProjectName(newProjectName: String) {
        projectName = newProjectName
        if (!isManifestIdCustomized) {
            manifestId = normalizeModuleName(newProjectName)
        }
    }

    /** Marks the add-on ID as user-controlled so later path edits do not overwrite it. */
    fun markManifestIdCustomized() {
        isManifestIdCustomized = true
    }

    /**
     * Converts the mutable form state into the immutable scaffolding payload.
     *
     * @param projectPath generated project root path.
     * @param isGitInitialized whether PyCharm initialized Git for the project.
     * @return manifest payload consumed by [BlenderProjectGenerator].
     */
    fun toManifest(projectPath: String, isGitInitialized: Boolean): BlenderExtensionManifest =
        BlenderExtensionManifest(
            name = projectName,
            path = projectPath,
            description = description,
            extensionVersion = extensionVersion,
            blenderVersion = blenderVersion,
            addExampleCode = addExampleCode,
            isGitInitialized = isGitInitialized,
            author = authorName,
            projectType = manifestExtensionType,
            extensionId = manifestId,
            projectLicense = manifestLicense,
            minBlenderVersion = manifestMinBlenderVersion,
            maxBlenderVersion = maximumBlenderVersion.orEmpty(),
            website = manifestWebsiteLink,
            tags = manifestTags.split(',').map(String::trim).filter(String::isNotEmpty),
            filesPermission = manifestFilesPermission,
            networkPermission = manifestNetworkPermission,
            clipboardPermission = manifestClipboardPermission,
            cameraPermission = manifestCameraPermission,
            microphonePermission = manifestMicrophonePermission,
        )

    /**
     * Generates Blender files after PyCharm creates and assigns the selected Python SDK.
     *
     * @param module module supplied by PyCharm's project generator.
     * @param baseDir generated project root.
     * @param sdk Python SDK selected or created by PyCharm.
     * @return successful generation result, or the scaffolding failure reported by PyCharm.
     */
    override suspend fun generateProject(module: Module, baseDir: VirtualFile, sdk: Sdk): PyResult<Unit> {
        updateProjectName(baseDir.name)
        val isGitInitialized = baseDir.findChild(".git")?.isDirectory == true
        return BlenderProjectGenerator(toManifest(baseDir.path, isGitInitialized)).generateNewProject(module, baseDir, sdk)
    }

    private companion object {
        /**
         * Converts a project name to the Python-module-compatible default used for the add-on ID.
         *
         * @param value project name to normalize.
         * @return normalized add-on ID.
         */
        fun normalizeModuleName(value: String): String =
            value.replace(Regex("[\\s-]+"), "_").replace(Regex("_{2,}"), "_")
    }
}
