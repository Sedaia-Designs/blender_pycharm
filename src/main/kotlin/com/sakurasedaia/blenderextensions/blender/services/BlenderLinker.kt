package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.BlenderProjectPaths
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.common.utils.ExternalProcessUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import com.sakurasedaia.blenderextensions.blender.utils.BlenderPathUtil
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings

@Service(Service.Level.PROJECT)
class BlenderLinker(private val project: Project) {
    private val logger = BlenderLogger.getInstance(project)

    fun linkExtensionSource(addonSourceDir: String?, addonSymlinkName: String?, isSandboxed: Boolean = false) {
        val userRepoDir = getExtensionsRepoDir(isSandboxed) ?: run {
            logger.log("BlenderLinker: Could not determine extensions repository directory (Sandboxed: $isSandboxed)")
            return
        }
        logger.log("BlenderLinker: Linking extension source to: ${userRepoDir.absolutePathString()}")
        
        if (!userRepoDir.exists()) {
            Files.createDirectories(userRepoDir)
        }

        val projectPath = project.basePath ?: return
        
        // Collect all sources to link
        val sourcesToLink = mutableListOf<Pair<Path, String>>()
        
        if (!addonSourceDir.isNullOrEmpty()) {
            val sourcePath = Path.of(addonSourceDir)
            if (sourcePath.exists()) {
                val symlinkName = if (!addonSymlinkName.isNullOrEmpty()) addonSymlinkName else sourcePath.name
                sourcesToLink.add(sourcePath to symlinkName)
            } else {
                logger.log(LangManager.message("log.linker.source.not.found", sourcePath.toString()))
            }
        } else {
            val settings = com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings.getInstance(project)
            val markedSources = settings.getSourceFolders()
            if (markedSources.isNotEmpty()) {
                markedSources.forEach { pathStr ->
                    val sourcePath = Path.of(pathStr)
                    if (sourcePath.exists()) {
                        sourcesToLink.add(sourcePath to sourcePath.name)
                    } else {
                        logger.log(LangManager.message("log.linker.marked.source.not.found", sourcePath.toString()))
                    }
                }
            } else {
                val sourcePath = Path.of(projectPath)
                if (sourcePath.exists()) {
                    sourcesToLink.add(sourcePath to sourcePath.name)
                }
            }
        }

        for ((sourcePath, symlinkName) in sourcesToLink) {
            val targetLink = userRepoDir.resolve(symlinkName)
            if (targetLink.exists()) {
                try {
                    Files.delete(targetLink)
                } catch (e: Exception) {
                    logger.log(LangManager.message("log.linker.failed.delete.link", targetLink.toString(), e.message ?: ""))
                    continue
                }
            }

            try {
                Files.createSymbolicLink(targetLink, sourcePath)
                logger.log(LangManager.message("log.linker.created.link", targetLink.toString(), sourcePath.toString()))
            } catch (e: Exception) {
                logger.log(LangManager.message("log.linker.failed.link", sourcePath.toString(), e.message ?: ""))
                if (BlenderHelper.isWindows()) {
                    createWindowsJunction(targetLink, sourcePath)
                }
            }
        }
    }

    private fun createWindowsJunction(target: Path, source: Path) {
        logger.log(LangManager.message("log.linker.attempt.junction"))
        try {
            val commandLine = GeneralCommandLine("cmd", "/c", "mklink", "/J", target.toString(), source.toString())
            val exitCode = ExternalProcessUtil.executeCommand(commandLine, silentFailure = false, logger = logger)
            if (exitCode == 0) {
                logger.log(LangManager.message("log.linker.junction.success"))
            } else {
                logger.log(LangManager.message("log.linker.junction.failed", exitCode))
            }
        } catch (e: Exception) {
            logger.log(LangManager.message("log.linker.junction.error", e.message ?: ""))
        }
    }
    
    fun getExtensionsRepoDir(isSandboxed: Boolean = false): Path? {
        if (isSandboxed) {
            return BlenderProjectPaths.getSandboxExtensionsPycharmDir(project)
        }
        
        val blenderConfigDir = BlenderPathUtil.getBlenderRootConfigDir() ?: return null

        if (!blenderConfigDir.exists()) return null

        val versions = Files.list(blenderConfigDir).use { stream ->
            stream.filter { path ->
                Files.isDirectory(path) && path.name.all { it.isDigit() || it == '.' }
            }.toList()
        }
        val latestVersion = versions.maxWithOrNull { p1, p2 ->
            BlenderHelper.compareVersions(
                BlenderHelper.parseVersion(p1.name),
                BlenderHelper.parseVersion(p2.name)
            )
        } ?: run {
            logger.log("BlenderLinker: No versioned configuration directories found in ${blenderConfigDir.absolutePathString()}")
            return null
        }
        
        val repoDir = latestVersion.resolve("extensions").resolve("blender_pycharm")
        logger.log("BlenderLinker: Detected latest Blender config version: ${latestVersion.name}, repo path: ${repoDir.absolutePathString()}")
        return repoDir
    }

    companion object {
        fun getInstance(project: Project): BlenderLinker = project.getService(BlenderLinker::class.java)
    }
}
