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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.util.PluginResources
import org.jetbrains.jps.model.java.JavaSourceRootType

/** Immutable configuration payload consumed by [BlenderProjectGenerator]. */
data class BlenderExtensionManifest (
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
 * based on the user provided input, and the BlenderExtensionManifest data class passed to it by
 * the NewProjectWizard. On top of handling the project creation, the generator functions
 * below the main `generate` function can be called after project creation
 */
class BlenderProjectGenerator(val data: BlenderExtensionManifest) {
    /**
     * Must only be called by the NewProjectWizard, as it generates the new Project itself.
     *
     * @param project project being initialized.
     * @param baseDir project root directory.
     * @return `Unit`.
     */
    fun generateNewProject(project: Project, baseDir: VirtualFile) {
        val logger = PluginLogger.getInstance(project)
        val notifications = NotificationModal.getInstance(project)
        logger.log("Creating new project for ${data.name} at ${baseDir.path}")
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
                
                // Necessary Components for a Blender Project
                if (data.projectType != PROJECT_TYPE_ADD_ON) generateManifest(project, sourceDir)
                generateMainScript(project, sourceDir)
                
                // Repository Extras
                if (data.isGitInitialized) {
                    logger.log("Initializing Git instance")
                    generateGitIgnore(project, baseDir)
                    generateReadme(project, baseDir)
                }
                generateLicense(project, baseDir)
                
                // Generate PyProject.Toml for UV
                generatePyproject(project, baseDir)
                
            } catch (e: Exception) {
                logger.warn("Project generation failed for ${data.name} at ${baseDir.path}", e)
                notifications.sendError(MessageBundle.message("notification.project.generation.failed"), throwable = e)
            }
        }
        
        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)
         
        @Suppress("UNUSED_VARIABLE", "unused")
        val projectConfig = ProjectConfig.getInstance(project).apply {
            setAddonSymlinkName(data.extensionId)
            setSourceFolder("src/")
        }

        logger.log("Project generation complete")
    }
    
    /**
     * Creates `pyproject.toml` from the internal file template.
     *
     * @param project active project context.
     * @param baseDir project root directory.
     */
    fun generatePyproject(project: Project, baseDir: VirtualFile) {
        PluginResources.createFromTemplate(
            project,
            name="pyproject.toml",
            template = "Pyproject",
            destination=baseDir,
            internal=true,
            Pair("name", data.name),
            Pair("version", data.extensionVersion),
            Pair("python", BlenderVersions.getPythonVersion(data.blenderVersion)),
            Pair("license", data.projectLicense),
        )
    }
    /**
     * Creates `blender_manifest.toml` from wizard configuration values.
     *
     * @param project active project context.
     * @param baseDir output directory for the manifest file.
     */
    fun generateManifest(project: Project, baseDir: VirtualFile) {
        // Helper to ensure empty strings are passed instead of nulls for Velocity logic.
        fun String?.valOrEmpty(): String = if (this.isNullOrBlank()) "" else this
        
        // Format the list of strings into a TOML array: ["tag1", "tag2"]
        val formattedTags = data.tags.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        
        // Convert Extension to 'add-on', since the type key in the blender_manifest expects "add-on" or "theme", not extension.
        val projectType = when (data.projectType) {
            PROJECT_TYPE_EXTENSION -> "add-on"
            PROJECT_TYPE_THEME -> "theme"
            else -> ""
        }
        
        PluginResources.createFromTemplate(
            project = project,
            name = "blender_manifest.toml",
            template = "BlenderManifest",
            destination = baseDir,
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
    /**
     * Creates the initial add-on `__init__.py` script from template.
     *
     * @param project active project context.
     * @param baseDir output directory for the script.
     */
    fun generateMainScript(project: Project, baseDir: VirtualFile) {
        // Helper for normalizing nullable strings.
        fun String?.valOrEmpty(): String = if (this.isNullOrBlank()) "" else this
        
        PluginResources.createFromTemplate(
            project = project,
            name = "__init__.py",
            template = "NewProjectMainScript", // Ensure this matches your plugin.xml registration
            destination = baseDir,
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
    /**
     * Creates the `.gitignore` template file.
     *
     * @param project active project context.
     * @param baseDir project root directory.
     */
    fun generateGitIgnore(project: Project, baseDir: VirtualFile) {
        PluginResources.createFromTemplate(
            project,
            name=".gitignore",
            template = "GitIgnore",
            destination = baseDir,
            internal = true,
        )
    }
    /**
     * Creates a GPLv3 license file from template.
     *
     * @param project active project context.
     * @param baseDir project root directory.
     */
    fun generateLicense(project: Project, baseDir: VirtualFile) {
        PluginResources.createFromTemplate(
            project,
            name="LICENSE",
            template = "GplLicenseV3",
            destination = baseDir,
            internal = true
        )
    }
    /**
     * Creates `README.md` from template.
     *
     * @param project active project context.
     * @param baseDir project root directory.
     */
    fun generateReadme(project: Project, baseDir: VirtualFile) {
        // Helper for normalizing nullable strings.
        fun String?.valOrEmpty(): String = if (this.isNullOrBlank()) "" else this
        PluginResources.createFromTemplate(
            project,
            name="README.md",
            template = "README",
            destination = baseDir,
            internal = true,
            
            // Metadata
            "name" to data.name.valOrEmpty(),
            "description" to data.description.valOrEmpty(),
            "blenderVersion" to data.blenderVersion.valOrEmpty(),
        )
    }

    companion object {
        const val PROJECT_TYPE_EXTENSION: String = "extension"
        const val PROJECT_TYPE_ADD_ON: String = "add-on"
        const val PROJECT_TYPE_THEME: String = "theme"
    }
}
