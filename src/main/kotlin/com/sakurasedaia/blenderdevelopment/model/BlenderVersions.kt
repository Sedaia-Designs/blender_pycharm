package com.sakurasedaia.blenderdevelopment.model

data class BlenderVersion(
    private val blender: List<Int>,
    private val python: List<Int>,
    val compatWithOs: Map<String, List<String>>
) {
    val blVersion: String get() = blender.joinToString(separator = ".")
    val pyVersion: String get() = python.joinToString(separator = ".")
    
    val blMajorMinor: String get() = "${blender[0]}.${blender[1]}"
    val pyMajorMinor: String get() = "${python[0]}.${python[1]}"
    
    val blFallback: String get() = blender[2].toString()
    val pyFallback: String get() = python[2].toString()
    
    val blVersionList: List<Int> get() = blender
    val pyVersionList: List<Int> get() = python
}

object BlenderVersions {
    private val VERSION_TABLE = listOf(
        BlenderVersion(
            blender = listOf(4, 2, 19),
            python = listOf(3,11,7),
            compatWithOs = mapOf(
                "win" to listOf("x64"),
                "mac" to listOf("x64", "arm64"),
                "linux" to listOf("x64")
            )),
        BlenderVersion(
            blender = listOf(4,5,8),
            python = listOf(3,11,9),
            compatWithOs = mapOf(
                "win" to listOf("x64", "arm64"),
                "mac" to listOf("x64", "arm64"),
                "linux" to listOf("x64")
            )),
        BlenderVersion(
            blender = listOf(5,1,1),
            python = listOf(3,13,9),
            compatWithOs = mapOf(
                "win" to listOf("x64", "arm64"),
                "mac" to listOf("arm64"),
                "linux" to listOf("x64")
            ))
    )
    
    @Volatile
    private var _cachedVersions: List<BlenderVersion>? = null
    
    private fun getVersionTableSafe(): List<BlenderVersion> {
        _cachedVersions?.let { return it }
        val versions = VERSION_TABLE
        _cachedVersions = versions
        return versions
    }
    
    fun getVersionTable(): List<BlenderVersion> {
        return getVersionTableSafe()
    }
    
    val LIST: List<BlenderVersion>
        get() = getVersionTable()
    
    fun getBlenderVersion(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.blVersion
    }

    fun getPythonVersion(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.pyVersion
    }
    
    fun getCompatibleArch(blMajorMinor: String): Map<String, List<String>>? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.compatWithOs
    }
    
    private fun normalizeVersion(version: String): String {
        val parts = version.split('.')
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
    }
}