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

/**
 * Handles the actual execution of a Blender run configuration.
 */
class BlenderRunProfileState(
    private val project: Project,
    private val options: BlenderRunConfigurationOptions,
    private val environment: ExecutionEnvironment
) : RunProfileState {
    
    /**
     * Executes the run configuration.
     * 
     * The execution follows these steps:
     * 1. Resolve the Blender path (managed or system installation).
     * 2. Detect the Blender version from the path if unknown.
     * 3. Start the Blender process with the configured arguments.
     * 4. Attach a console to the process for logging.
     */
    override fun execute(executor: Executor, runner: ProgramRunner<*>): com.intellij.execution.ExecutionResult? {
        val service = BlenderService.getInstance(project)
        val version = options.blenderVersion ?: "5.0"
        
        service.log("--- Starting Blender Run Configuration: ${environment.runProfile.name} ---")
        service.log("Requested version/path: $version")
        
        // --- STEP 1 & 2: RESOLVE BLENDER PATH AND VERSION ---
        var detectedVersion: String? = null
        val blenderPath = resolveBlenderPath(service, version) { detectedVersion = it }
            ?: throw ExecutionException(LangManager.message("run.configuration.error.start"))

        service.log("Final Startup Parameters - Path: $blenderPath, Version: $detectedVersion, Sandboxed: ${options.isSandboxed}")
        
        // --- STEP 3: START BLENDER PROCESS ---
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
        
        // --- STEP 4: ATTACH CONSOLE ---
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        val console = consoleBuilder.console
        console.attachToProcess(handler)
        
        return com.intellij.execution.DefaultExecutionResult(console, handler)
    }

    /**
     * Resolves the Blender path based on whether it's a managed version or a direct path.
     */
    private fun resolveBlenderPath(
        service: BlenderService,
        rawVersion: String,
        onVersionDetected: (String?) -> Unit
    ): String? {
        return when {
            BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == rawVersion } -> {
                service.log("Using managed Blender version: $rawVersion")
                onVersionDetected(rawVersion)
                
                var result: String? = null
                ProgressManager.getInstance().runProcessWithProgressSynchronously({
                    result = service.getOrDownloadBlenderPath(rawVersion)
                }, LangManager.message("run.configuration.downloading", rawVersion), true, project)
                result
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
