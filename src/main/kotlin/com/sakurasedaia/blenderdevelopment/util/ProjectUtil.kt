package com.sakurasedaia.blenderdevelopment.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

/**
 * Returns the first open, active project, or the IDE default project when none are open.
 *
 * @return the project suitable for project-level services when no explicit project is available.
 */
fun currentProject(): Project {
  val projectManager = ProjectManager.getInstance()
  return projectManager.openProjects.firstOrNull { it.isOpen && !it.isDisposed }
    ?: projectManager.defaultProject
}
