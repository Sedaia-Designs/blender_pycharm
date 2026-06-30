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

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderdevelopment.config.ProjectConfig
import com.sakurasedaia.blenderdevelopment.lib.MessageBundle
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path

/** Serializable model used by the Blender run configuration skeleton. */
data class BlenderRunConfigurationData(
    @Deprecated("Use BlenderExtensionManifest.sourceFolder")
    var sourcePath: String = "src/",
    @Deprecated("Use BlenderExtensionManifest.runArguments")
    var arguments: String = "",
)

/** Minimal run configuration skeleton for Blender execution integration work. */
class BlenderRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<Any>(project, factory, name) {
    var data: BlenderRunConfigurationData = BlenderRunConfigurationData()

    override fun getConfigurationEditor(): SettingsEditor<out com.intellij.execution.configurations.RunConfiguration> {
        return BlenderRunSettingsEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return BlenderRunCommandLineState(environment, this)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(data, element)

        // Migrate older run configuration state into the shared project-level config.
        @Suppress("DEPRECATION")
        val projectConfig = ProjectConfig.getInstance(project)
        @Suppress("DEPRECATION")
        val migratedSource = data.sourcePath.trim()
        if (migratedSource.isNotEmpty()) {
            projectConfig.setSourceFolder(migratedSource)
        }
        @Suppress("DEPRECATION")
        projectConfig.setRunArguments(data.arguments.trim())
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(data, element)
    }

    override fun checkConfiguration() {
        val projectConfig = ProjectConfig.getInstance(project)
        val sourcePath = projectConfig.getSourceFolder().trim()
        if (sourcePath.isEmpty()) {
            throw RuntimeConfigurationError(MessageBundle.message("run.configuration.error.source.path.required"))
        }

        val projectBase = project.basePath ?: throw RuntimeConfigurationError(
            MessageBundle.message("run.configuration.error.project.directory.unavailable")
        )
        val sourceDir = Path.of(projectBase).resolve(sourcePath).normalize().toAbsolutePath()
        if (!Files.isDirectory(sourceDir)) {
            throw RuntimeConfigurationError(
                MessageBundle.message("run.configuration.error.source.path.missing", sourceDir.toString())
            )
        }
        if (!Files.exists(sourceDir.resolve("__init__.py"))) {
            throw RuntimeConfigurationError(MessageBundle.message("run.configuration.error.source.path.missing.init"))
        }
        if (!Files.exists(sourceDir.resolve("blender_manifest.toml"))) {
            throw RuntimeConfigurationError(MessageBundle.message("run.configuration.error.source.path.missing.manifest"))
        }
    }
}
