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

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.IconBundle
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle

/** Registers the Blender launch run configuration type. */
internal class BlenderConfigurationType :
    ConfigurationTypeBase(
        "BlenderConfigurationType",
        MessageBundle.message("run.configuration.blender.type.name"),
        MessageBundle.message("run.configuration.blender.type.description"),
        IconBundle.BlenderColor,
    ) {
    init {
        addFactory(BlenderLaunchConfigurationFactory(this))
        addFactory(BlenderCommandConfigurationFactory(this))
        addFactory(BlenderExtensionBuildConfigurationFactory(this))
    }
}

private class BlenderExtensionBuildConfigurationFactory(type: BlenderConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "BlenderExtensionBuildConfigurationFactory"

    override fun getName(): String = MessageBundle.message("run.configuration.blender.extension.build.factory.name")

    override fun getOptionsClass(): Class<out BaseState> = BlenderExtensionBuildRunConfigurationOptions::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        val configuration =
            BlenderExtensionBuildRunConfiguration(
                project,
                this,
                MessageBundle.message("run.configuration.blender.extension.build.default.name"),
            )
        configuration.sourcePath = ProjectConfig.getInstance(project).getSourceFolder()
        configuration.outputDirectory = "."
        return configuration
    }
}

private class BlenderCommandConfigurationFactory(type: BlenderConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "BlenderCommandConfigurationFactory"

    override fun getName(): String = MessageBundle.message("run.configuration.blender.command.factory.name")

    override fun getOptionsClass(): Class<out BaseState> = BlenderCommandRunConfigurationOptions::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return BlenderCommandRunConfiguration(
            project,
            this,
            MessageBundle.message("run.configuration.blender.command.default.name"),
        )
    }
}

private class BlenderLaunchConfigurationFactory(type: BlenderConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "BlenderLaunchConfigurationFactory"

    override fun getName(): String = MessageBundle.message("run.configuration.blender.live-run.name")

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return BlenderLaunchRunConfiguration(
            project,
            this,
            MessageBundle.message("run.configuration.blender.live-run.name"),
        )
    }
}
