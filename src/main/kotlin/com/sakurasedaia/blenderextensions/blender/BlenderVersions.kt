package com.sakurasedaia.blenderextensions.blender

data class BlenderVersion(
    val majorMinor: String,
    val fallbackPatch: String,
    val pythonVersion: String? = null
)

object BlenderVersions {

    private val STATIC_SUPPORTED_VERSIONS = listOf(
        BlenderVersion("4.2", "18", "3.11.7"),
        BlenderVersion("4.3", "3", "3.11.9"),
        BlenderVersion("4.4", "3", "3.11.11"),
        BlenderVersion("4.5", "8", "3.11.11"),
        BlenderVersion("5.0", "1", "3.11.13"),
        BlenderVersion("5.1", "0", "3.13.9")
    )

    fun getSupportedVersions(): List<BlenderVersion> {
        return getSupportedVersionsSafe()
    }

    @Volatile
    private var _cachedSupportedVersions: List<BlenderVersion>? = null

    private fun getSupportedVersionsSafe(): List<BlenderVersion> {
        _cachedSupportedVersions?.let { return it }
        val versions = STATIC_SUPPORTED_VERSIONS
        _cachedSupportedVersions = versions
        return versions
    }

    val SUPPORTED_VERSIONS: List<BlenderVersion>
        get() = getSupportedVersions()

    fun getSupportedVersionsWithCustom(): Array<String> {
        return getSupportedVersionsSafe().map { it.majorMinor }.toTypedArray()
    }

    /**
     * Returns a list of all selectable versions, including managed and discovered paths.
     */
    fun getAllSelectableVersions(): List<String> {
        val selectable = mutableListOf<String>()
        
        // Managed versions
        selectable.addAll(getSupportedVersionsSafe().map { it.majorMinor })
        
        // System discovered versions
        val systemInstallations = BlenderScanner.getCachedInstallations() ?: emptyList()
        selectable.addAll(systemInstallations.map { it.path })
        
        return selectable.distinct()
    }
}
