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
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests registry-backed Blender-to-stub requirement resolution. */
class BlenderStubRequirementResolverTest {
  /** Verifies registry defaults and the additional supported 5.1 mapping. */
  @Test
  fun resolvesOnlyVerifiedVersionSpecificPackages() {
    assertEquals("fake-bpy-module-4.2", BlenderStubRequirementResolver.resolve("4.2"))
    assertEquals("fake-bpy-module-4.5", BlenderStubRequirementResolver.resolve("4.5.8"))
    assertEquals("fake-bpy-module-5.1", BlenderStubRequirementResolver.resolve("5.1.3"))
  }

  /** Verifies that a registry Fake-BPY override takes precedence over major/minor naming. */
  @Test
  fun resolvesRegistryPackageOverride() {
    assertEquals("fake-bpy-module-latest", BlenderStubRequirementResolver.resolve("5.2"))
    assertEquals("fake-bpy-module-latest", BlenderStubRequirementResolver.resolve("5.2.4"))
  }

  /** Verifies that unknown releases do not infer a rolling package. */
  @Test
  fun rejectsUnsupportedVersions() {
    assertNull(BlenderStubRequirementResolver.resolve("9.9.1"))
    assertNull(BlenderStubRequirementResolver.resolve("invalid"))
  }
}
