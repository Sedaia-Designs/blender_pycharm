package com.sakurasedaia.blenderdevelopment.util

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties

object PluginResources {
  /**
   * Renders a file template and writes it to the target directory.
   *
   * @param project active project context.
   * @param name output file name.
   * @param template optional template name override.
   * @param destination destination directory for generated output.
   * @param internal whether the template comes from internal templates.
   * @param args key/value template variables.
   * @return rendered template content.
   */
  fun createFromTemplate(
      project: Project,
      name: String,
      template: String? = null,
      destination: VirtualFile,
      internal: Boolean = false,
      vararg args: Pair<String, String?>,
  ): String {
    val templateManager = FileTemplateManager.getInstance(project)
    val templateFileName = template ?: name

    val fileTemplate =
        when (internal) {
          true -> templateManager.getInternalTemplate(templateFileName)
          else -> templateManager.getTemplate(templateFileName)
        } ?: throw IllegalStateException(MessageBundle.message("ui.project.wizard.error.project.template.file.missing", templateFileName))
    if (fileTemplate.text.isEmpty())
        throw IllegalStateException(MessageBundle.message("ui.project.wizard.error.project.template.template.missing", templateFileName))

    val templateProps = Properties(templateManager.defaultProperties)

    args.forEach { (arg, value) ->
      if (value != null) {
        templateProps.setProperty(arg, value)
      }
    }

    val result = fileTemplate.getText(templateProps)

    val file = destination.findChild(name) ?: destination.createChildData(this, name)
    file.setBinaryContent(result.toByteArray())
    return result
  }

  /**
   * Renders a file from the plugin's resources into the target directory.
   *
   * @param project active project context
   * @param name Output File Name
   * @param resourceFile Resource File Name
   * @param destination destination directory for generated output.
   * @param args key/value template variables.
   * @return rendered resource content.
   */
  fun importResource(
      project: Project,
      name: String,
      resourceFile: String,
      destination: String,
      vararg args: Any,
  ) {
    val basePath =
        project.basePath ?: throw IllegalStateException(MessageBundle.message("ui.project.wizard.error.project.base.path.missing"))

    val outputPath = resolveOutputPath(basePath = basePath, destination = destination, name = name)
    Files.createDirectories(outputPath.parent)

    val resourceText =
        PluginResources::class.java.classLoader.getResourceAsStream(resourceFile)?.bufferedReader(StandardCharsets.UTF_8)?.use {
          it.readText()
        } ?: throw IllegalStateException(MessageBundle.message("ui.project.wizard.error.project.resource.file.missing", resourceFile))

    val contentToAppend = if (args.isEmpty()) resourceText else resourceText.format(*args)

    Files.writeString(
        outputPath,
        contentToAppend,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )

    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outputPath)?.refresh(false, false)
  }

  private fun resolveOutputPath(basePath: String, destination: String, name: String): Path {
    val destinationPath = Path.of(destination)
    return if (destinationPath.isAbsolute) destinationPath.resolve(name) else Path.of(basePath).resolve(destinationPath).resolve(name)
  }
}
