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

package com.sakurasedaia.blenderdevelopment.core

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.execution.ParametersListUtil
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.BlenderBootstrapScriptCleanup
import com.sakurasedaia.blenderdevelopment.util.BlenderRuntimeResources
import com.sakurasedaia.blenderdevelopment.util.PluginResources
import java.nio.file.Files
import java.nio.file.Path

/** Describes a Blender launch that executes either a provided or generated Python script. */
internal data class BlenderPythonLaunchRequest(
    val blenderPath: String = "",
    val scriptPath: Path? = null,
    val additionalArguments: List<String> = emptyList(),
    val debugger: Boolean = false,
)

/** Orchestrates Blender Python bootstrap generation, runtime sessions, and cleanup. */
@Service(Service.Level.PROJECT)
internal class BlenderPythonLauncher(private val project: Project) {
    private val logger = PluginLogger.getInstance(project)
    private val projectConfig = ProjectConfig.getInstance(project)
    private val blenderLauncher = BlenderLauncher.getInstance(project)

    /** Starts Blender with an explicit `--python` launch surface. */
    fun start(request: BlenderPythonLaunchRequest): OSProcessHandler {

        val launchSession =
            if (request.debugger) {
                BlenderEditorServerService.getInstance(project).prepareLaunchSession()
            } else {
                null
            }

        val generatedScriptPath =
            when {
                request.scriptPath != null -> null
                request.debugger -> createScratchDebugLaunchScript()
                else -> createScratchRuntimeSyncLaunchScript()
            }

        val scriptPath = request.scriptPath ?: checkNotNull(generatedScriptPath)
        val workspaceArguments = ParametersListUtil.parse(projectConfig.getRunArguments().trim())
        val arguments =
            BlenderLaunchArguments.python(
                logLevel = projectConfig.getBlenderLogLevel(),
                workspaceArguments = workspaceArguments,
                scriptPath = scriptPath,
                additionalArguments = request.additionalArguments,
            )

        val processHandler =
            blenderLauncher.start(
                BlenderLaunchRequest(
                    blenderPath = request.blenderPath,
                    arguments = arguments,
                    environment = buildRuntimeEnvironment(launchSession),
                )
            )

        if (generatedScriptPath != null || launchSession != null) {
            processHandler.putUserData(LAUNCH_SESSION_IDENTIFIER_KEY, launchSession?.identifier)
            processHandler.addProcessListener(
                object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) {
                        if (generatedScriptPath != null) {
                            BlenderBootstrapScriptCleanup.cleanupScript(
                                path = generatedScriptPath,
                                debugLog = logger::debug,
                                warnLog = logger::warn,
                            )
                        }
                        if (launchSession != null) {
                            BlenderEditorServerService.getInstance(project).unregisterSession(launchSession.identifier)
                        }
                    }
                }
            )
        }
        return processHandler
    }

    private fun buildRuntimeEnvironment(launchSession: BlenderRuntimeLaunchSession?): Map<String, String> {
        val environment = mutableMapOf<String, String>()
        val runtimeLogLevel = toRuntimeLogLevel(projectConfig.getBlenderLogLevel())
        environment["BLENDER_PYCHARM_LOG_LEVEL"] = runtimeLogLevel

        val extensionsRepository = projectConfig.getExtensionsRepository().trim()
        if (extensionsRepository.isNotEmpty()) {
            environment["BLENDER_PYCHARM_EXTENSIONS_REPOSITORY"] = extensionsRepository
        }

        val configuredScriptDirectories = resolveConfiguredScriptDirectories()
        if (configuredScriptDirectories.isNotEmpty()) {
            environment["BLENDER_PYCHARM_SCRIPT_DIRECTORIES"] =
                configuredScriptDirectories.joinToString(separator = java.io.File.pathSeparator)
        }

        if (launchSession != null) {
            environment["EDITOR_PORT"] = launchSession.editorPort.toString()
            environment["BLENDER_PYCHARM_IDENTIFIER"] = launchSession.identifier
            environment["BLENDER_PYCHARM_AUTHKEY"] = launchSession.encodedAuthKey
        }
        return environment
    }

    private fun createScratchDebugLaunchScript(): Path {
        val scratchDirectory = resolveScratchDirectory()
        val includeDirectoryPath = BlenderRuntimeResources.ensureRuntimeExtracted()
        val scriptFileName = BlenderBootstrapScriptCleanup.newScriptFileName()

        WriteAction.run<Throwable> {
            PluginResources.createFromTemplate(
                project = project,
                name = scriptFileName,
                template = "BlenderRuntimeLaunch",
                destination = scratchDirectory,
                internal = true,
                "includeDirLiteral" to toPythonStringLiteral(includeDirectoryPath.toString()),
                "projectPathLiteral" to toPythonStringLiteral(project.basePath ?: ""),
                "sourceFolderLiteral" to toPythonStringLiteral(projectConfig.getSourceFolder()),
                "addonSymlinkNameLiteral" to toPythonStringLiteral(projectConfig.getAddonSymlinkName()),
            )
        }
        return Path.of(scratchDirectory.path, scriptFileName)
    }

    private fun createScratchRuntimeSyncLaunchScript(): Path {
        val scratchDirectory = resolveScratchDirectory()
        val scriptFileName = BlenderBootstrapScriptCleanup.newScriptFileName()
        val includeDirectoryPath = BlenderRuntimeResources.ensureRuntimeExtracted()

        WriteAction.run<Throwable> {
            PluginResources.createFromTemplate(
                project = project,
                name = scriptFileName,
                template = "BlenderRuntimeRepoSyncLaunch",
                destination = scratchDirectory,
                internal = true,
                "includeDirLiteral" to toPythonStringLiteral(includeDirectoryPath.toString()),
                "projectPathLiteral" to toPythonStringLiteral(project.basePath ?: ""),
                "sourceFolderLiteral" to toPythonStringLiteral(projectConfig.getSourceFolder()),
                "addonSymlinkNameLiteral" to toPythonStringLiteral(projectConfig.getAddonSymlinkName()),
                "extensionsRepositoryLiteral" to toPythonStringLiteral(projectConfig.getExtensionsRepository()),
            )
        }
        return Path.of(scratchDirectory.path, scriptFileName)
    }

    private fun resolveScratchDirectory(): com.intellij.openapi.vfs.VirtualFile {
        val scratchDirectoryPath = PathManager.getScratchDir()
        require(scratchDirectoryPath.toString().isNotBlank()) {
            MessageBundle.message("run.configuration.blender.error.scratch.path.empty")
        }
        Files.createDirectories(scratchDirectoryPath)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(scratchDirectoryPath)
            ?: throw IllegalStateException(
                MessageBundle.message(
                    "run.configuration.blender.error.scratch.directory.missing",
                    scratchDirectoryPath.toString(),
                )
            )
    }

    private fun toPythonStringLiteral(value: String): String {
        val builder = StringBuilder(value.length + 8)
        value.forEach { character ->
            when (character) {
                '\\' -> builder.append("\\\\")
                '\'' -> builder.append("\\'")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }

    private fun toRuntimeLogLevel(logLevel: BlenderLogLevel): String {
        return when (logLevel) {
            BlenderLogLevel.FATAL -> "critical"
            BlenderLogLevel.ERROR -> "error"
            BlenderLogLevel.WARNING -> "warning"
            BlenderLogLevel.INFO -> "info"
            BlenderLogLevel.DEBUG -> "debug"
            BlenderLogLevel.TRACE -> "debug"
        }
    }

    private fun resolveConfiguredScriptDirectories(): List<String> {
        val basePath = project.basePath
        return projectConfig
            .getScriptDirectories()
            .orEmpty()
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { configuredPath ->
                val path = Path.of(configuredPath)
                when {
                    path.isAbsolute -> path.normalize()
                    basePath != null -> Path.of(basePath).resolve(path).normalize()
                    else -> null
                }
            }
            .map(Path::toString)
            .distinct()
            .toList()
    }

    companion object {
        /** Process-handler key containing the editor-server launch session identifier. */
        val LAUNCH_SESSION_IDENTIFIER_KEY: Key<String> = Key.create("com.sakurasedaia.blenderdevelopment.runtime.launchSessionIdentifier")

        /** Returns the project-scoped Blender Python launcher. */
        fun getInstance(project: Project): BlenderPythonLauncher = project.service()
    }
}
