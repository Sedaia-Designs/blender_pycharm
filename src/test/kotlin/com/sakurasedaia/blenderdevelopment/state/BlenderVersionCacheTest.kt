package com.sakurasedaia.blenderdevelopment.state

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BlenderVersionCacheTest : BasePlatformTestCase() {
  private lateinit var cache: BlenderVersionCache

  override fun setUp() {
    super.setUp()
    cache = BlenderVersionCache.getInstance()
    cache.clear()
  }

  override fun tearDown() {
    try {
      cache.clear()
    } finally {
      super.tearDown()
    }
  }

  fun testCachesLatestPatchPerConfiguredMinorVersion() {
    cache.cacheDiscoveredVersions(
        listOf(
            listOf(4, 2, 18),
            listOf(4, 2, 21),
            listOf(4, 5, 9),
            listOf(9, 9, 9),
        )
    )

    val versions = cache.getVersionTable()
    assertEquals("4.2.21", versions.first { it.blMajorMinor == "4.2" }.blVersion)
    assertEquals("4.5.9", versions.first { it.blMajorMinor == "4.5" }.blVersion)
    assertEquals("5.2.0", versions.first { it.blMajorMinor == "5.2" }.blVersion)
    val discovered = versions.first { it.blMajorMinor == "9.9" }
    assertEquals("9.9.9", discovered.blVersion)
    assertEquals("", discovered.pyVersion)
  }

  fun testMalformedPersistedVersionsFallBackToConfiguredTable() {
    cache.loadState(BlenderVersionCacheState(versions = listOf("4.2.invalid", "4.5.9.extra", "5.2")))

    val versions = cache.getVersionTable()
    assertEquals("4.2.19", versions.first { it.blMajorMinor == "4.2" }.blVersion)
    assertEquals("4.5.8", versions.first { it.blMajorMinor == "4.5" }.blVersion)
    assertEquals("5.2.0", versions.first { it.blMajorMinor == "5.2" }.blVersion)
  }

  fun testClearRemovesCachedVersionState() {
    cache.cacheDiscoveredVersions(listOf(listOf(4, 3, 9)))
    assertTrue(cache.hasCachedVersions())

    cache.clear()

    assertFalse(cache.hasCachedVersions())
    assertEquals(listOf("4.2", "4.5", "5.2"), cache.getVersionTable().map { it.blMajorMinor })
  }
}
