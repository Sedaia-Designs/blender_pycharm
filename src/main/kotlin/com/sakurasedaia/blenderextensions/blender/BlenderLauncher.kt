package com.sakurasedaia.blenderextensions.blender

import com.sakurasedaia.blenderextensions.LangManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

@Service(Service.Level.PROJECT)
class BlenderLauncher(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)

    fun startBlenderProcess(
        blenderPath: String,
        scriptPath: Path? = null,
        additionalArgs: String? = null,
        isSandboxed: Boolean = false,
        blenderCommand: String? = null,
        importUserConfig: Boolean = false,
        blenderVersion: String? = null
    ): OSProcessHandler? {
        val blenderFile = Paths.get(blenderPath)
        if (!blenderFile.exists()) {
            logger.log(LangManager.message("log.service.exec.not.found", blenderPath))
            return null
        }

        val commandLine = GeneralCommandLine(blenderPath)
        commandLine.workDirectory = project.basePath?.let { java.io.File(it) }
        
        if (!blenderCommand.isNullOrBlank()) {
            commandLine.addParameters("--command")
            commandLine.addParameters(blenderCommand.split(" "))
        } else if (scriptPath != null) {
            commandLine.addParameters("--python", scriptPath.absolutePathString())
        }
        
        if (isSandboxed) {
            setupSandbox(commandLine, importUserConfig, blenderVersion, blenderCommand)
        }
        
        if (!additionalArgs.isNullOrBlank()) {
            commandLine.addParameters(additionalArgs.split(" "))
        }
        
        logger.log(LangManager.message("log.launcher.executing", commandLine.commandLineString))
        return OSProcessHandler(commandLine)
    }

    private fun setupSandbox(
        commandLine: GeneralCommandLine,
        importUserConfig: Boolean,
        blenderVersion: String?,
        blenderCommand: String?
    ) {
        logger.log(LangManager.message("log.launcher.using.sandbox"))
        val projectPath = project.basePath ?: return
        val sandboxDir = Paths.get(projectPath, ".venv", "blender_sandbox")
        val configDir = sandboxDir.resolve("config")
        val scriptsDir = sandboxDir.resolve("scripts")
        
        configDir.createDirectories()
        scriptsDir.createDirectories()
        
        if (importUserConfig) {
            importBlenderConfig(configDir, blenderVersion)
        }

        // Create a simple app template
        val templatesDir = scriptsDir.resolve("startup/bl_app_templates/pycharm")
        templatesDir.createDirectories()
        val initFile = templatesDir.resolve("__init__.py")
        if (!initFile.exists()) {
            initFile.writeText("\"\"\"\n# Blender Extension Development App Template\n# Created by the Blender Extension Development for PyCharm plugin.\n\"\"\"\ndef register():\n    pass\n\ndef unregister():\n    pass\n")
        }

        handleSandboxSplashScreen(templatesDir)
        
        commandLine.withEnvironment("BLENDER_USER_CONFIG", configDir.absolutePathString())
        commandLine.withEnvironment("BLENDER_USER_SCRIPTS", scriptsDir.absolutePathString())

        val isExtensionCommand = blenderCommand?.contains("extension") == true

        if (!isExtensionCommand) {
            commandLine.addParameters("--app-template", "pycharm")
        } else {
            logger.log(LangManager.message("log.launcher.extension.command"))
        }
    }

    private fun importBlenderConfig(targetConfigDir: Path, version: String?) {
        val versionToUse = version ?: "5.0" // Fallback to 5.0
        val sourceConfigDir = findSystemBlenderConfigDir(versionToUse)
        
        if (sourceConfigDir == null || !sourceConfigDir.exists()) {
            logger.log(LangManager.message("log.launcher.config.not.found", versionToUse))
            return
        }

        logger.log(LangManager.message("log.launcher.importing.config", sourceConfigDir.absolutePathString()))
        val filesToCopy = listOf("userpref.blend", "startup.blend", "bookmarks.txt", "recent-files.txt", "recent-searches.txt")
        
        for (fileName in filesToCopy) {
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
            val dirName = sourceDir.name
            try {
                copyDirectory(sourceDir, targetConfigDir.resolve(dirName))
                logger.log(LangManager.message("log.launcher.imported.folder", dirName))
            } catch (e: Exception) {
                logger.log(LangManager.message("log.launcher.failed.import.folder", dirName, e.message ?: ""))
            }
        }
    }

    private fun copyDirectory(source: Path, target: Path) {
        source.walk().forEach { sourcePath ->
            val targetPath = target.resolve(source.relativize(sourcePath))
            if (sourcePath.isDirectory()) {
                targetPath.createDirectories()
            } else {
                sourcePath.copyTo(targetPath, overwrite = true)
            }
        }
    }

    private fun findSystemBlenderConfigDir(version: String): Path? {
        return BlenderPathUtil.getSystemBlenderConfigDir(version)
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

    companion object {
        fun getInstance(project: Project): BlenderLauncher = project.service()
    }
}
