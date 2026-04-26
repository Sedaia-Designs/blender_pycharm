package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class BlenderRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<BlenderRunConfigurationOptions>(project, factory, name) {

    public override fun getOptions(): BlenderRunConfigurationOptions {
        return super.getOptions() as BlenderRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return BlenderSettingsEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return BlenderRunProfileState(project, getOptions(), environment)
    }
}
