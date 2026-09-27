/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sakurasedaia.blenderdevelopment.stubs

import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions

/** Resolves Blender releases to the linting package declared by the version registry. */
object BlenderStubRequirementResolver {
    private val additionalRequirementsByVersion = mapOf("5.1" to "fake-bpy-module-5.1")

    /**
     * Resolves a Blender major/minor or full version to its configured package requirement. Registry entries take precedence so their
     * `fakeBpy` override is honored.
     *
     * @param blenderVersion selected Blender version.
     * @return exact package requirement, or `null` when the release has no configured mapping.
     */
    fun resolve(blenderVersion: String): String? {
        val normalizedVersion = BlenderVersions.normalizeVersion(blenderVersion)
        return BlenderVersions.getFakeBpyPackageName(normalizedVersion) ?: additionalRequirementsByVersion[normalizedVersion]
    }
}
