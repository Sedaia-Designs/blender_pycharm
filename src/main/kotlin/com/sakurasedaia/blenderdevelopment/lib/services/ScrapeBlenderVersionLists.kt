package com.sakurasedaia.blenderdevelopment.lib.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.io.HttpRequests
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.BlenderVersionCache
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator


@Service
internal class ScrapeBlenderVersionLists {
  val BLENDER_VERSION_SITE: String = "https://download.blender.org/release/"

  companion object {
    private val VERSION_DIRECTORY_PATTERN = Regex(
      pattern = "^Blender(\\d+(?:\\.\\d+)+(?:[a-z]+)?)/$",
      option = RegexOption.IGNORE_CASE,
    )
    private val PATCH_VERSION_PATTERN = Regex(
      pattern = "^blender-(\\d+\\.\\d+\\.\\d+)(?:[.-].*)?$",
      option = RegexOption.IGNORE_CASE,
    )

    fun getInstance(): ScrapeBlenderVersionLists = service()
  }

  suspend fun getAvailableVersions(url: String = BLENDER_VERSION_SITE): List<String> =
    withContext(Dispatchers.IO) {
      filterMinorVersions(
        parseAvailableVersions(getHTML(url)),
        PluginConfig.getInstance().getMinimumBlenderVersion(),
      )
    }

  internal suspend fun refreshVersionCache(): List<BlenderVersion> = withContext(Dispatchers.IO) {
    val availableMinorVersions = filterMinorVersions(
      parseAvailableVersions(getHTML(BLENDER_VERSION_SITE)),
      PluginConfig.getInstance().getMinimumBlenderVersion(),
    )
      .toSet()
    val discoveredPatchVersions = BlenderVersions.supportedMinorVersions()
      .filter(availableMinorVersions::contains)
      .mapNotNull { minorVersion ->
        val releaseUrl = "${BLENDER_VERSION_SITE}Blender$minorVersion/"
        parsePatchVersions(getHTML(releaseUrl), minorVersion)
      }

    val cache = BlenderVersionCache.getInstance()
    cache.cacheDiscoveredVersions(discoveredPatchVersions)
    cache.getVersionTable()
  }

  internal fun getHTML(url: String): String = HttpRequests.request(url)
    .connectTimeout(5_000)
    .readTimeout(10_000)
    .readString()

  internal fun parseAvailableVersions(html: String): List<String> {
    val versions = linkedSetOf<String>()
    parseLinks(html) { href ->
      VERSION_DIRECTORY_PATTERN.matchEntire(href)?.groupValues?.get(1)?.let(versions::add)
    }
    return versions.toList()
  }

  internal fun parsePatchVersions(html: String, minorVersion: String): List<Int>? {
    val versions = linkedSetOf<List<Int>>()
    parseLinks(html) { href ->
      val version = PATCH_VERSION_PATTERN.matchEntire(href)?.groupValues?.get(1) ?: return@parseLinks
      if (version.substringBeforeLast('.') != minorVersion) return@parseLinks

      versions += version.split('.').map(String::toInt)
    }
    return versions.maxWithOrNull(compareBy({ it[0] }, { it[1] }, { it[2] }))
  }

  internal fun filterMinorVersions(versions: List<String>, minimumVersion: String): List<String> {
    val minimum = parseMinorVersion(minimumVersion) ?: return emptyList()
    return versions.filter { version -> parseMinorVersion(version)?.let { it >= minimum } == true }
  }

  private fun parseMinorVersion(version: String): MinorVersion? {
    val parts = version.split('.')
    if (parts.size != 2) return null
    return MinorVersion(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
  }

  private fun parseLinks(html: String, consumeHref: (String) -> Unit) {
    val callback = object : HTMLEditorKit.ParserCallback() {
      override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
        if (tag != HTML.Tag.A) return

        val href = attributes.getAttribute(HTML.Attribute.HREF) as? String ?: return
        consumeHref(href)
      }
    }

    ParserDelegator().parse(StringReader(html), callback, true)
  }

  private data class MinorVersion(val major: Int, val minor: Int) : Comparable<MinorVersion> {
    override fun compareTo(other: MinorVersion): Int =
      compareValuesBy(this, other, MinorVersion::major, MinorVersion::minor)
  }
}
