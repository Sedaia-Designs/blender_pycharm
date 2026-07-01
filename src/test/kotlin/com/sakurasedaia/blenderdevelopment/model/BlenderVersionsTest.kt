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

import com.sakurasedaia.blenderdevelopment.lib.SystemHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for host/platform compatibility checks in [com.sakurasedaia.blenderdevelopment.lib.SystemHelper]. */
class BlenderVersionsTest {
    @Test
    /**
     * Verifies known supported major/minor versions report compatibility.
     *
     * @return `Unit`.
     */
    fun testIsOSCompatibleSpecific() {
        assertTrue("Should be compatible with 4.5", SystemHelper.isOSCompatible("4.5"))
        assertTrue("Should be compatible with 5.1", SystemHelper.isOSCompatible("5.1"))
    }
    
    @Test
    /**
     * Verifies unknown versions are rejected by compatibility checks.
     *
     * @return `Unit`.
     */
    fun testIsOSCompatibleUnknownVersion() {
        assertFalse("Should not be compatible with unknown version", SystemHelper.isOSCompatible("9.9"))
    }

    @Test
    /**
     * Verifies full version strings normalize correctly for compatibility checks.
     *
     * @return `Unit`.
     */
    fun testIsOSCompatibleFullVersion() {
        assertTrue("Should be compatible with full version string 4.2.19", SystemHelper.isOSCompatible("4.2.19"))
    }
}
