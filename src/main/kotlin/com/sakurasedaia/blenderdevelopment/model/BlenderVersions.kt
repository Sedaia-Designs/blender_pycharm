/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.model

/** Version mapping row between Blender and the bundled Python runtime. */
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


/** In-memory version registry used by wizard defaults and compatibility checks. */
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
    
    
    /**
     * Returns the cached version table, initializing cache on first access.
     *
     * @return cached list of [BlenderVersion] entries.
     */
    private fun getVersionTableSafe(): List<BlenderVersion> {
        _cachedVersions?.let { return it }
        val versions = VERSION_TABLE
        _cachedVersions = versions
        return versions
    }
    
    
    /**
     * Returns the loaded Blender/Python compatibility table.
     *
     * @return list of known Blender/Python compatibility rows.
     */
    fun getVersionTable(): List<BlenderVersion> {
        return getVersionTableSafe()
    }
    
    val LIST: List<BlenderVersion>
        get() = getVersionTable()
    

    /**
     * Returns full Blender version (e.g. `4.5.8`) for a major/minor selector (e.g. `4.5`).
     *
     * @param blMajorMinor Blender version selector in major/minor form.
     * @return full Blender version string, or `null` when not found.
     */
    fun getBlenderVersion(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.blVersion
    }

    
    /**
     * Returns full Python version that corresponds to the provided Blender major/minor value.
     *
     * @param blMajorMinor Blender version selector in major/minor form.
     * @return full Python version string, or `null` when no mapping exists.
     */
    fun getPythonVersion(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.pyVersion
    }
    
    
    /**
     * Returns OS/architecture compatibility matrix for the selected Blender version.
     *
     * @param blMajorMinor Blender version selector in major/minor form.
     * @return compatibility map keyed by OS shorthand, or `null` when not found.
     */
    fun getCompatibleArch(blMajorMinor: String): Map<String, List<String>>? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.compatWithOs
    }
    
    
    /**
     * Normalizes version strings to `major.minor` for table lookup.
     *
     * @param version raw version string.
     * @return normalized major/minor version.
     */
    private fun normalizeVersion(version: String): String {
        val parts = version.split('.')
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
    }
}
