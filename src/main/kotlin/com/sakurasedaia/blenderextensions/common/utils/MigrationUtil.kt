package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.common.utils.BlenderTaskManager
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.io.File

object MigrationUtil {

    /**
     * Prepares the target directory according to the rules:
     * 1. If parent exists but subdirectory doesn't: create the subdirectory.
     * 2. If target directory is valid but not empty: create a `blender_pycharm` subdirectory inside it.
     */
    fun prepareTargetDirectory(path: String): File {
        val target = File(path)
        if (!target.exists()) {
            target.mkdirs()
            return target
        }

        if (target.isDirectory) {
            val children = target.list()
            if (children != null && children.isNotEmpty()) {
                // If it's already a blender_downloads or blender_pycharm directory, don't nest it further
                val name = target.name.lowercase()
                if (name == "blender_downloads" || name == "blender_pycharm" || 
                    children.contains("app") || children.contains("python") || children.contains("linter")) {
                    return target
                }
                
                val subDir = File(target, "blender_pycharm")
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
                return subDir
            }
        } else {
            target.mkdirs()
        }
        
        return target
    }

    fun migrate(project: Project, oldPath: String, newPath: String, onComplete: (String) -> Unit) {
        if (oldPath.isBlank() || newPath.isBlank() || oldPath == newPath) {
            onComplete(newPath)
            return
        }

        val sourceDir = File(oldPath)
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            onComplete(newPath)
            return
        }

        val targetDir = prepareTargetDirectory(newPath)
        val finalNewPath = targetDir.absolutePath

        if (sourceDir.absolutePath == targetDir.absolutePath) {
            onComplete(finalNewPath)
            return
        }

        BlenderTaskManager.getInstance().run(project, LangManager.message("migration.progress.title"), true) { indicator ->
            val files = sourceDir.listFiles() ?: return@run
            val total = files.size
            
            files.forEachIndexed { index, file ->
                indicator.checkCanceled()
                indicator.fraction = (index.toDouble() / total)
                indicator.text = LangManager.message("migration.progress.file", file.name)
                
                try {
                    val destination = File(targetDir, file.name)
                    if (file.isDirectory) {
                        FileUtil.copyDir(file, destination)
                        FileUtil.delete(file)
                    } else {
                        FileUtil.copy(file, destination)
                        FileUtil.delete(file)
                    }
                } catch (e: Exception) {
                    // Log error or notify user
                    BlenderLogger.getInstance(project).error("Migration failed for ${file.name}", e)
                }
            }
            
            // Try to delete the old directory if it's empty
            if (sourceDir.list()?.isEmpty() == true) {
                sourceDir.delete()
            }

            ApplicationManager.getApplication().invokeLater {
                onComplete(finalNewPath)
            }
        }
    }
}
