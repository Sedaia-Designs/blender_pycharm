package com.sakurasedaia.blenderdevelopment.lib.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapeBlenderVersionListsTest {
  private val scraper = ScrapeBlenderVersionLists()

  @Test
  fun testParsesReleaseDirectoryLinksInSiteOrder() {
    val html = """
      <html>
      <body>
      <a href="../">../</a>
      <a href="Blender1.0/">Blender1.0/</a>
      <a href="Blender2.28a/">Blender2.28a/</a>
      <a href="Blender2.50alpha/">Blender2.50alpha/</a>
      <a href="Blender4.2/">Blender4.2/</a>
      <a href="Blender5.2/">Blender5.2/</a>
      </body>
      </html>
    """.trimIndent()

    assertEquals(
      listOf("1.0", "2.28a", "2.50alpha", "4.2", "5.2"),
      scraper.parseAvailableVersions(html),
    )
  }

  @Test
  fun testIgnoresNonReleaseLinksAndDuplicateVersions() {
    val html = """
      <a href="Blender4.2/">Blender4.2/</a>
      <a href="Blender4.2/">duplicate</a>
      <a href="BlenderBenchmark2.0/">BlenderBenchmark2.0/</a>
      <a href="Publisher2.25/">Publisher2.25/</a>
      <a href="blender2.04-ipaq.zip">blender2.04-ipaq.zip</a>
      <a>missing href</a>
    """.trimIndent()

    assertEquals(listOf("4.2"), scraper.parseAvailableVersions(html))
  }

  @Test
  fun testReturnsEmptyListForHtmlWithoutReleaseLinks() {
    assertTrue(scraper.parseAvailableVersions("<html><body>No releases</body></html>").isEmpty())
  }

  @Test
  fun testParsesLatestPatchVersionForRequestedMinorRelease() {
    val html = """
      <a href="blender-5.2.0-linux-x64.tar.xz">Linux</a>
      <a href="blender-5.2.0-macos-arm64.dmg">macOS</a>
      <a href="blender-5.2.0.sha256">checksum</a>
      <a href="blender-5.2.1-windows-x64.zip">Windows</a>
      <a href="blender-5.1.9-windows-x64.zip">different minor</a>
      <a href="blender-benchmark-5.2.0.zip">benchmark</a>
    """.trimIndent()

    assertEquals(
      listOf(5, 2, 1),
      scraper.parsePatchVersions(html, "5.2"),
    )
  }

  @Test
  fun testFiltersMinorVersionsAtConfiguredMinimum() {
    assertEquals(
      listOf("4.2", "4.5", "5.0"),
      scraper.filterMinorVersions(listOf("3.6", "4.1", "4.2", "4.5", "5.0", "2.50alpha"), "4.2"),
    )
    assertEquals(
      listOf("4.10", "5.0"),
      scraper.filterMinorVersions(listOf("4.9", "4.10", "5.0"), "4.10"),
    )
  }
}
