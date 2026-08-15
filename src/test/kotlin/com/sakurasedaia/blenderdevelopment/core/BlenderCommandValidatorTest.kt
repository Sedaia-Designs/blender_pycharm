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

package com.sakurasedaia.blenderdevelopment.core

import junit.framework.TestCase

class BlenderCommandValidatorTest : TestCase() {
  fun testCatalogContainsEveryDocumentedExtensionSubcommand() {
    assertEquals(
        listOf(
            "list",
            "sync",
            "update",
            "install",
            "install-file",
            "remove",
            "repo-list",
            "repo-add",
            "repo-remove",
            "build",
            "validate",
            "server-generate",
        ),
        BlenderCommandCatalog.extensionSubcommands.map { it.id },
    )
  }

  fun testUnknownRegisteredCommandIsAccepted() {
    assertNull(BlenderCommandValidator.validate(listOf("custom_command", "--custom-option")))
  }

  fun testBuildAcceptsDocumentedPathOptions() {
    assertNull(BlenderCommandValidator.validate(listOf("extension", "build", "--source-dir", "Extension/src", "--output-dir=/tmp")))
  }

  fun testKnownOptionRequiresItsValue() {
    assertEquals(
        BlenderCommandValidationIssue.MissingOptionValue("--source-dir"),
        BlenderCommandValidator.validate(listOf("extension", "build", "--source-dir")),
    )
  }

  fun testInstallRequiresPackages() {
    assertEquals(
        BlenderCommandValidationIssue.MissingPositional("PACKAGES"),
        BlenderCommandValidator.validate(listOf("extension", "install", "--enable")),
    )
  }

  fun testInstallFileRequiresRepositoryOption() {
    assertEquals(
        BlenderCommandValidationIssue.MissingRequiredOption("--repo"),
        BlenderCommandValidator.validate(listOf("extension", "install-file", "package.zip")),
    )
  }

  fun testInstallFileAcceptsShortRepositoryOption() {
    assertNull(BlenderCommandValidator.validate(listOf("extension", "install-file", "-r", "local", "package.zip")))
  }

  fun testServerGenerateRequiresRepositoryDirectory() {
    assertEquals(
        BlenderCommandValidationIssue.MissingRequiredOption("--repo-dir"),
        BlenderCommandValidator.validate(listOf("extension", "server-generate", "--html")),
    )
  }
}
