/**
 * Data Storage of the project wizard, can be called by other functions later on, for example,
 * gitignore, License File (If Applicable), and Agent Guidelines.
 */
package com.sakurasedaia.blenderdevelopment.wizard

import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.openapi.observable.properties.GraphProperty

interface BlenderNewProjectWizardData : NewProjectWizardBaseData {
    val addBlenderManifestProperty: GraphProperty<Boolean>
    var addBlenderManifest: Boolean
    
    // You can add more shared properties here (e.g., Blender Path, API Version)
    val blenderVersionProperty: GraphProperty<String>
    var blenderVersion: String
}