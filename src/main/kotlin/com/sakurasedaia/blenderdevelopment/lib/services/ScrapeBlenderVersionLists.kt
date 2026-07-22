package com.sakurasedaia.blenderdevelopment.lib.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.io.HttpRequests
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

    fun getInstance(): ScrapeBlenderVersionLists = service()
  }

  suspend fun getAvailableVersions(url: String = BLENDER_VERSION_SITE): List<String> =
    withContext(Dispatchers.IO) {
      parseAvailableVersions(getHTML(url))
    }

  internal fun getHTML(url: String): String = HttpRequests.request(url)
    .connectTimeout(5_000)
    .readTimeout(10_000)
    .readString()

  internal fun parseAvailableVersions(html: String): List<String> {
    val versions = linkedSetOf<String>()
    val callback = object : HTMLEditorKit.ParserCallback() {
      override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
        if (tag != HTML.Tag.A) return

        val href = attributes.getAttribute(HTML.Attribute.HREF) as? String ?: return
        VERSION_DIRECTORY_PATTERN.matchEntire(href)?.groupValues?.get(1)?.let(versions::add)
      }
    }

    ParserDelegator().parse(StringReader(html), callback, true)
    return versions.toList()
  }
}
