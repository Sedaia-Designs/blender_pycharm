/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sakurasedaia.blenderdevelopment.stubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderStubDependencyFileUpdaterTest {
  @Test
  fun replacesOnlyStubRequirementInExistingDevGroup() {
    val original = """
      [project]
      name = "sample"

      [dependency-groups]
      dev = ["fake-bpy-module-4.2", "pytest"]
    """.trimIndent()

    val updated = BlenderStubDependencyFileUpdater.updateContent(original, "fake-bpy-module-4.5")

    assertTrue(updated.contains("dev = [\"fake-bpy-module-4.5\", \"pytest\"]"))
  }

  @Test
  fun addsDevGroupWhenMissing() {
    val updated = BlenderStubDependencyFileUpdater.updateContent(
      "[project]\nname = \"sample\"\n",
      "fake-bpy-module-4.2",
    )

    assertTrue(updated.contains("[dependency-groups]\ndev = [\"fake-bpy-module-4.2\"]"))
  }

  @Test
  fun repeatedUpdateIsIdempotent() {
    val original = "[dependency-groups]\ndev = [\"fake-bpy-module-4.5\"]\n"
    val updated = BlenderStubDependencyFileUpdater.updateContent(original, "fake-bpy-module-4.5")

    assertEquals(original, updated)
  }
}
