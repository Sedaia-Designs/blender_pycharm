package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * Represents a single Blender run configuration.
 * 
 * This class acts as a bridge between the stored [BlenderRunConfigurationOptions],
 * the UI [BlenderSettingsEditor], and the execution state [BlenderRunProfileState].
 */
class BlenderRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<BlenderRunConfigurationOptions>(project, factory, name) {

    /**
     * Provides typed access to the configuration options.
     */
    public override fun getOptions(): BlenderRunConfigurationOptions {
        return super.getOptions() as BlenderRunConfigurationOptions
    }

    /**
     * Creates the editor UI for this configuration.
     */
    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return BlenderSettingsEditor(project)
    }

    /**
     * Creates the execution state for this configuration.
     * This is where the actual logic for running Blender resides.
     */
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return BlenderRunProfileState(project, getOptions(), environment)
    }
}
