package com.sakurasedaia.blenderextensions.project

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.run.BlenderRunConfiguration
import com.sakurasedaia.blenderextensions.run.BlenderRunConfigurationType
import com.sakurasedaia.blenderextensions.run.BlenderStartBlenderConfigurationFactory
import com.sakurasedaia.blenderextensions.run.BlenderBuildConfigurationFactory
import com.sakurasedaia.blenderextensions.run.BlenderValidateConfigurationFactory
import com.sakurasedaia.blenderextensions.blender.services.BlenderFinder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

@Service(Service.Level.PROJECT)
class BlenderProjectService(private val project: Project) {

    fun generateAgentGuidelines() {
        val projectPath = project.basePath?.let { Path.of(it) } ?: return
        val projectName = project.name
        
        val agentDir = projectPath.resolve(".agent")
        val skillsDir = agentDir.resolve("skills")
        Files.createDirectories(skillsDir)

        agentDir.resolve("guidelines.md").writeText(
            BlenderProjectTemplateGenerator.generateAgentGuidelines()
        )
        agentDir.resolve("project.md").writeText(
            BlenderProjectTemplateGenerator.generateAgentProject(projectName)
        )
        agentDir.resolve("context.md").writeText(
            BlenderProjectTemplateGenerator.generateAgentContext()
        )

        val skills = listOf("blender_extension_dev", "python_practices", "git_management", "ai_workflow")
        for (skill in skills) {
            skillsDir.resolve("$skill.md").writeText(
                BlenderProjectTemplateGenerator.generateAgentSkill(skill)
            )
        }
        
        VfsUtil.markDirtyAndRefresh(true, true, true, project.baseDir)
    }

    fun generateGitignore() {
        val projectPath = project.basePath?.let { Path.of(it) } ?: return
        val gitignorePath = projectPath.resolve(".gitignore")
        
        val content = BlenderProjectTemplateGenerator.generateGitignore()
        if (content.isBlank()) return

        if (gitignorePath.exists()) {
            val existingContent = Files.readString(gitignorePath)
            if (!existingContent.contains(content)) {
                Files.writeString(gitignorePath, existingContent + "\n\n# Blender Development Tools\n" + content)
            }
        } else {
            Files.writeString(gitignorePath, content)
        }
        
        VfsUtil.markDirtyAndRefresh(true, true, true, project.baseDir)
    }

    fun generateLicense() {
        val projectPath = project.basePath?.let { Path.of(it) } ?: return
        val licensePath = projectPath.resolve("LICENSE")
        
        val content = BlenderProjectTemplateGenerator.generateLicense()
        if (content.isBlank()) return

        if (!licensePath.exists()) {
            Files.writeString(licensePath, content)
        }
        
        VfsUtil.markDirtyAndRefresh(true, true, true, project.baseDir)
    }

    fun generateRunConfigurations() {
        val runManager = RunManager.getInstance(project)
        val configType = ConfigurationTypeUtil.findConfigurationType(BlenderRunConfigurationType::class.java)
        
        val projectPath = project.basePath?.let { Path.of(it) } ?: return
        val srcDir = projectPath.resolve("src")
        val addonId = project.name.lowercase().replace(" ", "_")
        
        // Try to find an existing Blender version or path
        val selectedVersion = "4.2" // Default fallback

        // 1. Start Blender
        val startBlenderFactory = configType.configurationFactories.find { it is BlenderStartBlenderConfigurationFactory }
        if (startBlenderFactory != null) {
            val runSettings = runManager.createConfiguration("Start Blender", startBlenderFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            val options = runConfig.getOptions()
            options.blenderVersion = selectedVersion
            options.isSandboxed = true
            if (srcDir.exists()) {
                options.addonSourceDirectory = srcDir.toAbsolutePath().toString()
            }
            options.addonSymlinkName = addonId
            runManager.addConfiguration(runSettings)
            runManager.selectedConfiguration = runSettings
        }

        // 2. Build
        val buildFactory = configType.configurationFactories.find { it is BlenderBuildConfigurationFactory }
        if (buildFactory != null) {
            val runSettings = runManager.createConfiguration("Build", buildFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            runConfig.getOptions().blenderVersion = selectedVersion
            runManager.addConfiguration(runSettings)
        }

        // 3. Validate
        val validateFactory = configType.configurationFactories.find { it is BlenderValidateConfigurationFactory }
        if (validateFactory != null) {
            val runSettings = runManager.createConfiguration("Validate", validateFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            runConfig.getOptions().blenderVersion = selectedVersion
            runManager.addConfiguration(runSettings)
        }
    }

    companion object {
        fun getInstance(project: Project): BlenderProjectService = project.getService(BlenderProjectService::class.java)
    }
}
