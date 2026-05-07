package com.sakurasedaia.blenderdevelopment.model

data class BlenderVersion(
    val majorMinor: Double,
    val fallbackPatch: Int,
    val pythonVersion: String
) {
    val fullVersion get() = "$majorMinor.$fallbackPatch"
}


object BlenderVersions {
    private val VERSION_TABLE = listOf(
        BlenderVersion(4.2, 19, "3.11.7"),
        BlenderVersion(4.5, 8, "3.11.9"),
        BlenderVersion(5.1, 1, "3.13.9")
    )
    
    @Volatile
    private var _cachedVersions: List<BlenderVersion>? = null
    
    private fun getVersionTable(): List<BlenderVersion> {
        _cachedVersions?.let { return it }
        val versions = VERSION_TABLE
        _cachedVersions = versions
        return versions
    }
    
    fun getFullVersion(majorMinor: Double): String? {
        return getVersionTable().find { it.majorMinor == majorMinor }?.fullVersion
    }
    
    fun getPythonVersion(majorMinor: Double): String? {
        return getVersionTable().find { it.majorMinor == majorMinor }?.pythonVersion
    }
    
    fun getStringList(majorMinor: Double): List<String> {
        val selectable = mutableListOf<String>()
        
        selectable.addAll(getVersionTable().map { it.majorMinor.toString() })
        
        return selectable.distinct()
    }
}