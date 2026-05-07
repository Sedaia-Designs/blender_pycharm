package com.sakurasedaia.blenderdevelopment.model

data class BlenderVersion(
    val majorMinor: Double,
    val fallbackPatch: Int,
    val pythonVersion: String,
    val compatWithOs: Map<String, List<String>>
) {
    val fullVersion get() = "$majorMinor.$fallbackPatch"
}


object BlenderVersions {
    private val VERSION_TABLE = listOf(
        BlenderVersion(
            4.2,
            19,
            "3.11.7",
            mapOf(
                "win" to listOf("x64"),
                "mac" to listOf("x64", "arm64"),
                "linux" to listOf("x64")
            )),
        BlenderVersion(
            4.5,
            8,
            "3.11.9",
            mapOf(
                "win" to listOf("x64", "arm64"),
                "mac" to listOf("x64", "arm64"),
                "linux" to listOf("x64")
            )),
        BlenderVersion(
            5.1,
            1,
            "3.13.9",
            mapOf(
                "win" to listOf("x64", "arm64"),
                "mac" to listOf("arm64"),
                "linux" to listOf("x64")
            ))
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
    
    fun getCompatibleArch(majorMinor: Double): Map<String, List<String>>? {
        return getVersionTable().find { it.majorMinor == majorMinor }?.compatWithOs
    }
}