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

/** Describes one option accepted by a built-in Blender command. */
internal data class BlenderCommandOption(
  val longName: String,
  val shortName: String? = null,
  val valueKind: BlenderCommandOptionValueKind = BlenderCommandOptionValueKind.NONE,
  val required: Boolean = false,
) {
  /** Returns every spelling accepted for this option. */
  val names: Set<String>
    get() = setOfNotNull(shortName, longName)

  /** Returns whether the option must be followed by a value. */
  val requiresValue: Boolean
    get() = valueKind != BlenderCommandOptionValueKind.NONE
}

/** Identifies the kind of value consumed by a Blender command option. */
internal enum class BlenderCommandOptionValueKind {
  NONE,
  TEXT,
  PATH,
  BOOLEAN,
}

/** Describes one built-in `extension` subcommand. */
internal data class BlenderExtensionSubcommand(
  val id: String,
  val options: List<BlenderCommandOption> = emptyList(),
  val minimumPositionals: Int = 0,
  val positionalName: String? = null,
)

/** Contains the built-in Blender command metadata used for completion and validation. */
internal object BlenderCommandCatalog {
  val commandIds: List<String> = listOf("extension", "maketx", "keyconfig_export", "sysinfo")

  val extensionSubcommands: List<BlenderExtensionSubcommand> = listOf(
    BlenderExtensionSubcommand("list", options = listOf(syncOption())),
    BlenderExtensionSubcommand("sync"),
    BlenderExtensionSubcommand("update", options = listOf(syncOption())),
    BlenderExtensionSubcommand(
      id = "install",
      options = listOf(syncOption(), enableOption(), noPreferencesOption()),
      minimumPositionals = 1,
      positionalName = "PACKAGES",
    ),
    BlenderExtensionSubcommand(
      id = "install-file",
      options = listOf(
        BlenderCommandOption("--repo", "-r", BlenderCommandOptionValueKind.TEXT, required = true),
        enableOption(),
        noPreferencesOption(),
      ),
      minimumPositionals = 1,
      positionalName = "FILE",
    ),
    BlenderExtensionSubcommand(
      id = "remove",
      options = listOf(noPreferencesOption()),
      minimumPositionals = 1,
      positionalName = "PACKAGES",
    ),
    BlenderExtensionSubcommand("repo-list"),
    BlenderExtensionSubcommand(
      id = "repo-add",
      options = listOf(
        BlenderCommandOption("--name", valueKind = BlenderCommandOptionValueKind.TEXT),
        BlenderCommandOption("--directory", valueKind = BlenderCommandOptionValueKind.PATH),
        BlenderCommandOption("--url", valueKind = BlenderCommandOptionValueKind.TEXT),
        BlenderCommandOption("--access-token", valueKind = BlenderCommandOptionValueKind.TEXT),
        BlenderCommandOption("--source", valueKind = BlenderCommandOptionValueKind.TEXT),
        BlenderCommandOption("--cache", valueKind = BlenderCommandOptionValueKind.BOOLEAN),
        BlenderCommandOption("--clear-all"),
        noPreferencesOption(),
      ),
      minimumPositionals = 1,
      positionalName = "ID",
    ),
    BlenderExtensionSubcommand(
      id = "repo-remove",
      options = listOf(noPreferencesOption()),
      minimumPositionals = 1,
      positionalName = "ID",
    ),
    BlenderExtensionSubcommand(
      id = "build",
      options = listOf(
        BlenderCommandOption("--source-dir", valueKind = BlenderCommandOptionValueKind.PATH),
        BlenderCommandOption("--output-dir", valueKind = BlenderCommandOptionValueKind.PATH),
        BlenderCommandOption("--output-filepath", valueKind = BlenderCommandOptionValueKind.PATH),
        BlenderCommandOption("--valid-tags", valueKind = BlenderCommandOptionValueKind.PATH),
        BlenderCommandOption("--split-platforms"),
        BlenderCommandOption("--verbose"),
      ),
    ),
    BlenderExtensionSubcommand(
      id = "validate",
      options = listOf(BlenderCommandOption("--valid-tags", valueKind = BlenderCommandOptionValueKind.PATH)),
      positionalName = "SOURCE_PATH",
    ),
    BlenderExtensionSubcommand(
      id = "server-generate",
      options = listOf(
        BlenderCommandOption("--repo-dir", valueKind = BlenderCommandOptionValueKind.PATH, required = true),
        BlenderCommandOption("--repo-config", valueKind = BlenderCommandOptionValueKind.PATH),
        BlenderCommandOption("--html"),
        BlenderCommandOption("--html-template", valueKind = BlenderCommandOptionValueKind.PATH),
      ),
    ),
  )

  /** Finds a documented `extension` subcommand by its command-line identifier. */
  fun findExtensionSubcommand(id: String): BlenderExtensionSubcommand? {
    return extensionSubcommands.find { it.id == id }
  }

  private fun syncOption() = BlenderCommandOption("--sync", "-s")

  private fun enableOption() = BlenderCommandOption("--enable", "-e")

  private fun noPreferencesOption() = BlenderCommandOption("--no-prefs")
}

/** Reports a validation problem in a known Blender command invocation. */
internal sealed interface BlenderCommandValidationIssue {
  data object MissingCommand : BlenderCommandValidationIssue

  data object DuplicateCommandOption : BlenderCommandValidationIssue

  data class MissingOptionValue(val optionName: String) : BlenderCommandValidationIssue

  data class MissingRequiredOption(val optionName: String) : BlenderCommandValidationIssue

  data class MissingPositional(val positionalName: String) : BlenderCommandValidationIssue
}

/** Validates documented Blender commands while accepting installation-defined commands and options. */
internal object BlenderCommandValidator {
  /** Returns the first problem found in [arguments], or `null` when the invocation can be launched. */
  fun validate(arguments: List<String>): BlenderCommandValidationIssue? {
    if (arguments.isEmpty() || arguments.first().isBlank()) {
      return BlenderCommandValidationIssue.MissingCommand
    }
    if (arguments.first() == "--command" || arguments.first() == "-c") {
      return BlenderCommandValidationIssue.DuplicateCommandOption
    }
    if (arguments.first() != "extension" || arguments.size < 2) {
      return null
    }

    val subcommand = BlenderCommandCatalog.findExtensionSubcommand(arguments[1]) ?: return null
    return validateSubcommand(subcommand, arguments.drop(2))
  }

  private fun validateSubcommand(
    subcommand: BlenderExtensionSubcommand,
    arguments: List<String>,
  ): BlenderCommandValidationIssue? {
    val foundOptions = mutableSetOf<BlenderCommandOption>()
    var positionalCount = 0
    var index = 0
    while (index < arguments.size) {
      val argument = arguments[index]
      val optionName = argument.substringBefore('=')
      val option = subcommand.options.find { optionName in it.names }
      if (option == null) {
        if (!argument.startsWith("-")) {
          positionalCount++
        }
        index++
        continue
      }

      foundOptions.add(option)
      if (option.requiresValue && '=' !in argument) {
        val value = arguments.getOrNull(index + 1)
        if (value == null || value.startsWith("-")) {
          return BlenderCommandValidationIssue.MissingOptionValue(option.longName)
        }
        index++
      }
      index++
    }

    subcommand.options.firstOrNull { it.required && it !in foundOptions }?.let {
      return BlenderCommandValidationIssue.MissingRequiredOption(it.longName)
    }
    if (positionalCount < subcommand.minimumPositionals) {
      return BlenderCommandValidationIssue.MissingPositional(requireNotNull(subcommand.positionalName))
    }
    return null
  }
}
