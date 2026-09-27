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

package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class BlendFileToOpenValidatorTest : BasePlatformTestCase() {
    fun testBlankPathIsValid() {
        assertEquals(BlendFileValidation.NONE, BlendFileToOpenValidator.validate("  "))
    }

    fun testMalformedPathIsInvalid() {
        assertEquals(BlendFileValidation.INVALID_PATH, BlendFileToOpenValidator.validate("\u0000"))
    }

    fun testWrongExtensionIsRejectedBeforeExistenceCheck() {
        val path = Path.of(project.basePath!!).resolve("scene.txt")

        assertEquals(BlendFileValidation.WRONG_EXTENSION, BlendFileToOpenValidator.validate(path.toString()))
    }

    fun testMissingBlendFileIsReported() {
        val path = Path.of(project.basePath!!).resolve("missing.blend")

        assertEquals(BlendFileValidation.MISSING, BlendFileToOpenValidator.validate(path.toString()))
    }

    fun testDirectoryWithBlendExtensionIsNotAFile() {
        val path = Path.of(project.basePath!!).resolve("directory.blend").createDirectories()

        assertEquals(BlendFileValidation.NOT_FILE, BlendFileToOpenValidator.validate(path.toString()))
    }

    fun testExistingUppercaseBlendFileIsValid() {
        val path = Path.of(project.basePath!!).resolve("scene.BLEND").createFile()

        assertEquals(BlendFileValidation.NONE, BlendFileToOpenValidator.validate(path.toString()))
    }
}
