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

/** Tests the explicit Blender-to-stub requirement allowlist. */
class BlenderStubRequirementResolverTest {
  /** Verifies supported major/minor and full patch versions. */
  @Test
  fun resolvesOnlyVerifiedVersionSpecificPackages() {
    assertEquals("fake-bpy-module-4.2", BlenderStubRequirementResolver.resolve("4.2"))
    assertEquals("fake-bpy-module-4.5", BlenderStubRequirementResolver.resolve("4.5.8"))
    assertEquals("fake-bpy-module-5.1", BlenderStubRequirementResolver.resolve("5.1.3"))
  }

  /** Verifies that current and unknown unsupported releases never use a rolling package. */
  @Test
  fun rejectsUnsupportedVersions() {
    assertNull(BlenderStubRequirementResolver.resolve("5.2"))
    assertNull(BlenderStubRequirementResolver.resolve("9.9.1"))
    assertNull(BlenderStubRequirementResolver.resolve("invalid"))
  }
}
