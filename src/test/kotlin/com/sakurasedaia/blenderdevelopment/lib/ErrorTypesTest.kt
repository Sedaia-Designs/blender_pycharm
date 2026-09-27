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

import java.security.InvalidKeyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTypesTest {
    @Test
    fun `format substitutes indexed parameters`() {
        assertEquals(
            "[BL-302]: Version probe failed for `/Applications/Blender.app`",
            ErrorTypes.INSTALL_VERSION_PROBE_FAILED.format("/Applications/Blender.app"),
        )
    }

    @Test
    fun `format substitutes multiple indexed parameters`() {
        assertEquals(
            "[BL-209]: Blender runtime command `reload` was rejected by session `session-1` with HTTP 503: unavailable",
            ErrorTypes.RUNTIME_COMMAND_REJECTED.format("reload", "session-1", 503, "unavailable"),
        )
    }

    @Test
    fun `format preserves literal apostrophes`() {
        assertEquals(
            ErrorTypes.UNSUPPORTED_OS.toString(),
            ErrorTypes.UNSUPPORTED_OS.format("unused"),
        )
    }

    @Test
    fun `format renders null parameters explicitly`() {
        assertEquals(
            "[BL-501]: Unexpected Blender stub operation failure: null",
            ErrorTypes.STUB_OPERATION_FAILED.format(null),
        )
    }

    @Test
    fun `format preserves an unresolved placeholder when its parameter is missing`() {
        assertEquals(
            ErrorTypes.INSTALL_VERSION_PROBE_FAILED.toString(),
            ErrorTypes.INSTALL_VERSION_PROBE_FAILED.format(),
        )
    }

    @Test
    fun `toString returns the unformatted message template`() {
        assertEquals(
            "[BL-302]: Version probe failed for `{0}`",
            ErrorTypes.INSTALL_VERSION_PROBE_FAILED.toString(),
        )
    }

    @Test
    fun `createException uses the formatted error message`() {
        val exception = ErrorTypes.KEY_SIZE_MISMATCH.createException(32, 16)

        assertTrue(exception is InvalidKeyException)
        assertEquals(ErrorTypes.KEY_SIZE_MISMATCH.format(32, 16), exception.message)
    }

    @Test
    fun `createException returns a fresh exception for each call`() {
        val first = ErrorTypes.KEY_SIZE_MISMATCH.createException(32, 16)
        val second = ErrorTypes.KEY_SIZE_MISMATCH.createException(32, 16)

        assertTrue(first !== second)
    }

    @Test
    fun `createExceptionOrNull returns null for non-exceptional outcomes`() {
        assertNull(ErrorTypes.INSTALL_SCAN_NO_INSTALLS.createExceptionOrNull("macOS"))
    }
}
