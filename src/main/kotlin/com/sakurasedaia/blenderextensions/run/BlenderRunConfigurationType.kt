package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.util.execution.ParametersListUtil
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.common.BlenderProjectPaths
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.pathString

fun getSrcPath(project: Project): String = 
    BlenderProjectPaths.getSrcDir(project).toAbsolutePath().pathString

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
class BlenderStartBlenderConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    val configName = LangManager.message("run.configuration.preset.start")
    
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        BlenderRunConfiguration(project, this, configName)

    override fun getName(): String = configName
    override fun getId(): String = "BlenderStartBlenderConfigurationFactory"
    override fun getOptionsClass(): Class<out BaseState> = BlenderRunConfigurationOptions::class.java
}

class BlenderBuildConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    val configName = LangManager.message("run.configuration.preset.build")
    
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        val config = BlenderRunConfiguration(project, this, configName)
        val srcPath = getSrcPath(project)
        config.options.blenderCommand = "extension build --source-dir ${ParametersListUtil.join(listOf(srcPath))}"
        return config
    }

    override fun getName(): String = configName
    override fun getId(): String = "BlenderBuildConfigurationFactory"
    override fun getOptionsClass(): Class<out BaseState> = BlenderRunConfigurationOptions::class.java
}

class BlenderValidateConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    val configName = LangManager.message("run.configuration.preset.validate")
    
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        val config = BlenderRunConfiguration(project, this, configName)
        val srcPath = getSrcPath(project)
        config.options.blenderCommand = "extension validate ${ParametersListUtil.join(listOf(srcPath))}"
        return config
    }

    override fun getName(): String = configName
    override fun getId(): String = "BlenderValidateConfigurationFactory"
    override fun getOptionsClass(): Class<out BaseState> = BlenderRunConfigurationOptions::class.java
}

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
