/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * Licensed under the GNU General Public License v3 or later.
 */

package com.sakurasedaia.blenderdevelopment.uvPython

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Project-level coroutine scope used by the Blender plugin to launch background
 * `uv` operations from non-suspending entry points (e.g. the NewProjectWizard's
 * `setupProject`). The scope is supplied by the platform and is cancelled
 * automatically when the project is closed.
 */
@Service(Service.Level.PROJECT)
class UvProjectScope(val scope: CoroutineScope) {
    companion object {
        fun get(project: Project): CoroutineScope = project.service<UvProjectScope>().scope
    }
}
