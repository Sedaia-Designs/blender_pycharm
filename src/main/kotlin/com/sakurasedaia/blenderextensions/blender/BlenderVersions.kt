package com.sakurasedaia.blenderextensions.blender

import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.io.HttpRequests
import java.util.concurrent.TimeUnit

data class BlenderVersion(
    val majorMinor: String,
    val fallbackPatch: String
)

object BlenderVersions {
    private const val CACHE_KEY_VERSIONS = "com.sakurasedaia.blenderextensions.versions.cache"
    private const val CACHE_KEY_TIMESTAMP = "com.sakurasedaia.blenderextensions.versions.timestamp"
    private val CACHE_EXPIRATION = TimeUnit.HOURS.toMillis(24)

    private val STATIC_SUPPORTED_VERSIONS = listOf(
        BlenderVersion("4.2", "18"),
        BlenderVersion("4.5", "8"),
        BlenderVersion("5.1", "0")
    )

    fun getSupportedVersions(): List<BlenderVersion> {
        return _supportedVersions
    }

    private val _supportedVersions by lazy {
        val dynamicVersions = getDynamicVersions()
        val staticVersions = STATIC_SUPPORTED_VERSIONS
        
        // Combine and prioritize static versions to keep their fallback patches
        val allVersions = (staticVersions + dynamicVersions).distinctBy { it.majorMinor }
        
        allVersions.filter {
            val parts = it.majorMinor.split(".")
            if (parts.size >= 2) {
                val major = parts[0].toIntOrNull() ?: 0
                val minor = parts[1].toIntOrNull() ?: 0
                major > 4 || (major == 4 && minor >= 2)
            } else false
        }.sortedByDescending { it.majorMinor } // Latest first
    }

    val SUPPORTED_VERSIONS: List<BlenderVersion>
        get() = getSupportedVersions()

    private fun getDynamicVersions(): List<BlenderVersion> {
        val properties = PropertiesComponent.getInstance()
        val cached = properties.getValue(CACHE_KEY_VERSIONS)
        val timestamp = properties.getLong(CACHE_KEY_TIMESTAMP, 0L)

        if (cached != null && System.currentTimeMillis() - timestamp < CACHE_EXPIRATION) {
            return cached.split(",").filter { it.isNotEmpty() }.map { BlenderVersion(it, "0") }
        }

        return try {
            val html = HttpRequests.request("https://download.blender.org/release/").readString()
            val versionRegex = Regex("Blender(\\d+\\.\\d+)/")
            val versions = versionRegex.findAll(html)
                .map { it.groupValues[1] }
                .distinct()
                .toList()

            if (versions.isNotEmpty()) {
                properties.setValue(CACHE_KEY_VERSIONS, versions.joinToString(","))
                properties.setValue(CACHE_KEY_TIMESTAMP, System.currentTimeMillis().toString())
                versions.map { BlenderVersion(it, "0") }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSupportedVersionsWithCustom(): Array<String> {
        return getSupportedVersions().map { it.majorMinor }.toTypedArray()
    }

    /**
     * Get a list of all selectable versions, including managed and discovered.
     * Managed versions are just their version strings (e.g. "5.0").
     * Discovered versions are their absolute paths.
     */
    fun getAllSelectableVersions(): List<String> {
        val selectable = mutableListOf<String>()
        
        // Managed versions
        selectable.addAll(getSupportedVersions().map { it.majorMinor })
        
        // System discovered versions
        val systemInstallations = BlenderScanner.scanSystemInstallations()
        selectable.addAll(systemInstallations.map { it.path })
        
        return selectable.distinct()
    }
}
