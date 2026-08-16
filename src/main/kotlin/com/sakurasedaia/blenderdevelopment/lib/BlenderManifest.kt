package com.sakurasedaia.blenderdevelopment.lib

import dev.eav.tomlkt.Toml
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** Permission descriptions declared by a Blender extension manifest. */
@Serializable
data class Permissions(
    val network: String? = null,
    val files: String? = null,
    val clipboard: String? = null,
    val camera: String? = null,
    val microphone: String? = null,
)

/** Build configuration declared by a Blender extension manifest. */
@Suppress("PropertyName") // Preserve Blender's TOML manifest field names.
@Serializable
data class Build(val paths_exclude_pattern: List<String> = emptyList())

/**
 * Read and parse Blender Manifest keys using the standard Blender Manifest, currently only supporting all official keys in the Blender
 * Manifest schema.
 */
@Serializable
@Suppress("PropertyName") // Preserve Blender's TOML manifest field names.
data class BlenderManifest(
    // Required base variables
    val schema_version: String,
    val id: String,
    val version: String,
    val name: String,
    val tagline: String,
    val maintainer: String,
    val type: String,
    val blender_version_min: String,
    val license: List<String>,

    // Optional variables (commented out in the template)
    val website: String? = null,
    val tags: List<String> = emptyList(),
    val blender_version_max: String? = null,
    val copyright: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val wheels: List<String> = emptyList(),

    // Nested Tables
    val permissions: Permissions? = null,
    val build: Build? = null,
) {
  companion object {
    private val toml = Toml {
      ignoreUnknownKeys = true
    }

    /**
     * Parses a Blender Manifest file from the specified file path into a BlenderManifest object.
     *
     * @param filePath the path of the Blender Manifest file to be read and parsed.
     * @return a BlenderManifest object representing the data from the provided file.
     */
    operator fun invoke(filePath: String): BlenderManifest = toml.decodeFromString(string = Paths.get(filePath).readText())
  }
}
