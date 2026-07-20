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

/** Resolves explicitly supported Blender releases to their version-matched linting package. */
object BlenderStubRequirementResolver {
  private val requirementsByVersion = mapOf(
    "4.2" to "fake-bpy-module-4.2",
    "4.5" to "fake-bpy-module-4.5",
    "5.1" to "fake-bpy-module-5.1",
  )

  /**
   * Resolves a Blender major/minor or full version to a verified package requirement.
   *
   * @param blenderVersion selected Blender version.
   * @return exact package requirement, or `null` when the release is not verified.
   */
  fun resolve(blenderVersion: String): String? =
    requirementsByVersion[BlenderVersions.normalizeVersion(blenderVersion)]
}
