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

package com.sakurasedaia.blenderdevelopment.wizard

import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathProvider
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import com.sakurasedaia.blenderdevelopment.util.PythonModuleNameValidator

/** Renders Blender-specific controls around PyCharm's native environment selector. */
@Suppress("UnstableApiUsage")
object BlenderProjectUI : PyV3ProjectTypeSpecificUI<BlenderProjectSettings> {
  /**
   * Adds frequently used Blender settings above the Python environment selector.
   *
   * @param settings Blender form state.
   * @param checkBoxRow PyCharm's row containing common project checkboxes.
   * @param belowCheckBoxes panel immediately above the environment selector.
   */
  override fun configureUpperPanel(settings: BlenderProjectSettings, checkBoxRow: Row, belowCheckBoxes: Panel) {
    checkBoxRow.checkBox(MessageBundle.message("ui.project.wizard.ui.group.project.add_example_code"))
      .bindSelected(settings.addExampleCodeProperty)

    with(belowCheckBoxes) {
      row(MessageBundle.message("ui.project.wizard.section.project.basics")) {}
      separator()

      row(MessageBundle.message("ui.project.wizard.row.label.extension.type")) {
        segmentedButton(
          listOf(
            BlenderProjectGenerator.PROJECT_TYPE_EXTENSION,
            BlenderProjectGenerator.PROJECT_TYPE_ADD_ON,
          ),
        ) {
          text = when (it) {
            BlenderProjectGenerator.PROJECT_TYPE_EXTENSION ->
              MessageBundle.message("ui.project.wizard.option.extension.type.extension")
            BlenderProjectGenerator.PROJECT_TYPE_ADD_ON ->
              MessageBundle.message("ui.project.wizard.option.extension.type.addon")
            else -> it
          }
        }.bind(settings.manifestExtensionTypeProperty)
      }.bottomGap(BottomGap.SMALL)

      row(MessageBundle.message("ui.project.wizard.ui.group.project.author")) {
        textField().bindText(settings.authorNameProperty)
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.project.version")) {
        textField().bindText(settings.extensionVersionProperty)
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.project.blender.version")) {
        comboBox(BlenderVersions.LIST.map { it.blMajorMinor })
          .bindItem(settings.blenderVersionProperty)
      }
      row("") {
        comment("").bindText(settings.recommendedPythonVersionCommentProperty)
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.project.license")) {
        textField().bindText(settings.manifestLicenseProperty).enabled(false)
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.project.website.docs")) {
        textField().bindText(settings.manifestWebsiteLinkProperty)
          .validationOnInput { validateWebsite(it.text) }
          .comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.description"))
      }.visibleIf(settings.manifestExtensionTypeProperty.equalsTo(BlenderProjectGenerator.PROJECT_TYPE_ADD_ON))
      row(MessageBundle.message("ui.project.wizard.ui.group.project.description")) {
        textArea().bindText(settings.descriptionProperty)
      }

      row(MessageBundle.message("ui.project.wizard.section.project.interpreter")) {}
        .topGap(topGap = TopGap.MEDIUM)
      separator()
    }
  }

  /** Detailed manifest and permission fields shown under PyCharm's Advanced Settings section. */
  override val advancedSettings: Panel.(BlenderProjectSettings, ProjectPathProvider) -> Unit = { settings, projectPath ->
    projectPath.onProjectFileNameChanged(settings::updateProjectName)

    rowsRange {
      row(MessageBundle.message("ui.project.wizard.ui.group.manifest.addon_id")) {
        textField().bindText(settings.manifestIdProperty)
          .applyToComponent {
            whenTextChangedFromUi { settings.markManifestIdCustomized() }
          }
          .validationOnInput {
            if (PythonModuleNameValidator.isValid(it.text.trim())) null
            else error(MessageBundle.message("ui.common.python.module.name.validation"))
          }
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.manifest.min.blender")) {
        comboBox(BlenderVersions.LIST.map { it.blMajorMinor })
          .bindItem(settings.manifestMinBlenderVersionProperty)
          .validationOnInput {
            validateVersionRange(settings.manifestMinBlenderVersion, settings.maximumBlenderVersion, true)
          }
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.manifest.max.blender")) {
        val noneLabel = MessageBundle.message("ui.project.wizard.option.none")
        comboBox(
          listOf(noneLabel) + BlenderVersions.LIST.map { it.blMajorMinor },
        )
          .bindItem(settings.manifestMaxBlenderVersionProperty)
          .validationOnInput {
            validateVersionRange(settings.manifestMinBlenderVersion, settings.maximumBlenderVersion, false)
          }
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.manifest.tags.label")) {
        textField().bindText(settings.manifestTagsProperty)
          .align(AlignX.FILL)
          .comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.tags.description"))
      }
      row(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.label")) {
        textField().bindText(settings.manifestWebsiteLinkProperty)
          .validationOnInput { validateWebsite(it.text) }
          .comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.description"))
      }
      group(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.label")) {
        row {
          comment(MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.description"))
        }
        permissionRow(
          MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.files"),
          settings.manifestFilesPermissionProperty,
        )
        permissionRow(
          MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.network"),
          settings.manifestNetworkPermissionProperty,
        )
        permissionRow(
          MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.clipboard"),
          settings.manifestClipboardPermissionProperty,
        )
        permissionRow(
          MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.camera"),
          settings.manifestCameraPermissionProperty,
        )
        permissionRow(
          MessageBundle.message("ui.project.wizard.ui.group.manifest.permissions.microphone"),
          settings.manifestMicrophonePermissionProperty,
        )
      }
    }.visibleIf(settings.manifestExtensionTypeProperty.equalsTo(BlenderProjectGenerator.PROJECT_TYPE_EXTENSION))
  }

  /**
   * Adds a full-width permission-reason field.
   *
   * @param label localized permission label.
   * @param property settings property bound to the field.
   */
  private fun Panel.permissionRow(
    label: String,
    property: com.intellij.openapi.observable.properties.GraphProperty<String>,
  ) {
    row(label) {
      textField().bindText(property).align(AlignX.FILL)
    }
  }

  /**
   * Validates an optional HTTPS website value.
   *
   * @param value current field value.
   * @return validation error for a non-HTTPS value, otherwise `null`.
   */
  private fun ValidationInfoBuilder.validateWebsite(value: String): ValidationInfo? =
    if (value.isNotBlank() && !value.startsWith("https://")) {
      error(MessageBundle.message("ui.project.wizard.ui.group.manifest.website.error.improper_format"))
    } else {
      null
    }

  /**
   * Validates the selected minimum and maximum Blender versions.
   *
   * @param minimum selected minimum Blender version.
   * @param maximum optional selected maximum Blender version.
   * @param validatingMinimum whether the minimum-version field requested validation.
   * @return localized validation error when the range is reversed, otherwise `null`.
   */
  private fun ValidationInfoBuilder.validateVersionRange(
    minimum: String,
    maximum: String?,
    validatingMinimum: Boolean,
  ): ValidationInfo? = if (maximum != null && VersionComparatorUtil.compare(minimum, maximum) > 0) {
    val key = if (validatingMinimum) {
      "ui.project.wizard.ui.group.manifest.min.blender.error"
    } else {
      "ui.project.wizard.ui.group.manifest.max.blender.error"
    }
    error(MessageBundle.message(key))
  } else {
    null
  }
}
