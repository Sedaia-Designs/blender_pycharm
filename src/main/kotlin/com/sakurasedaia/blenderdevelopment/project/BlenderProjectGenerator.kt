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

package com.sakurasedaia.blenderdevelopment.project

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.model.BlenderVersions
import kotlinx.io.IOException
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.util.Properties
import com.sakurasedaia.blenderdevelopment.common.MessageBundle

data class ProjectConfig (
    // Base Project Info
    val name: String,
    val path: String,
    val description: String,
    val extensionVersion: String,
    val isGitInitialized: Boolean,
    
    // Blender Environment
    val blenderVersion: String,
    val addExampleCode: Boolean,
    val author: String,
    val initiateUvInstance: Boolean,
    val projectType: String,
    
    // Blender Manifest Mandatory Settings
    val extensionId: String,
    val projectLicense: String,
    val minBlenderVersion: String,
    val maxBlenderVersion: String,
    
    // Blender Manifest Optional Settings
    val website: String,
    val tags: List<String>,
    
    // Permissions
    val filesPermission: String,
    val networkPermission: String,
    val clipboardPermission: String,
    val cameraPermission: String,
    val microphonePermission: String,
)


/**
 * The `BlenderProjectGenerator` class is the core logic and distribution center of
 * the new project wizard. Its primary purpose is to create the new project directory
 * based on the user provided input, and the ProjectConfig data class passed to it by
 * the NewProjectWizard. On top of handling the project creation, the generator functions
 * below the main `generate` function can be called after project creation
 */
class BlenderProjectGenerator(val data: ProjectConfig) {
    /**
     * Must only be called by the NewProjectWizard, as it generates the new Project itself.
     */
    fun generateNewProject(project: Project, baseDir: VirtualFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val sourceDir = baseDir.findChild("src") ?: baseDir.createChildDirectory(this, "src")
                
                // Mark the SRC Directory as the source root
                // 1. Get the module
                val module = ModuleManager.getInstance(project).modules.firstOrNull()
                
                if (module != null) {
                    // 2. Get the Modifiable Model
                    val model = ModuleRootManager.getInstance(module).modifiableModel
                    
                    // 3. Find the content entry for our project path
                    val contentEntry = model.contentEntries.find {
                        it.file == baseDir || (it.file != null && VfsUtil.isAncestor(it.file!!, baseDir, false))
                    }
                    
                    // 4. Mark as source root
                    contentEntry?.addSourceFolder(sourceDir, JavaSourceRootType.SOURCE)
                    
                    // 5. Commit the model (This saves the change)
                    model.commit()
                }
                
                // Run Generators
                
                // Necessary Components for a Blender Project
                if (data.projectType != "Add-on" ) generateManifest(project, sourceDir)
                generateMainScript(project, sourceDir)
                
                // Repository Extras
                if (data.initiateUvInstance) generatePyproject(project, baseDir)
                if (data.isGitInitialized) {
                    generateGitIgnore(project, baseDir)
                    generateReadme(project, baseDir)
                }
                generateLicense(project, baseDir)
                
            } catch (e: IOException) {
            
            }
        }
        
        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)
    }
    
    private fun createFromTemplate(
        project: Project,
        targetName: String,
        templateName: String? = null,
        parentDir: VirtualFile,
        internal: Boolean = false,
        vararg args: Pair<String, String?>
    ): String {
        val templateManager = FileTemplateManager.getInstance(project)
        val templateFileName = templateName ?: targetName
        
        val template = when (internal) {
            true -> templateManager.getInternalTemplate(templateFileName)
            else -> templateManager.getTemplate(templateFileName)
        } ?: throw IllegalStateException(MessageBundle.message("project.wizard.error.project.template.file.missing", templateFileName))
        if (template.text.isEmpty()) throw IllegalStateException(MessageBundle.message("project.wizard.error.project.template.template.missing", templateFileName))
        
        val templateProps = Properties(templateManager.defaultProperties)
        
        args.forEach { (name, value) ->
            if (value != null) {
                templateProps.setProperty(name, value)
            }
        }
        
        val result = template.getText(templateProps)
        
        val file = parentDir.findChild(targetName) ?: parentDir.createChildData(this, targetName)
        file.setBinaryContent(result.toByteArray())
        return result
    }
    
    fun generatePyproject(project: Project, baseDir: VirtualFile) {
        createFromTemplate(
            project,
            targetName="pyproject.toml",
            templateName = "Pyproject",
            parentDir=baseDir,
            internal=true,
            Pair("name", data.name),
            Pair("version", data.extensionVersion),
            Pair("python", BlenderVersions.getPythonVersion(data.blenderVersion)),
            Pair("license", data.projectLicense),
        )
    }
    fun generateManifest(project: Project, baseDir: VirtualFile) {
        // Helper to ensure empty strings are passed instead of nulls for Velocity logic
        fun String?.valOrEmpty(): String = if (this.isNullOrBlank()) "" else this
        
        // Format the list of strings into a TOML array: ["tag1", "tag2"]
        val formattedTags = data.tags.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        
        // Convert Extension to 'add-on', since the type key in the blender_manifest expects "add-on" or "theme", not extension.
        val projectType = when (data.projectType) {
            "Extension" -> "add-on"
            "Theme" -> "theme"
            else -> ""
        }
        
        createFromTemplate(
            project = project,
            targetName = "blender_manifest.toml",
            templateName = "BlenderManifest",
            parentDir = baseDir,
            internal = true,
            
            // Base Info
            "extensionId" to data.extensionId,
            "extensionVersion" to data.extensionVersion,
            "name" to data.name,
            "description" to data.description,
            "author" to data.author,
            "extensionType" to projectType,
            
            // Mandatory & Optional Blender Settings
            "website" to data.website.valOrEmpty(),
            "tags" to formattedTags,
            "minBlenderVersion" to data.minBlenderVersion,
            "maxBlendVersion" to data.maxBlenderVersion.valOrEmpty(),
            "license" to data.projectLicense,
            
            // Permissions (Mapped from textFields)
            "network" to data.networkPermission.valOrEmpty(),
            "files" to data.filesPermission.valOrEmpty(),
            "clipboard" to data.clipboardPermission.valOrEmpty(),
            "camera" to data.cameraPermission.valOrEmpty(),
            "microphone" to data.microphonePermission.valOrEmpty()
        )
    }
    fun generateMainScript(project: Project, baseDir: VirtualFile) {
        // Helper for strings
        fun String?.valOrEmpty(): String = if (this.isNullOrBlank()) "" else this
        
        createFromTemplate(
            project = project,
            targetName = "__init__.py",
            templateName = "NewProjectMainScript", // Ensure this matches your plugin.xml registration
            parentDir = baseDir,
            internal = true,
            
            // Flags for the #if blocks
            "newProject" to "true", // Usually true for a new wizard project
            "exampleCode" to data.addExampleCode.toString(),
            
            // Metadata
            "name" to data.name,
            "author" to data.author,
            "version" to data.extensionVersion.valOrEmpty(),
            "blenderVersion" to data.minBlenderVersion.valOrEmpty(),
            "description" to data.description.valOrEmpty()
        )
    }
    fun generateGitIgnore(project: Project, baseDir: VirtualFile) {
        createFromTemplate(
            project,
            targetName=".gitignore",
            templateName = "GitIgnore",
            parentDir = baseDir,
            internal = true,
        )
    }
    fun generateLicense(project: Project, baseDir: VirtualFile) {
        createFromTemplate(
            project,
            targetName="LICENSE",
            templateName = "GplLicenseV3",
            parentDir = baseDir,
            internal = true
        )
    }
    fun generateReadme(project: Project, baseDir: VirtualFile) {
        fun String?.valOrEmpty(): String = if (this.isNullOrBlank()) "" else this
        createFromTemplate(
            project,
            targetName="README.md",
            templateName = "README",
            parentDir = baseDir,
            internal = true,
            
            // Metadata
            "name" to data.name.valOrEmpty(),
            "description" to data.description.valOrEmpty(),
        )
    }
}