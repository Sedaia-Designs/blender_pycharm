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

package com.sakurasedaia.blenderdevelopment.run

import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.command.line.CompletionTableInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.util.ModificationTracker
import com.sakurasedaia.blenderdevelopment.core.BlenderCommandCatalog
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import javax.swing.Icon

/** Supplies arguments accepted by the selected Blender command to the command-line editor. */
internal class BlenderCommandLineInfo(
    selectedCommand: () -> String,
    modificationTracker: ModificationTracker,
) : CommandLineInfo {
  override val settingsId: String = "blender.command.line.fragment"
  override val settingsName: String = MessageBundle.message("run.configuration.blender.command.line.name")
  override val settingsHint: String = MessageBundle.message("run.configuration.blender.command.line.hint")
  override val dialogTitle: String = MessageBundle.message("run.configuration.blender.command.line.dialog.title")
  override val dialogTooltip: String = MessageBundle.message("run.configuration.blender.command.line.dialog.tooltip")
  override val fieldEmptyState: String = MessageBundle.message("run.configuration.blender.command.line.empty")
  override val tablesInfo: List<CompletionTableInfo> =
      listOf(
          ExtensionSubcommandsCompletionTable(selectedCommand, modificationTracker),
          OptionsCompletionTable(selectedCommand, modificationTracker),
      )

  private class ExtensionSubcommandsCompletionTable(
      selectedCommand: () -> String,
      modificationTracker: ModificationTracker,
  ) :
      BlenderCompletionTable(
          emptyStateKey = "run.configuration.blender.command.completion.subcommands.empty",
          columnNameKey = "run.configuration.blender.command.completion.subcommand.column",
          selectedCommandProvider = selectedCommand,
          modificationTracker = modificationTracker,
      ) {
    override fun entries(): List<TextCompletionInfo> {
      if (selectedCommand() != "extension") return emptyList()
      return BlenderCommandCatalog.extensionSubcommands.map { subcommand ->
        TextCompletionInfo(subcommand.id, subcommandDescription(subcommand.id))
      }
    }
  }

  private class OptionsCompletionTable(
      selectedCommand: () -> String,
      modificationTracker: ModificationTracker,
  ) :
      BlenderCompletionTable(
          emptyStateKey = "run.configuration.blender.command.completion.options.empty",
          columnNameKey = "run.configuration.blender.command.completion.option.column",
          selectedCommandProvider = selectedCommand,
          modificationTracker = modificationTracker,
      ) {
    override fun entries(): List<TextCompletionInfo> {
      if (selectedCommand() != "extension") return emptyList()
      return BlenderCommandCatalog.extensionSubcommands
          .flatMap { it.options }
          .flatMap { option -> option.names }
          .distinct()
          .sorted()
          .map { option -> TextCompletionInfo(option, optionDescription(option)) }
    }
  }
}

private abstract class BlenderCompletionTable(
    emptyStateKey: String,
    columnNameKey: String,
    private val selectedCommandProvider: () -> String,
    modificationTracker: ModificationTracker,
) : CompletionTableInfo {
  override val emptyState: String = MessageBundle.message(emptyStateKey)
  override val dataColumnIcon: Icon? = null
  override val dataColumnName: String = MessageBundle.message(columnNameKey)
  override val descriptionColumnIcon: Icon? = null
  override val descriptionColumnName: String = MessageBundle.message("run.configuration.blender.command.completion.description.column")
  override val completionModificationTracker: ModificationTracker = modificationTracker

  final override suspend fun collectCompletionInfo(): List<TextCompletionInfo> = entries()

  final override suspend fun collectTableCompletionInfo(): List<TextCompletionInfo> = entries()

  protected abstract fun entries(): List<TextCompletionInfo>

  protected fun selectedCommand(): String = selectedCommandProvider().trim()
}

private fun subcommandDescription(subcommand: String): String {
  return MessageBundle.message("run.configuration.blender.command.completion.subcommand.$subcommand")
}

private fun optionDescription(option: String): String {
  return MessageBundle.message(
      "run.configuration.blender.command.completion.option",
      option,
  )
}
