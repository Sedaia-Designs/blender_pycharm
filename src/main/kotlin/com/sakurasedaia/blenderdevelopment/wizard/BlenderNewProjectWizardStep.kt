/**
 * This module defines the UI itself for the New Project Wizard API
 */
package com.sakurasedaia.blenderdevelopment.wizard

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.sakurasedaia.blenderdevelopment.common.MessageBundle
import com.sakurasedaia.blenderdevelopment.model.BlenderVersions
import kotlin.collections.map

class BlenderNewProjectWizardStep(parent: NewProjectWizardBaseStep) :
    AbstractNewProjectWizardStep(parent),
    BlenderNewProjectWizardData {
    
    // --- 1. Base Data Mapping ---
    // We point these to the parent's properties so Name/Location are synced
    override val nameProperty: GraphProperty<String> = baseData!!.nameProperty
    override val pathProperty: GraphProperty<String> = baseData!!.pathProperty
    
    override var name: String by nameProperty
    override var path: String by pathProperty
    
    // --- 2. Blender Specific Properties ---
    // Initialized via the propertyGraph for reactive UI behavior
    override val addBlenderManifestProperty: GraphProperty<Boolean> = propertyGraph.property(true)
    override var addBlenderManifest: Boolean by addBlenderManifestProperty
    
    override val blenderVersionProperty: GraphProperty<String> = propertyGraph.property("")
    override var blenderVersion: String by blenderVersionProperty
    
    // --- 3. UI Layout ---
    override fun setupUI(builder: Panel) {
        with(builder) {
            row {
                checkBox(MessageBundle.message("npw.setting.label.manifest.toggle"))
                    .bindSelected(addBlenderManifestProperty)
            }
            row {
                comboBox(BlenderVersions.LIST.map { it.blMajorMinor }).bindItem(blenderVersionProperty)
            }
            // Group visible when checkbox is CHECKED
            group(MessageBundle.message("npw.setting.group.manifest.title")) {
                row(MessageBundle.message("npw.setting.group.manifest.blender.version.label")) {
                    textField().comment(MessageBundle.message("npw.setting.group.manifest.blender.version.comment"))
                }
            }.visibleIf(addBlenderManifestProperty)
            
            // Group visible when checkbox is UNCHECKED
            group(MessageBundle.message("npw.setting.group.bl_info.title")) {
                row {
                    label("This will use the classic __init__.py structure.")
                }
            }.visibleIf(addBlenderManifestProperty.not())
        }
    }
    
    // --- 4. Project Generation ---
    override fun setupProject(project: Project) {
        // Here, the data is already captured in the properties
        val manifestRequired = addBlenderManifest
        val projectName = name
        val targetPath = path
        
        // Call your service to handle file creation
        // BlenderProjectGenerator.generate(project, targetPath, manifestRequired)
    }
}