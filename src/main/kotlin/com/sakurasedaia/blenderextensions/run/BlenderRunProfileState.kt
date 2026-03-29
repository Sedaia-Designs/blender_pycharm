package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderService

class BlenderRunProfileState(
    private val project: Project,
    private val options: BlenderRunConfigurationOptions,
    private val environment: ExecutionEnvironment
) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): com.intellij.execution.ExecutionResult? {
        val service = BlenderService.getInstance(project)
        service.log(LangManager.message("run.configuration.starting", environment.runProfile.name))
        
        val version = options.blenderVersion ?: "5.0"
        var detectedVersion: String? = null
        val blenderPath = when {
            com.sakurasedaia.blenderextensions.blender.BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == version } -> {
                service.log(LangManager.message("run.configuration.managed", version))
                detectedVersion = version
                ProgressManager.getInstance().run(object : Task.WithResult<String?, ExecutionException>(project, LangManager.message("run.configuration.downloading", version), true) {
                    override fun compute(indicator: ProgressIndicator): String? {
                        return service.getOrDownloadBlenderPath(version)
                    }
                })
            }
            else -> {
                service.log(LangManager.message("run.configuration.system", version))
                // version is a path. Let's find its version for config import.
                val inst = com.sakurasedaia.blenderextensions.blender.BlenderScanner.scanSystemInstallations().find { it.path == version }
                detectedVersion = inst?.version?.takeIf { it != "Unknown" }
                version // It's a path
            }
        }

        if (blenderPath.isNullOrEmpty()) {
            throw ExecutionException(LangManager.message("run.configuration.error.path"))
        }

        // If version is still unknown, try a quick scan of the path itself if it's a path
        if (detectedVersion == null && (blenderPath.contains("/") || blenderPath.contains("\\"))) {
             detectedVersion = com.sakurasedaia.blenderextensions.system.BlenderFinder.tryGetVersion(blenderPath)
             if (detectedVersion == "Unknown") {
                 val match = Regex("(\\d+\\.\\d+)").find(blenderPath)
                 detectedVersion = match?.groupValues?.get(1)
             }
        }
        
        val handler = service.startBlenderProcess(
            blenderPath = blenderPath,
            addonSourceDir = options.addonSourceDirectory,
            addonSymlinkName = options.addonSymlinkName,
            additionalArgs = options.additionalArguments,
            isSandboxed = options.isSandboxed,
            blenderCommand = options.blenderCommand,
            importUserConfig = options.importUserConfig,
            blenderVersion = detectedVersion,
            runOptions = options
        ) ?: throw ExecutionException(LangManager.message("run.configuration.error.start"))
        
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        val console = consoleBuilder.console
        console.attachToProcess(handler)
        
        return com.intellij.execution.DefaultExecutionResult(console, handler)
    }
}
