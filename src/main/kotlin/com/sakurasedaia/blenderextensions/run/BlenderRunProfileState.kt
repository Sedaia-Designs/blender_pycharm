package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderService
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.blender.utils.BlenderPathUtil

class BlenderRunProfileState(
    private val project: Project,
    private val options: BlenderRunConfigurationOptions,
    private val environment: ExecutionEnvironment
) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): com.intellij.execution.ExecutionResult? {
        val service = BlenderService.getInstance(project)
        val rawVersion = options.blenderVersion ?: "5.0"
        service.log("--- Starting Blender Run Configuration: ${environment.runProfile.name} ---")
        service.log("Requested version/path: $rawVersion")
        
        var detectedVersion: String? = null
        val blenderPath = when {
            BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == rawVersion } -> {
                service.log("Using managed Blender version: $rawVersion")
                detectedVersion = rawVersion
                
                var result: String? = null
                ProgressManager.getInstance().runProcessWithProgressSynchronously({
                    result = service.getOrDownloadBlenderPath(rawVersion)
                }, LangManager.message("run.configuration.downloading", rawVersion), true, project)
                result
            }
            else -> {
                service.log("Using system Blender path: $rawVersion")
                // version is a path. Let's find its version for config import.
                val inst = com.sakurasedaia.blenderextensions.blender.services.BlenderScanner.scanSystemInstallations(project).find { it.path == rawVersion }
                detectedVersion = inst?.version ?: BlenderPathUtil.detectVersion(project, rawVersion)
                rawVersion // It's a path
            }
        }

        if (blenderPath.isNullOrEmpty()) {
            service.log("Error: Blender path could not be resolved for version: $rawVersion")
            throw ExecutionException(LangManager.message("run.configuration.error.path"))
        }
        service.log("Resolved Blender path: $blenderPath")

        // If version is still unknown, try to detect it from the resolved path
        if (detectedVersion == null || detectedVersion == "Unknown") {
             service.log("Attempting to detect version from path: $blenderPath")
             detectedVersion = BlenderPathUtil.detectVersion(project, blenderPath)
             service.log("Detected version: $detectedVersion")
        }
        
        service.log("Final Startup Parameters - Path: $blenderPath, Version: $detectedVersion, Sandboxed: ${options.isSandboxed}")
        
        val handler = service.startBlenderProcess(
            blenderPath = blenderPath,
            addonSourceDir = options.addonSourceDirectory,
            addonSymlinkName = options.addonSymlinkName,
            additionalArgs = options.additionalArguments,
            isSandboxed = options.isSandboxed,
            blenderCommand = options.blenderCommand,
            importUserConfig = options.importUserConfig,
            blenderVersion = detectedVersion,
            runOptions = options,
            indicator = ProgressManager.getInstance().progressIndicator
        ) ?: throw ExecutionException(LangManager.message("run.configuration.error.start"))
        
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        val console = consoleBuilder.console
        console.attachToProcess(handler)
        
        return com.intellij.execution.DefaultExecutionResult(console, handler)
    }
}
