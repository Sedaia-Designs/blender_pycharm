package com.sakurasedaia.blenderextensions.common.utils.paths

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger

/**
 * Functions for IDE-specific integration, such as SDK and linter management.
 */

/**
 * Adds the Blender linter directory to the current project SDK's classpath.
 */
fun addLinterToCurrentSdk(project: Project, blenderVersion: String) {
    val sdk: Sdk? = ProjectRootManager.getInstance(project).projectSdk
    if (sdk == null) {
        BlenderLogger.warn(project, "Cannot add linter: No project SDK configured.")
        return
    }

    val lintDir = getLintDirectory(blenderVersion, project)
    val lintUrl = VirtualFileManager.constructUrl("file", lintDir.toAbsolutePath().toString())
    val lintFile = VirtualFileManager.getInstance().findFileByUrl(lintUrl)

    if (lintFile == null) {
        BlenderLogger.warn(project, "Linter directory not found at: $lintDir")
        return
    }

    ApplicationManager.getApplication().runWriteAction {
        val modificator = sdk.sdkModificator
        val existingRoots = modificator.getRoots(OrderRootType.CLASSES)
        if (!existingRoots.contains(lintFile)) {
            modificator.addRoot(lintFile, OrderRootType.CLASSES)
            modificator.commitChanges()
            BlenderLogger.log(project, "Successfully added Blender $blenderVersion linter to SDK: ${sdk.name}")
        }
    }
}
