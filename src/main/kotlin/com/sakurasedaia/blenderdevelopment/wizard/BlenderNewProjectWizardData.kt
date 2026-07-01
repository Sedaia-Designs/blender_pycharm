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

import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.openapi.observable.properties.GraphProperty

/** Contract for state exchanged between the Blender new-project UI and generator. */
interface BlenderNewProjectWizardData : NewProjectWizardBaseData {
    // This property will determine the minimum version of Blender Desired.
    val blenderVersionProperty: GraphProperty<String>
    var blenderVersion: String
    
    val authorNameProperty: GraphProperty<String>
    var authorName: String

    val descriptionProperty: GraphProperty<String>
    var description: String
    
    val extensionVersionProperty: GraphProperty<String>
    var extensionVersion: String
    // This Prop will be in the form of a Combo Box, having three options: extension, add-on and theme
    // Extension will be the default, and add in the Blender manifest.
    val manifestExtensionTypeProperty: GraphProperty<String>
    var manifestExtensionType: String
    
    val manifestIDProperty: GraphProperty<String>
    var manifestID: String
    
    // This value will be locked to just `SPDX:GPL-3.0-or-later` due to Blender requiring the use of the copyleft license GPL V3
    val manifestLicenseProperty: GraphProperty<String>
    var manifestLicense: String
    
    val manifestWebsiteLinkProperty: GraphProperty<String>
    var manifestWebsiteLink: String
    
    // Separated by Commas, will automatically form the list based off that. https://docs.blender.org/manual/en/dev/advanced/extensions/tags.html
    val manifestTagsProperty: GraphProperty<String>
    var manifestTags: String
    
    
    val manifestMinBlenderVersionProperty: GraphProperty<String>
    var manifestMinBlenderVersion: String
    
    val manifestMaxBlenderVersionProperty: GraphProperty<String>
    var manifestMaxBlenderVersion: String
    
    // TODO: Impement a system to help users restrict what OS's can and cannot use this plugin
    val blenderDebugSymlinkProperty: GraphProperty<String>
    var blenderDebugSymlinkName: String
    
    // If the reason is left blank or has only spaces, tabs, or any other symbole it will not be added to the manifest.
    val manifestFilesPermissionProperty: GraphProperty<String>
    var manifestFilesPermission: String
    
    val manifestNetworkPermissionProperty: GraphProperty<String>
    var manifestNetworkPermission: String
    
    val manifestClipboardPermissionProperty: GraphProperty<String>
    var manifestClipboardPermission: String
    
    val manifestCameraPermissionProperty: GraphProperty<String>
    var manifestCameraPermission: String
    
    val manifestMicrophonePermissionProperty: GraphProperty<String>
    var manifestMicrophonePermission: String
}
