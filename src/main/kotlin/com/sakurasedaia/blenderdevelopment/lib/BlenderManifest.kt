package com.sakurasedaia.blenderdevelopment.lib

import dev.eav.tomlkt.Toml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Paths
import kotlin.io.path.readText

/** Permission descriptions declared by a Blender extension manifest. */
@Serializable
data class Permissions(
  val network: String? = null,
  val files: String? = null,
  val clipboard: String? = null,
  val camera: String? = null,
  val microphone: String? = null
)

/** Build configuration declared by a Blender extension manifest. */
@Serializable
data class Build(
  val paths_exclude_pattern: List<String> = emptyList()
)

/**
 * Read and parse Blender Manifest keys using the standard Blender Manifest, currently only supporting all official keys in the Blender Manifest schema.
 *
 * @param filePath Path to the Blender Manifest to edit.
 * */
@Serializable
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
  val build: Build? = null
) {
  companion object {
    private val toml = Toml {
      ignoreUnknownKeys = true
    }

    /** Reads and parses the Blender manifest at [filePath]. */
    operator fun invoke(filePath: String): BlenderManifest =
      toml.decodeFromString(string = Paths.get(filePath).readText())
  }
}
