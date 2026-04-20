package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.pathString

private fun getSrcPath(project: Project): String = 
    Path.of(project.basePath ?: "", "src").toAbsolutePath().pathString

/**
 * Entry point for Blender run configurations.
 * 
 * The run configuration module follows this progression:
 * 1. [BlenderRunConfigurationType] defines the type and available factories.
 * 2. [ConfigurationFactory] implementations create specific presets (Start, Build, etc.).
 * 3. [BlenderRunConfiguration] holds the actual configuration instance.
 * 4. [BlenderRunConfigurationOptions] stores the persistent settings.
 * 5. [BlenderSettingsEditor] provides the UI to edit those settings.
 * 6. [BlenderRunProfileState] handles the execution logic when the configuration is run.
 */
class BlenderRunConfigurationType : ConfigurationType {
    override fun getDisplayName(): String = LangManager.message("run.configuration.name")
    override fun getConfigurationTypeDescription(): String = LangManager.message("run.configuration.description")
    override fun getIcon(): Icon = BlenderIcons.BlenderColor
    override fun getId(): String = "BLENDER_RUN_CONFIGURATION"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(
        BlenderStartBlenderConfigurationFactory(this),
        BlenderBuildConfigurationFactory(this),
        BlenderValidateConfigurationFactory(this),
        BlenderCommandConfigurationFactory(this)
    )
}

// --- Configuration Factories ---

/**
 * Factory for the default "Start Blender" configuration.
 * This is used for general Blender usage and testing.
 */
class BlenderStartBlenderConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    val configName = LangManager.message("run.configuration.preset.start")
    
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        BlenderRunConfiguration(project, this, configName)

    override fun getName(): String = configName
    override fun getId(): String = "BlenderStartBlenderConfigurationFactory"
    override fun getOptionsClass(): Class<out BaseState> = BlenderRunConfigurationOptions::class.java
}

/**
 * Factory for the "Build Extension" configuration.
 * Pre-fills the command to build the Blender extension.
 */
class BlenderBuildConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    val configName = LangManager.message("run.configuration.preset.build")
    
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        val config = BlenderRunConfiguration(project, this, configName)
        config.options.blenderCommand = "extension build --source-dir ${getSrcPath(project)}"
        return config
    }

    override fun getName(): String = configName
    override fun getId(): String = "BlenderBuildConfigurationFactory"
    override fun getOptionsClass(): Class<out BaseState> = BlenderRunConfigurationOptions::class.java
}

/**
 * Factory for the "Validate Extension" configuration.
 * Pre-fills the command to validate the Blender extension.
 */
class BlenderValidateConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    val configName = LangManager.message("run.configuration.preset.validate")
    
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        val config = BlenderRunConfiguration(project, this, configName)
        config.options.blenderCommand = "extension validate ${getSrcPath(project)}"
        return config
    }

    override fun getName(): String = configName
    override fun getId(): String = "BlenderValidateConfigurationFactory"
    override fun getOptionsClass(): Class<out BaseState> = BlenderRunConfigurationOptions::class.java
}

/**
 * Factory for a custom Blender command configuration.
 * Allows users to specify any Blender CLI command.
 */
class BlenderCommandConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    val configName = LangManager.message("run.configuration.preset.command")
    
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        val config = BlenderRunConfiguration(project, this, configName)
        config.options.blenderCommand = ""
        return config
    }

    override fun getName(): String = configName
    override fun getId(): String = "BlenderCommandConfigurationFactory"
    override fun getOptionsClass(): Class<out BaseState> = BlenderRunConfigurationOptions::class.java
}
