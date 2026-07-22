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

package com.sakurasedaia.blenderdevelopment.lib

import com.sakurasedaia.blenderdevelopment.util.SystemHelper

/**
 * Version mapping row between a Blender release and its bundled Python runtime.
 *
 * @property compatWithOs compatibility matrix keyed by OS name (`windows`, `macos`, `linux`)
 * with supported architecture values (for example, `x64`, `arm64`).
 */
data class BlenderVersion(
    private val blender: List<Int>,
    private val python: List<Int>,
    private val fakeBpy: String? = null,
    val compatWithOs: Map<String, List<String>>
) {
    /** Full Blender version in `major.minor.patch` format. */
    val blVersion: String get() = blender.joinToString(separator = ".")
    /** Full Python version in `major.minor.patch` format. */
    val pyVersion: String get() = python.joinToString(separator = ".")
    
    /** Blender version selector in `major.minor` format. */
    val blMajorMinor: String get() = "${blender[0]}.${blender[1]}"
    /** Python version selector in `major.minor` format. */
    val pyMajorMinor: String get() = "${python[0]}.${python[1]}"
    
    /** Blender patch component as a string. */
    val blFallback: String get() = blender[2].toString()
    /** Python patch component as a string. */
    val pyFallback: String get() = python[2].toString()
    
    /** Blender version components as `[major, minor, patch]`. */
    val blVersionList: List<Int> get() = blender
    /** Python version components as `[major, minor, patch]`. */
    val pyVersionList: List<Int> get() = python
    
    /** Fake-BPY package name, including any release-specific package suffix override. */
    val fakeBpyPackage = "fake-bpy-module-${fakeBpy ?: blMajorMinor}"

    /**
     * Builds the official Blender download URL for a supported platform artifact.
     *
     * @param platform target operating system name or alias.
     * @param arch target CPU architecture name or alias.
     * @param fileExtension requested distribution file suffix.
     * @return artifact URL, or an empty string when the target combination is unsupported.
     */
    fun getDownloadURL(
        fileExtension: String = SystemHelper.getSysInfo.bundleFileType
    ): String {
        val normalizedPlatform = SystemHelper.normalizeOSName
        val normalizedArch = SystemHelper.normalizeOsArch
        if (
            compatWithOs[normalizedPlatform]?.contains(normalizedArch) != true
            ||
            !SystemHelper.isBundleFileTypeSupported(normalizedPlatform, fileExtension)
        ) {
            return ""
        }
        return "${DOWNLOAD_BASE_URL}Blender$blMajorMinor/blender-$blVersion-$normalizedPlatform-$normalizedArch.$fileExtension"
    }

    companion object {
        private const val DOWNLOAD_BASE_URL = "https://download.blender.org/release/"
    }
}


/** In-memory version registry used by wizard defaults and compatibility checks. */
object BlenderVersions {
    private val cacheLock = Any()
    private val VERSION_TABLE = listOf(
        BlenderVersion(
            blender = listOf(4, 2, 19),
            python = listOf(3,11,7),
            compatWithOs = mapOf(
                "windows" to listOf("x64"),
                "macos" to listOf("x64", "arm64"),
                "linux" to listOf("x64")
            )),
        BlenderVersion(
            blender = listOf(4,5,8),
            python = listOf(3,11,9),
            compatWithOs = mapOf(
                "windows" to listOf("x64", "arm64"),
                "macos" to listOf("x64", "arm64"),
                "linux" to listOf("x64")
            )),
        BlenderVersion(
            blender = listOf(5,2,0),
            python = listOf(3,13,13),
            fakeBpy = "latest",
            compatWithOs = mapOf(
                "windows" to listOf("x64", "arm64"),
                "macos" to listOf("arm64"),
                "linux" to listOf("x64")
            ))
    )
    
    @Volatile
    private var _cachedVersions: List<BlenderVersion>? = null

    internal fun supportedMinorVersions(): List<String> = VERSION_TABLE.map(BlenderVersion::blMajorMinor)

    internal fun cacheDiscoveredVersions(discoveredVersions: List<List<Int>>) {
        val latestPatchByMinor = discoveredVersions
            .filter { it.size == 3 }
            .groupBy { normalizeVersionFromList(it) }
            .mapValues { (_, versions) -> versions.maxWith(compareBy({ it[0] }, { it[1] }, { it[2] })) }

        val refreshedVersions = VERSION_TABLE.map { configuredVersion ->
            val discoveredVersion = latestPatchByMinor[configuredVersion.blMajorMinor]
            if (discoveredVersion == null) configuredVersion else configuredVersion.copy(blender = discoveredVersion)
        }

        synchronized(cacheLock) {
            _cachedVersions = refreshedVersions
        }
    }

    internal fun resetCache() {
        synchronized(cacheLock) {
            _cachedVersions = null
        }
    }
    
    
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
    
    /** Public alias for [getVersionTable]. */
    val LIST: List<BlenderVersion>
        get() = getVersionTable()
    

    /**
     * Returns full Blender version (e.g. `4.5.8`) for a major/minor selector (e.g. `4.5`).
     *
     * @param blMajorMinor Blender version selector in `major.minor` form (also accepts full versions).
     * @return full Blender version string, or `null` when not found.
     */
    fun getBlenderVersion(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.blVersion
    }
    
    /**
     * Returns full Python version that corresponds to the provided Blender major/minor value.
     *
     * @param blMajorMinor Blender version selector in `major.minor` form (also accepts full versions).
     * @return full Python version string, or `null` when no mapping exists.
     */
    fun getPythonVersion(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.pyVersion
    }
    
    /**
     * Returns the configured Fake-BPY package name for a Blender version.
     *
     * @param blMajorMinor Blender version selector in `major.minor` form (also accepts full versions).
     * @return package name using the version row's override when present, or `null` when not found.
     */
    fun getFakeBpyPackageName(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.fakeBpyPackage
    }
    
    /**
     * Returns OS/architecture compatibility matrix for the selected Blender version.
     *
     * @param blMajorMinor Blender version selector in `major.minor` form (also accepts full versions).
     * @return compatibility map keyed by `win`, `mac`, or `linux`, or `null` when not found.
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
    fun normalizeVersion(version: String): String {
        val parts = version.split('.')
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
    }
    
    fun normalizeVersionFromList(version: List<Int>): String {
        if (version.size >= 2) {
            return version.take(2).joinToString(".")
        }
        return version.joinToString(".")
    }
}
