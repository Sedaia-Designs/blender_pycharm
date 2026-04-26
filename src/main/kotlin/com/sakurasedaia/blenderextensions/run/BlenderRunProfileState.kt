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
        val version = options.blenderVersion ?: "5.0"
        
        service.log("--- Starting Blender Run Configuration: ${environment.runProfile.name} ---")
        service.log("Requested version/path: $version")
        
        var detectedVersion: String? = null
        var blenderPath: String? = null
        var handler: com.intellij.execution.process.OSProcessHandler? = null

        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                val indicator = ProgressManager.getInstance().progressIndicator
                // --- STEP 1 & 2: RESOLVE BLENDER PATH AND VERSION ---
                blenderPath = resolveBlenderPathInternal(service, version) { detectedVersion = it }
                
                if (blenderPath == null) return@runProcessWithProgressSynchronously
    
                service.log("Final Startup Parameters - Path: $blenderPath, Version: $detectedVersion, Sandboxed: ${options.isSandboxed}")
    
                // --- STEP 3: START BLENDER PROCESS ---
                handler = service.startBlenderProcess(
                    blenderPath = blenderPath!!,
                    addonSourceDir = options.addonSourceDirectory,
                    addonSymlinkName = options.addonSymlinkName,
                    additionalArgs = options.additionalArguments,
                    isSandboxed = options.isSandboxed,
                    blenderCommand = options.blenderCommand,
                    importUserConfig = options.importUserConfig,
                    blenderVersion = detectedVersion,
                    runOptions = options,
                    indicator = indicator
                )
            }, LangManager.message("run.configuration.starting", environment.runProfile.name), true, project)
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            return null
        }

        val finalHandler = handler ?: throw ExecutionException(LangManager.message("run.configuration.error.start"))
        
        // --- STEP 4: ATTACH CONSOLE ---
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        val console = consoleBuilder.console
        console.attachToProcess(finalHandler)
        
        return com.intellij.execution.DefaultExecutionResult(console, finalHandler)
    }

    private fun resolveBlenderPathInternal(
        service: BlenderService,
        rawVersion: String,
        onVersionDetected: (String?) -> Unit
    ): String? {
        return when {
            BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == rawVersion } -> {
                service.log("Using managed Blender version: $rawVersion")
                onVersionDetected(rawVersion)
                service.getOrDownloadBlenderPath(rawVersion)
            }
            else -> {
                service.log("Using system Blender path: $rawVersion")
                // rawVersion is a path. Let's find its version for config import.
                val inst = com.sakurasedaia.blenderextensions.blender.services.BlenderScanner.scanSystemInstallations(project).find { it.path == rawVersion }
                onVersionDetected(inst?.version ?: BlenderPathUtil.detectVersion(project, rawVersion))
                rawVersion
            }
        }
    }
}
