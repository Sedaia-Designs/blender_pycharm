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

package com.sakurasedaia.blenderdevelopment.blender.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class BlenderScriptTemplatesTest {
    @Test
    fun testEntryScriptRendersConfiguredPaths() {
        val entryScript = BlenderScriptTemplates.renderEntryScript(
            configPath = Path.of("/tmp/blender config.json"),
            bootstrapPath = Path.of("/tmp/blender_pycharm_bootstrap.py"),
        )

        assertTrue(entryScript.contains("BLENDER_PYCHARM_CONFIG"))
        assertTrue(entryScript.contains("/tmp/blender config.json"))
        assertTrue(entryScript.contains("/tmp/blender_pycharm_bootstrap.py"))
        assertFalse(entryScript.contains("__CONFIG_PATH_LITERAL__"))
        assertFalse(entryScript.contains("__BOOTSTRAP_PATH_LITERAL__"))
    }

    @Test
    fun testBootstrapScriptIsLoadedFromResources() {
        val bootstrapScript = BlenderScriptTemplates.loadBootstrapScript()

        assertTrue(bootstrapScript.contains("def ensure_extension_repo_exists(config):"))
        assertTrue(bootstrapScript.contains("BLENDER_PYCHARM_RELOAD_PORT"))
        assertTrue(bootstrapScript.contains("def listen_for_reload(config):"))
    }
}
