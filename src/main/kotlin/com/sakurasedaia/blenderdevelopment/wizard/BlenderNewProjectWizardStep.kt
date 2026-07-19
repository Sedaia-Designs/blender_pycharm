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

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.GitNewProjectWizardData
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.intellij.util.text.VersionComparatorUtil
import java.nio.file.Path

/** Wizard step that collects Blender-specific settings and creates project files. */
class BlenderNewProjectWizardStep(parent: NewProjectWizardStep) :
    AbstractNewProjectWizardStep(parent),
    BlenderNewProjectWizardData {
    
    // --- 1. Base Data Mapping ---
    // We point these to the parent's properties so Name/Location are synced
    override val nameProperty: GraphProperty<String> = baseData!!.nameProperty
    override var name: String by nameProperty
    
    private var isCustomized = false
    
    override val pathProperty: GraphProperty<String> = baseData!!.pathProperty
    override var path: String by pathProperty
    
    override val authorNameProperty: GraphProperty<String> = propertyGraph.property(System.getProperty("user.name"))
    override var authorName: String by authorNameProperty
    

    override val descriptionProperty: GraphProperty<String> = propertyGraph.property("")
    override var description: String by descriptionProperty
    
    override val extensionVersionProperty: GraphProperty<String> = propertyGraph.property("0.0.0")
    override var extensionVersion: String by extensionVersionProperty
    
    // --- 2. Blender-Specific Properties ---
    // Initialized via the propertyGraph for reactive UI behavior
    
    val blenderVersionMap: List<String> = BlenderVersions.LIST.map { it.blMajorMinor }
    
    // Initiates the Blender Version with the oldest version supported as the target version.
    override val blenderVersionProperty: GraphProperty<String> = propertyGraph.property(blenderVersionMap.first())
    override var blenderVersion: String by blenderVersionProperty
    
    val addExampleCodeProperty: GraphProperty<Boolean> = propertyGraph.property(true)
    var addExampleCode: Boolean by addExampleCodeProperty
    
    override val manifestIDProperty: GraphProperty<String> = propertyGraph.property(name)
    override var manifestID: String by manifestIDProperty
    
    override val manifestExtensionTypeProperty: GraphProperty<String> =
        propertyGraph.property(BlenderProjectGenerator.PROJECT_TYPE_EXTENSION)
    override var manifestExtensionType: String by manifestExtensionTypeProperty
    
    override val manifestLicenseProperty: GraphProperty<String> = propertyGraph.property("SPDX:GPL-3.0-or-later")
    override var manifestLicense: String by manifestLicenseProperty
    
    override val manifestWebsiteLinkProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestWebsiteLink: String by manifestWebsiteLinkProperty
    
    override val manifestTagsProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestTags: String by manifestTagsProperty
    
    override val manifestMinBlenderVersionProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestMinBlenderVersion: String by manifestMinBlenderVersionProperty
    
    override val manifestMaxBlenderVersionProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestMaxBlenderVersion: String by manifestMaxBlenderVersionProperty
    
    override val manifestFilesPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestFilesPermission: String by manifestFilesPermissionProperty
    
    override val manifestNetworkPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestNetworkPermission: String by manifestNetworkPermissionProperty
    
    override val manifestClipboardPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestClipboardPermission: String by manifestClipboardPermissionProperty
    
    override val manifestCameraPermissionProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestCameraPermission: String by manifestCameraPermissionProperty
    
    override val manifestMicrophonePermissionProperty: GraphProperty<String> = propertyGraph.property("")
    override var manifestMicrophonePermission: String by manifestMicrophonePermissionProperty
    
    
    // TODO: Add options to restrict what platforms can and cannot use the plugin
    val restrictVersionsProperty: GraphProperty<Boolean> = propertyGraph.property(false)
    
    override val blenderDebugSymlinkProperty: GraphProperty<String> = propertyGraph.property("")
    override var blenderDebugSymlinkName: String by blenderDebugSymlinkProperty
    
    // --- 3. UI Layout Props ---
    val pythonVersionData: (String) -> String? = { blMajorMinor -> BlenderVersions.getPythonVersion(blMajorMinor) }
    val pythonVersionProperty: GraphProperty<String> = propertyGraph.property(pythonVersionData(blenderVersion) ?: "")
    var pythonVersion: String by pythonVersionProperty
    
    init {
        nameProperty.afterChange { newName ->
            if (! isCustomized) {
                manifestIDProperty.set(
                    newName.replace(Regex("[\\s-]+"), "_")
                        .replace(Regex("_{2,}"), "_")
                )
            }
        }
        
        blenderVersionProperty.afterChange { newVersion ->
            pythonVersionProperty.set(BlenderVersions.getPythonVersion(newVersion) ?: "")
        }
    }
    
    // --- 4. UI Layout ---
    
    @Suppress("UnstableApiUsage")
    /**
     * Renders the form controls for Blender project options.
     *
     * @param builder UI DSL panel builder for this step.
     * @return `Unit`.
     */
    override fun setupUI(builder: Panel) {
        val extensionTypeOptions = listOf(
            BlenderProjectGenerator.PROJECT_TYPE_EXTENSION,
            BlenderProjectGenerator.PROJECT_TYPE_ADD_ON,
        )
        val noneVersionLabel = MessageBundle.message("ui.project.wizard.option.none")

        with(builder) {
            
            row(MessageBundle.message("ui.project.wizard.row.label.extension.type")) {
                segmentedButton(extensionTypeOptions) {
                    text = when (it) {
                        BlenderProjectGenerator.PROJECT_TYPE_EXTENSION ->
                            MessageBundle.message("ui.project.wizard.option.extension.type.extension")
                        BlenderProjectGenerator.PROJECT_TYPE_ADD_ON ->
                            MessageBundle.message("ui.project.wizard.option.extension.type.addon")
                        else -> it
                    }
                }
                    .bind(manifestExtensionTypeProperty)
            }.bottomGap(BottomGap.SMALL)
            
            group(MessageBundle.message("ui.project.wizard.ui.group.project.label")) {
                row(MessageBundle.message("ui.project.wizard.ui.group.project.author")) {
                    textField().bindText(authorNameProperty)
                }
                row(MessageBundle.message("ui.project.wizard.ui.group.project.blender.version")) {
                    comboBox(BlenderVersions.LIST.map { it.blMajorMinor }).bindItem(blenderVersionProperty)
                }
                row {
                    checkBox(MessageBundle.message("ui.project.wizard.ui.group.project.add_example_code")).bindSelected(addExampleCodeProperty)
                }
                row(MessageBundle.message("ui.project.wizard.ui.group.project.license")) {
                    textField().bindText(manifestLicenseProperty).enabled(false)
                }
                
                row(MessageBundle.message("ui.project.wizard.ui.group.project.website.docs")) {
                    textField().bindText(manifestWebsiteLinkProperty)
                        .validationOnInput {
                            if (!manifestWebsiteLink.startsWith("https://")) {
                                error(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.error.improper_format"))
                            } else {
                                null
                            }
                        }
                        .comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.description"))
                }.visibleIf(manifestExtensionTypeProperty.equalsTo(BlenderProjectGenerator.PROJECT_TYPE_ADD_ON))
                
                row(MessageBundle.message("ui.project.wizard.ui.group.project.description")) {
                    textArea().bindText(descriptionProperty)
                }
            }
            
            group(MessageBundle.message("ui.project.wizard.ui.group.manifest.label")) {
                row(MessageBundle.message("ui.project.wizard.ui.group.manifest.addon_id")) {
                    textField().bindText(manifestIDProperty).applyToComponent {
                        whenTextChangedFromUi {
                            isEnabled = true
                        }
                    }.validationOnInput { sb ->
                        if (! sb.text.matches(Regex("^[a-zA-Z0-9_]*$"))) {
                            error(MessageBundle.message("ui.project.wizard.ui.group.manifest.addon_id.error"))
                        } else {
                            null
                        }
                    }
                }
                
                
                row(MessageBundle.message("ui.project.wizard.ui.group.manifest.min.blender")) {
                    comboBox(BlenderVersions.LIST.map { it.blMajorMinor })
                        .bindItem(manifestMinBlenderVersionProperty)
                        .validationOnInput {// Directly access both properties here
                            val min = manifestMinBlenderVersionProperty.get()
                            val max = manifestMaxBlenderVersionProperty.get()
                            
                            if (max != noneVersionLabel && VersionComparatorUtil.compare(min, max) > 0) {
                                error(MessageBundle.message("ui.project.wizard.ui.group.manifest.min.blender.error"))
                            } else null
                        }
                }
                
                row(MessageBundle.message("ui.project.wizard.ui.group.manifest.max.blender")) {
                    comboBox(listOf(noneVersionLabel) + BlenderVersions.LIST.map { it.blMajorMinor })
                        .bindItem(manifestMaxBlenderVersionProperty)
                        .validationOnInput {val min = manifestMinBlenderVersionProperty.get()
                            val max = manifestMaxBlenderVersionProperty.get()
                            
                            if (max != noneVersionLabel && VersionComparatorUtil.compare(min, max) > 0) {
                                error(MessageBundle.message("ui.project.wizard.ui.group.manifest.max.blender.error"))
                            } else null
                        }
                }
                row(MessageBundle.message("ui.project.wizard.ui.group.manifest.tags.label")) {
                    textField().bindText(manifestTagsProperty)
                        .align(AlignX.FILL)
                        .comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.tags.description"))
                }
                row(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.label")) {
                    textField().bindText(manifestWebsiteLinkProperty)
                        .validationOnInput {
                            if (!manifestWebsiteLink.startsWith("https://")) {
                                error(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.error.improper_format"))
                            } else {
                                null
                            }
                        }
                        .comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.description"))
                    
                }
                group(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.label")) {
                    row {
                        comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.description"))
                    }
                    row(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.files")) {
                        textField().bindText(manifestFilesPermissionProperty)
                            .align(AlignX.FILL)
                    }
                    
                    row(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.network")) {
                        textField().bindText(manifestNetworkPermissionProperty)
                            .align(AlignX.FILL)
                    }
                    
                    row(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.clipboard")) {
                        textField().bindText(manifestClipboardPermissionProperty)
                            .align(AlignX.FILL)
                    }
                    
                    row(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.camera")) {
                        textField().bindText(manifestCameraPermissionProperty)
                            .align(AlignX.FILL)
                    }
                    
                    row(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.microphone")) {
                        textField().bindText(manifestMicrophonePermissionProperty)
                            .align(AlignX.FILL)
                    }
                }
            }.visibleIf(manifestExtensionTypeProperty.equalsTo(BlenderProjectGenerator.PROJECT_TYPE_EXTENSION))
        }
    }
    // --- 5. Project Generation ---
    /**
     * Converts wizard input into [BlenderExtensionManifest] and triggers generation.
     *
     * @param project target project being created.
     * @return `Unit`.
     */
    override fun setupProject(project: Project) {
        val noneVersionLabel = MessageBundle.message("ui.project.wizard.option.none")
        
        val gitData = data.getUserData(GitNewProjectWizardData.KEY)
        
        val isGitInitialized = gitData?.git ?: false
        
        // Call your service to handle file creation
        val data = BlenderExtensionManifest(
            name = name,
            path = path,
            description = description,
            extensionVersion = extensionVersion,
            blenderVersion = blenderVersion,
            addExampleCode = addExampleCode,
            isGitInitialized = isGitInitialized,
            author = authorName,
            projectType = manifestExtensionType,
            extensionId = manifestID,
            projectLicense = manifestLicense,
            minBlenderVersion = manifestMinBlenderVersion,
            maxBlenderVersion = manifestMaxBlenderVersion,
            website = manifestWebsiteLink,
            tags = manifestTags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            filesPermission = manifestFilesPermission,
            networkPermission = manifestNetworkPermission,
            clipboardPermission = manifestClipboardPermission,
            cameraPermission = manifestCameraPermission,
            microphonePermission = manifestMicrophonePermission,
        )
        
        val projectPath: Path = context.projectDirectory
        val baseDir = VfsUtil.findFileByIoFile(projectPath.toFile(), true)
            ?: throw IllegalStateException(MessageBundle.message("ui.project.wizard.error.project.directory.not.found", projectPath.toString()))
        
        BlenderProjectGenerator(data).generateNewProject(project, baseDir)
    }
}
