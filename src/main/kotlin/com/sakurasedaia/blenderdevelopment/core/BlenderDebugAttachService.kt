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

@file:Suppress("UsePropertyAccessSyntax")

package com.sakurasedaia.blenderdevelopment.core

import com.intellij.execution.ExecutionResult
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PathMappingSettings
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
internal class BlenderDebugAttachService(private val project: Project) {
    private val logger = PluginLogger.getInstance(project)
    private val notifications = NotificationModal.getInstance(project)
    private val projectConfig = ProjectConfig.getInstance(project)

    fun scheduleAttach(environment: ExecutionEnvironment, executionResult: ExecutionResult, sessionIdentifier: String) {
        // TODO(V1): Tie debugger polling to project and execution disposal instead of the application-wide executor.
        AppExecutorUtil.getAppExecutorService().submit {
            val setupPayload = waitForSetupPayload(sessionIdentifier, executionResult)
            if (setupPayload == null) {
                notifications.sendError(
                    MessageBundle.message("notification.blender.debug.attach.timeout", sessionIdentifier),
                    MessageBundle.message("notification.blender.debug.attach.failed"),
                )
                return@submit
            }

            if (setupPayload.debugpyPort <= 0) {
                notifications.sendError(
                    MessageBundle.message("notification.blender.debug.attach.invalid.port", setupPayload.debugpyPort.toString()),
                    MessageBundle.message("notification.blender.debug.attach.failed"),
                )
                return@submit
            }

            val mappingSettings = buildPathMappings(setupPayload)
            ApplicationManager.getApplication().invokeLater {
                runCatching {
                    when (setupPayload.debugProtocol) {
                        BlenderDebugProtocol.DEBUGPY_DAP -> {
                            if (!attachWithDebugpyDap(setupPayload, mappingSettings)) {
                                throw IllegalStateException(MessageBundle.message("notification.blender.debug.attach.dap.unavailable"))
                            }
                            logger.debug("Attached Python debugger with DAP protocol to Blender debugpy port ${setupPayload.debugpyPort}.")
                        }
                        BlenderDebugProtocol.PYDEVD -> {
                            attachWithPydevClientMode(environment, executionResult, setupPayload, mappingSettings)
                            logger.debug("Attached Python debugger with pydev protocol to Blender debug port ${setupPayload.debugpyPort}.")
                        }
                    }
                }
                    .onFailure { error ->
                        logger.warn(ErrorTypes.DEBUG_ATTACH_FAILED.format(sessionIdentifier), error)
                        notifications.sendError(
                            error.message ?: MessageBundle.message("notification.blender.debug.attach.generic.error"),
                            MessageBundle.message("notification.blender.debug.attach.failed"),
                        )
                    }
            }
        }
    }

    private fun waitForSetupPayload(sessionIdentifier: String, executionResult: ExecutionResult): BlenderSetupPayload? {
        val editorServerService = BlenderEditorServerService.getInstance(project)
        val deadline = System.currentTimeMillis() + DEBUG_ATTACH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (executionResult.processHandler.isProcessTerminated || executionResult.processHandler.isProcessTerminating) {
                editorServerService.unregisterSession(sessionIdentifier)
                return null
            }

            val payload = editorServerService.removeSetupPayload(sessionIdentifier)
            if (payload != null) {
                return payload
            }
            Thread.sleep(DEBUG_ATTACH_POLL_INTERVAL_MS)
        }
        return null
    }

    private fun buildPathMappings(setupPayload: BlenderSetupPayload): PathMappingSettings {
        val mappingSettings = PathMappingSettings()
        val uniqueMappings = linkedSetOf<Pair<String, String>>()

        setupPayload.pathMappings.forEach { mapping ->
            if (mapping.src.isNotBlank() && mapping.load.isNotBlank()) {
                uniqueMappings.add(mapping.src to mapping.load)
            }
        }

        val projectBasePath = project.basePath
        val sourceFolder = projectConfig.getSourceFolder().trim()
        if (!projectBasePath.isNullOrBlank() && sourceFolder.isNotBlank()) {
            val localSourcePath = Path.of(projectBasePath).resolve(sourceFolder).normalize()
            uniqueMappings.add(localSourcePath.toString() to localSourcePath.toString())

            val addonSymlinkName = projectConfig.getAddonSymlinkName().trim()
            if (addonSymlinkName.isNotBlank() && setupPayload.scriptsFolder.isNotBlank()) {
                val remoteAddonPath = Path.of(setupPayload.scriptsFolder).resolve("addons").resolve(addonSymlinkName).normalize()
                uniqueMappings.add(localSourcePath.toString() to remoteAddonPath.toString())
            }
        }

        uniqueMappings
            .filter { (localPath, remotePath) -> localPath.isNotBlank() && remotePath.isNotBlank() }
            .filter { (localPath, _) -> Path.of(localPath).exists() }
            .forEach { (localPath, remotePath) ->
                mappingSettings.add(PathMappingSettings.PathMapping(localPath, remotePath))
            }

        return mappingSettings
    }

    private fun attachWithDebugpyDap(setupPayload: BlenderSetupPayload, mappingSettings: PathMappingSettings): Boolean {
        val configurationType =
            ConfigurationTypeUtil.findConfigurationType(PYTHON_DAP_ATTACH_CONFIGURATION_TYPE_ID)
                ?: run {
                    logger.debug("Python DAP attach configuration type is not registered.")
                    return false
                }
        val factory =
            configurationType.configurationFactories.firstOrNull()
                ?: run {
                    logger.debug("Python DAP attach configuration type does not provide a configuration factory.")
                    return false
                }

        val settings =
            RunManager.getInstance(project)
                .createConfiguration(
                    "Blender Debug Attach (${setupPayload.debugpyPort})",
                    factory,
                )
        val configuration = settings.configuration
        val configurationClass = configuration.javaClass

        configurationClass.getMethod("setRemoteAddress", String::class.java).invoke(configuration, "127.0.0.1:${setupPayload.debugpyPort}")
        val pathMappingsSetter =
            configurationClass.methods.firstOrNull { method ->
                method.name == "setPathMappingSettings" && method.parameterCount == 1
            }
        if (pathMappingsSetter != null) {
            pathMappingsSetter.invoke(configuration, mappingSettings)
        } else {
            val firstMapping = mappingSettings.pathMappings.firstOrNull()
            val localRootSetter =
                configurationClass.methods.firstOrNull { method ->
                    method.name == "setLocalRoot" && method.parameterCount == 1
                }
            val remoteRootSetter =
                configurationClass.methods.firstOrNull { method ->
                    method.name == "setRemoteRoot" && method.parameterCount == 1
                }
            if (firstMapping != null && localRootSetter != null && remoteRootSetter != null) {
                localRootSetter.invoke(configuration, firstMapping.localRoot)
                remoteRootSetter.invoke(configuration, firstMapping.remoteRoot)
            } else if (mappingSettings.pathMappings.isNotEmpty()) {
                logger.debug("Python DAP attach configuration does not expose a supported path-mapping setter.")
            }
        }
        configurationClass.methods
            .firstOrNull { method ->
                method.name == "setJustMyCode" && method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
            }
            ?.invoke(configuration, projectConfig.getJustMyCode())

        val debugpyRegistry = Registry.get("debugpy.dap.is.enable")
        val wasDebugpyEnabled = debugpyRegistry.asBoolean()
        if (!wasDebugpyEnabled) {
            debugpyRegistry.setValue(true)
        }
        try {
            ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
        } finally {
            if (!wasDebugpyEnabled) {
                debugpyRegistry.setValue(false)
            }
        }
        return true
    }

    private fun attachWithPydevClientMode(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult,
        setupPayload: BlenderSetupPayload,
        mappingSettings: PathMappingSettings,
    ) {
        val starter =
            object : XDebugProcessStarter() {
                override fun start(session: com.intellij.xdebugger.XDebugSession): XDebugProcess {
                    val debugProcess =
                        PyDebugProcess(
                            session,
                            executionResult.executionConsole,
                            executionResult.processHandler,
                            "localhost",
                            setupPayload.debugpyPort,
                        )
                    if (mappingSettings.pathMappings.isNotEmpty()) {
                        debugProcess.positionConverter = PyRemotePositionConverter(debugProcess, mappingSettings)
                    }
                    return debugProcess
                }
            }
        @Suppress("UnstableApiUsage")
        XDebuggerManager.getInstance(project).newSessionBuilder(starter).environment(environment).startSession()
    }

    companion object {
        private const val DEBUG_ATTACH_TIMEOUT_MS = 45_000L
        private const val DEBUG_ATTACH_POLL_INTERVAL_MS = 200L
        private const val PYTHON_DAP_ATTACH_CONFIGURATION_TYPE_ID = "PythonDapAttachConfiguration"

        fun getInstance(project: Project): BlenderDebugAttachService = project.service()
    }
}
