package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.sakurasedaia.blenderextensions.common.utils.BlenderTaskManager
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderService
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.common.utils.paths.*

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
            BlenderTaskManager.getInstance().runModal(project, LangManager.message("run.configuration.starting", environment.runProfile.name), true) { indicator ->
                // --- STEP 1 & 2: RESOLVE BLENDER PATH AND VERSION ---
                blenderPath = resolveBlenderPathInternal(service, version) { detectedVersion = it }
                
                if (blenderPath == null) return@runModal
    
                service.log("Final Startup Parameters - Path: $blenderPath, Version: $detectedVersion, Sandboxed: ${options.isSandboxed}")
    
                val isDebug = executor.id == com.intellij.openapi.wm.ToolWindowId.DEBUG
                options.isDebugMode = isDebug
                
                val finalArgs = buildFinalArgs(project, options)

                if (isDebug && blenderPath != null) {
                    val pythonService = com.sakurasedaia.blenderextensions.python.PythonService.getInstance(project)
                    pythonService.ensureDebugpyInstalled(blenderPath!!, indicator)
                }

                // --- STEP 3: START BLENDER PROCESS ---
                handler = service.startBlenderProcess(
                    blenderPath = blenderPath!!,
                    addonSourceDir = options.addonSourceDirectory,
                    addonSymlinkName = options.addonSymlinkName,
                    additionalArgs = finalArgs,
                    isSandboxed = options.isSandboxed,
                    importUserConfig = options.importUserConfig,
                    blenderVersion = detectedVersion,
                    runOptions = options,
                    indicator = indicator,
                    isDebugMode = isDebug
                )

                if (isDebug && handler != null) {
                    service.log("Starting IDE debugger attachment to localhost:5678...")
                    attachDebugger(project, handler!!)
                }
            }
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
                onVersionDetected(inst?.version ?: detectVersion(project, rawVersion))
                rawVersion
            }
        }
    }

    private fun buildFinalArgs(project: Project, options: BlenderRunConfigurationOptions): String? {
        val factory = environment.runProfile.let { (it as? BlenderRunConfiguration)?.factory }
        val isBuild = factory is BlenderBuildConfigurationFactory
        val isValidate = factory is BlenderValidateConfigurationFactory
        
        if (isBuild || isValidate) {
            val srcDir = options.addonSourceDirectory.takeIf { !it.isNullOrBlank() } ?: getSrcPath(project)
            val baseCmd = if (isBuild) "extension build" else "extension validate"
            val args = mutableListOf<String>()
            
            if (isBuild) {
                args.add("--source-dir")
                args.add(srcDir)
                if (!options.addonOutputDirectory.isNullOrBlank()) {
                    args.add("--output-dir")
                    args.add(options.addonOutputDirectory!!)
                }
            } else {
                args.add(srcDir)
            }
            
            val fullCmd = "--command $baseCmd ${com.intellij.util.execution.ParametersListUtil.join(args)}"
            return if (!options.additionalArguments.isNullOrBlank()) "$fullCmd ${options.additionalArguments}" else fullCmd
        }
        
        return if (!options.blenderCommand.isNullOrBlank()) {
            val cmd = "--command ${options.blenderCommand}"
            if (!options.additionalArguments.isNullOrBlank()) "$cmd ${options.additionalArguments}" else cmd
        } else {
            options.additionalArguments
        }
    }

    private fun attachDebugger(project: Project, handler: OSProcessHandler) {
        val configurationType = ConfigurationTypeUtil.findConfigurationType("PythonConfigurationType") ?: return
        val factory = configurationType.configurationFactories.find { it.id == "PythonDebugServer" } ?: return
        val runManager = com.intellij.execution.RunManager.getInstance(project)
        val settings = runManager.createConfiguration("Blender Debugger", factory)
        
        val configuration = settings.configuration
        // Use reflection to set properties to avoid compile-time dependency on Python plugin internal classes
        try {
            val cls = configuration.javaClass
            cls.methods.find { it.name == "setPort" && it.parameterCount == 1 }?.invoke(configuration, 5678)
            cls.methods.find { it.name == "setHost" && it.parameterCount == 1 }?.invoke(configuration, "localhost")

            // Path mappings are critical for the debugger to find the source files
            // when the code is running inside Blender's sandbox or linked via symlinks
            val projectPath = project.basePath
            if (projectPath != null) {
                val mappings = mutableListOf<Pair<String, String>>()
                
                // Map the local project directory to the execution context
                // If sandboxed, we might need more specific mappings, but the standard project root is a good start
                mappings.add(projectPath to projectPath)
                
                // Try to find setMapping method
                val setMappingMethod = cls.methods.find { it.name == "setMapping" && it.parameterCount == 1 }
                if (setMappingMethod != null) {
                    setMappingMethod.invoke(configuration, mappings)
                }
            }
        } catch (e: Exception) {
            // Fallback or log error
        }

        com.intellij.execution.ProgramRunnerUtil.executeConfiguration(settings, com.intellij.execution.executors.DefaultDebugExecutor.getDebugExecutorInstance())
    }
}
