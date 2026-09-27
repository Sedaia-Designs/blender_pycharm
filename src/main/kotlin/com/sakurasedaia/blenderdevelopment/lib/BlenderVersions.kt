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

import com.intellij.openapi.application.ApplicationManager
import com.sakurasedaia.blenderdevelopment.state.BlenderVersionCache
import com.sakurasedaia.blenderdevelopment.util.SystemInfo

/**
 * Version mapping row between a Blender release and its bundled Python runtime.
 *
 * @property compatWithOs compatibility matrix keyed by OS name (`windows`, `macos`, `linux`) with supported architecture values (for
 *   example, `x64`, `arm64`).
 */
data class BlenderVersion(
    private val installName: String,
    val blVersionList: List<Int>,
    val pyVersionList: List<Int> = emptyList(),
    private val fakeBpy: String? = null,
    val compatWithOs: Map<String, List<String>>,
) {
    /** Full Blender version in `major.minor.patch` format. */
    val blVersion: String
        get() = blVersionList.joinToString(separator = ".")

    /** Full Python version in `major.minor.patch` format. */
    val pyVersion: String
        get() = pyVersionList.joinToString(separator = ".")

    /** Blender version selector in `major.minor` format. */
    val blMajorMinor: String
        get() = "${blVersionList[0]}.${blVersionList[1]}"

    /** Python version selector in `major.minor` format. */
    val pyMajorMinor: String
        get() = pyVersionList.take(2).joinToString(separator = ".")

    /** Blender patch component as a string. */
    val blFallback: String
        get() = blVersionList[2].toString()

    /** Python patch component as a string. */
    val pyFallback: String
        get() = pyVersionList.getOrNull(2)?.toString().orEmpty()

    /** Fake-BPY package name, including any release-specific package suffix override. */
    val fakeBpyPackage = "fake-bpy-module-${fakeBpy ?: blMajorMinor}"

    private val normalizedPlatform = SystemInfo.normalizeOSName
    private val normalizedArch = SystemInfo.normalizeOsArch

    /** Generates the name of the Blender Bunder artifact sans File Extension */
    val artifactName: String = "blender-$blVersion-$normalizedPlatform-$normalizedArch"

    /**
     * Adds the fileExtension to the artifact name, and catches if the code tries to get an incompatible version.
     *
     * @param fileExtension The file extension of the desired binary. Defaults to a predefined list.
     */
    fun getArchiveName(fileExtension: String = SystemInfo.getSysInfo.bundleFileType): String {
        if (
            compatWithOs[normalizedPlatform]?.contains(normalizedArch) != true ||
                !SystemInfo.isBundleFileTypeSupported(normalizedPlatform, fileExtension)
        ) {
            return ""
        }

        return "$artifactName.$fileExtension"
    }

    /** Returns the download URL of the desired Blender Artifact/ */
    fun getDownloadURL(): String {
        return "${DOWNLOAD_BASE_URL}Blender$blMajorMinor/${getArchiveName()}"
    }

    companion object {
        private const val DOWNLOAD_BASE_URL = "https://download.blender.org/release/"
    }
}

/** In-memory version registry used by wizard defaults and compatibility checks. */
object BlenderVersions {
    private val FALLBACK_VERSION_TABLE =
        listOf(
            BlenderVersion(
                installName = "Blender 4.2.19",
                blVersionList = listOf(4, 2, 19),
                pyVersionList = listOf(3, 11, 7),
                compatWithOs =
                    mapOf(
                        "windows" to listOf("x64"),
                        "macos" to listOf("x64", "arm64"),
                        "linux" to listOf("x64"),
                    ),
            ),
            BlenderVersion(
                installName = "Blender 4.5.8",
                blVersionList = listOf(4, 5, 8),
                pyVersionList = listOf(3, 11, 9),
                compatWithOs =
                    mapOf(
                        "windows" to listOf("x64", "arm64"),
                        "macos" to listOf("x64", "arm64"),
                        "linux" to listOf("x64"),
                    ),
            ),
            BlenderVersion(
                installName = "Blender 5.2.0",
                blVersionList = listOf(5, 2, 0),
                pyVersionList = listOf(3, 13, 13),
                fakeBpy = "latest",
                compatWithOs =
                    mapOf(
                        "windows" to listOf("x64", "arm64"),
                        "macos" to listOf("arm64"),
                        "linux" to listOf("x64"),
                    ),
            ),
        )

    internal fun mergeDiscoveredVersions(discoveredVersions: List<List<Int>>): List<BlenderVersion> {
        val latestPatchByMinor =
            discoveredVersions
                .filter { it.size == 3 }
                .groupBy { normalizeVersionFromList(it) }
                .mapValues { (_, versions) -> versions.maxWith(compareBy({ it[0] }, { it[1] }, { it[2] })) }

        val configuredByMinor = FALLBACK_VERSION_TABLE.associateBy(BlenderVersion::blMajorMinor)
        val mergedVersions =
            (configuredByMinor.keys + latestPatchByMinor.keys).map { minorVersion ->
                val configuredVersion = configuredByMinor[minorVersion]
                val discoveredVersion = latestPatchByMinor[minorVersion]
                when {
                    configuredVersion == null ->
                        BlenderVersion(
                            installName = "Blender ${checkNotNull(discoveredVersion).joinToString(".")}",
                            blVersionList = discoveredVersion,
                            compatWithOs = emptyMap(),
                        )
                    discoveredVersion == null -> configuredVersion
                    else ->
                        configuredVersion.copy(
                            installName = "Blender ${discoveredVersion.joinToString(".")}",
                            blVersionList = discoveredVersion,
                        )
                }
            }
        return mergedVersions.sortedWith(compareBy({ it.blVersionList[0] }, { it.blVersionList[1] }, { it.blVersionList[2] }))
    }

    /**
     * Returns the loaded Blender/Python compatibility table.
     *
     * @return list of known Blender/Python compatibility rows.
     */
    fun getVersionTable(): List<BlenderVersion> {
        val application = ApplicationManager.getApplication() ?: return FALLBACK_VERSION_TABLE
        return application.getService(BlenderVersionCache::class.java)?.getVersionTable() ?: FALLBACK_VERSION_TABLE
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

    fun getVersionMeta(blMajorMinor: String): BlenderVersion? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }
    }

    /**
     * Returns full Python version that corresponds to the provided Blender major/minor value.
     *
     * @param blMajorMinor Blender version selector in `major.minor` form (also accepts full versions).
     * @return full Python version string, or `null` when no mapping exists.
     */
    fun getPythonVersion(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.pyVersion?.takeIf(String::isNotBlank)
    }

    /**
     * Returns the configured Fake-BPY package name for a Blender version.
     *
     * @param blMajorMinor Blender version selector in `major.minor` form (also accepts full versions).
     * @return package name using the version row's override when present, or `null` when not found.
     */
    fun getFakeBpyPackageName(blMajorMinor: String): String? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized && it.pyVersion.isNotBlank() }?.fakeBpyPackage
    }

    /**
     * Returns OS/architecture compatibility matrix for the selected Blender version.
     *
     * @param blMajorMinor Blender version selector in `major.minor` form (also accepts full versions).
     * @return compatibility map keyed by `win`, `mac`, or `linux`, or `null` when not found.
     */
    fun getCompatibleArch(blMajorMinor: String): Map<String, List<String>>? {
        val normalized = normalizeVersion(blMajorMinor)
        return getVersionTable().find { it.blMajorMinor == normalized }?.compatWithOs?.takeIf(Map<String, List<String>>::isNotEmpty)
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
