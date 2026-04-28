package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.utils.paths.*
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.utils.toBlenderHandler
import com.sakurasedaia.blenderextensions.blender.model.ProgressType
import com.sakurasedaia.blenderextensions.blender.model.DownloadProgress
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore

@Service(Service.Level.PROJECT)
class BlenderLauncher(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)

    fun startBlenderProcess(
        blenderPath: String,
        scriptPath: Path? = null,
        additionalArgs: String? = null,
        isSandboxed: Boolean = false,
        importUserConfig: Boolean = false,
        blenderVersion: String? = null,
        indicator: ProgressIndicator? = null,
        isDebugMode: Boolean = false
    ): OSProcessHandler? {
        logger.log("BlenderLauncher.startBlenderProcess: Starting Blender (Path: $blenderPath, Version: ${blenderVersion ?: "Unknown"}, Debug: $isDebugMode)")
        
        val blenderFile = Paths.get(blenderPath)

        // Ensure execution permission on Unix-like systems
        makeExecutable(blenderFile)

        val commandLine = GeneralCommandLine(blenderFile.absolutePathString())
        commandLine.workDirectory = project.basePath?.let { java.io.File(it) }
        
        // Check for Linux execution restrictions
        val restrictionMessage = getExecutionRestrictionMessage(blenderFile)
        if (restrictionMessage != null) {
            logger.error(restrictionMessage)
            return null
        }
        
        if (scriptPath != null) {
            commandLine.addParameters("--python", scriptPath.absolutePathString())
        }
        
        if (isDebugMode) {
            commandLine.addParameters("--python-expr", "import debugpy; debugpy.wait_for_client(); print('Debugger attached')")
        }

        if (isSandboxed) {
            setupSandbox(commandLine, importUserConfig, blenderVersion, additionalArgs, indicator)
        }
        
        if (!additionalArgs.isNullOrBlank()) {
            commandLine.addParameters(ParametersListUtil.parse(additionalArgs))
        }
        
        logger.log(LangManager.message("log.launcher.executing", commandLine.commandLineString))
        try {
            return KillableProcessHandler(commandLine)
        } catch (e: ExecutionException) {
            val message = e.message ?: ""
            if (message.contains("Permission denied") || message.contains("error=13")) {
                val restrictionMessage = getExecutionRestrictionMessage(blenderFile)
                if (restrictionMessage != null) {
                    logger.error(restrictionMessage)
                    throw ExecutionException(restrictionMessage, e)
                }
            }
            throw e
        }
    }

    private fun setupSandbox(
        commandLine: GeneralCommandLine,
        importUserConfig: Boolean,
        blenderVersion: String?,
        additionalArgs: String? = null,
        indicator: ProgressIndicator? = null
    ) {
        val downloader = BlenderDownloader.getInstance(project)
        val statusText = LangManager.message("log.launcher.using.sandbox")
        val progressHandler = indicator.toBlenderHandler(downloader, blenderVersion ?: "unknown", statusText, ProgressType.SANDBOX)
        
        try {
            indicator?.checkCanceled()
            logger.log("$statusText (Version: ${blenderVersion ?: "Unknown"})")
            val sandboxDir = getSandboxDir(project)
            logger.log("Sandbox directory: ${sandboxDir.absolutePathString()}")
            val configDir = getSandboxConfigDir(project)
            val scriptsDir = getSandboxScriptsDir(project)
            
            configDir.createDirectories()
            scriptsDir.createDirectories()
            excludeDirectory(sandboxDir)
            
            if (importUserConfig) {
                importBlenderConfig(configDir, blenderVersion, indicator)
            }

            // Create a simple app template
            val templatesDir = getSandboxAppTemplatesDir(project)
            templatesDir.createDirectories()
            val initFile = templatesDir.resolve("__init__.py")
            if (!initFile.exists()) {
                initFile.writeText("\"\"\"\n# Blender Extension Development App Template\n# Created by the Blender Extension Development for PyCharm plugin.\n\"\"\"\ndef register():\n    pass\n\ndef unregister():\n    pass\n")
            }

            handleSandboxSplashScreen(templatesDir)
            
            commandLine.withEnvironment("BLENDER_USER_CONFIG", configDir.absolutePathString())
            commandLine.withEnvironment("BLENDER_USER_SCRIPTS", scriptsDir.absolutePathString())

            val isExtensionCommand = additionalArgs?.contains("extension") == true

            if (!isExtensionCommand) {
                commandLine.addParameters("--app-template", "pycharm")
            } else {
                logger.log(LangManager.message("log.launcher.extension.command"))
            }
        } finally {
            downloader.updateProgress(DownloadProgress.None)
        }
    }

    private fun importBlenderConfig(targetConfigDir: Path, version: String?, indicator: ProgressIndicator? = null) {
        val versionToUse = version ?: "5.0" // Fallback to 5.0
        val sourceConfigDir = findSystemBlenderConfigDir(versionToUse)
        
        if (sourceConfigDir == null || !sourceConfigDir.exists()) {
            logger.log(LangManager.message("log.launcher.config.not.found", versionToUse))
            return
        }

        logger.log(LangManager.message("log.launcher.importing.config", sourceConfigDir.absolutePathString()))
        val filesToCopy = listOf("userpref.blend", "startup.blend", "bookmarks.txt", "recent-files.txt", "recent-searches.txt")
        
        for (fileName in filesToCopy) {
            indicator?.checkCanceled()
            val sourceFile = sourceConfigDir.resolve(fileName)
            if (sourceFile.exists()) {
                try {
                    sourceFile.copyTo(targetConfigDir.resolve(fileName), overwrite = true)
                    logger.log(LangManager.message("log.launcher.imported.file", fileName))
                } catch (e: Exception) {
                    logger.log(LangManager.message("log.launcher.failed.import.file", fileName, e.message ?: ""))
                }
            }
        }
        
        // Dynamically detect and copy special directories from the config folder
        sourceConfigDir.listDirectoryEntries().filter { it.isDirectory() }.forEach { sourceDir ->
            indicator?.checkCanceled()
            val dirName = sourceDir.name
            try {
                copyDirectory(sourceDir, targetConfigDir.resolve(dirName))
                logger.log(LangManager.message("log.launcher.imported.folder", dirName))
            } catch (e: Exception) {
                logger.log(LangManager.message("log.launcher.failed.import.folder", dirName, e.message ?: ""))
            }
        }
    }

    private fun findSystemBlenderConfigDir(version: String): Path? {
        return getSystemBlenderConfigDir(version)
    }

    private fun handleSandboxSplashScreen(templatesDir: Path) {
        val projectPath = project.basePath ?: return
        val projectSplash = Paths.get(projectPath, "images/sandbox_splash.png")
        val targetSplash = templatesDir.resolve("splash.png")
        
        if (projectSplash.exists()) {
            try {
                projectSplash.copyTo(targetSplash, overwrite = true)
                logger.log(LangManager.message("log.launcher.copied.splash"))
            } catch (e: Exception) {
                logger.log(LangManager.message("log.launcher.failed.copy.splash", e.message ?: ""))
            }
        } else {
            // Try to copy the default splash from plugin resources
            try {
                this.javaClass.getResourceAsStream("/images/sandbox_splash.png")?.use { input ->
                    Files.copy(input, targetSplash, StandardCopyOption.REPLACE_EXISTING)
                }
                logger.log(LangManager.message("log.launcher.copied.splash"))
            } catch (e: Exception) {
                logger.log(LangManager.message("log.launcher.failed.copy.splash", e.message ?: ""))
            }
        }
    }

    private fun excludeDirectory(path: Path) {
        val virtualFile = VfsUtil.findFileByIoFile(path.toFile(), true) ?: return
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: return
        
        ModuleRootModificationUtil.updateModel(module) { model ->
            val contentEntry = model.contentEntries.find { 
                it.file?.let { file -> VfsUtilCore.isAncestor(file, virtualFile, false) } == true 
            }
            if (contentEntry != null) {
                if (!contentEntry.excludeFolders.any { it.file == virtualFile }) {
                    contentEntry.addExcludeFolder(virtualFile)
                }
            }
        }
    }

    companion object {
        fun getInstance(project: Project): BlenderLauncher = project.service()
    }
}
