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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.state

import com.intellij.configurationStore.Property
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions

/** Persistent application-level cache of Blender versions discovered online. */
@Service(Service.Level.APP)
@State(name = "BlenderVersionCache", storages = [Storage("blender-version-cache.xml")])
internal class BlenderVersionCache :
  SerializablePersistentStateComponent<BlenderVersionCacheState>(BlenderVersionCacheState()) {

  internal fun getVersionTable(): List<BlenderVersion> =
    BlenderVersions.mergeDiscoveredVersions(state.versions.mapNotNull(::parseVersion))

  internal fun hasCachedVersions(): Boolean = state.versions.any { parseVersion(it) != null }

  internal fun cacheDiscoveredVersions(discoveredVersions: List<List<Int>>) {
    val versions = discoveredVersions
      .filter { it.size == 3 }
      .groupBy { BlenderVersions.normalizeVersionFromList(it) }
      .map { (_, patches) -> patches.maxWith(compareBy({ it[0] }, { it[1] }, { it[2] })) }
      .map { it.joinToString(".") }

    updateState { it.copy(versions = versions) }
  }

  internal fun clear() {
    updateState { BlenderVersionCacheState() }
  }

  private fun parseVersion(version: String): List<Int>? {
    val parts = version.split('.')
    if (parts.size != 3) return null
    return parts.map { it.toIntOrNull() ?: return null }
  }

  companion object {
    fun getInstance(): BlenderVersionCache = service()
  }
}

internal data class BlenderVersionCacheState(
  @JvmField @Property val versions: List<String> = emptyList(),
)
