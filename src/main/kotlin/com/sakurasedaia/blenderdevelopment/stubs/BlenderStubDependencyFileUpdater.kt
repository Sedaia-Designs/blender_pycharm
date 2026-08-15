/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sakurasedaia.blenderdevelopment.stubs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

internal object BlenderStubDependencyFileUpdater {
  private val dependencyGroupHeaderPattern = Regex("(?m)^\\[dependency-groups][ \\t]*$")
  private val tableHeaderPattern = Regex("(?m)^\\[[^\\r\\n]+][ \\t]*$")
  private val devGroupPattern = Regex("(?ms)^dev[ \\t]*=[ \\t]*\\[(.*?)](?=[ \\t]*(?:\\r?$|\\z))")
  private val quotedRequirementPattern = Regex("[\"']([^\"']+)[\"']")

  fun update(project: Project, targetRequirement: String) {
    val projectDirectory = project.basePath?.let(LocalFileSystem.getInstance()::findFileByPath) ?: return
    val pyprojectFile = projectDirectory.findChild("pyproject.toml") ?: return
    VfsUtil.saveText(pyprojectFile, updateContent(VfsUtil.loadText(pyprojectFile), targetRequirement))
  }

  fun updateContent(content: String, targetRequirement: String): String {
    val dependencyGroupHeader = dependencyGroupHeaderPattern.find(content)
    if (dependencyGroupHeader != null) {
      val sectionStart = dependencyGroupHeader.range.last + 1
      val sectionEnd = tableHeaderPattern.find(content, sectionStart)?.range?.first ?: content.length
      val section = content.substring(sectionStart, sectionEnd)
      val existingDevGroups = devGroupPattern.findAll(section).toList()
      if (existingDevGroups.isNotEmpty()) {
        val retainedRequirements =
            existingDevGroups
                .asSequence()
                .flatMap { match -> quotedRequirementPattern.findAll(match.groupValues[1]) }
                .map { it.groupValues[1] }
                .filterNot { it.startsWith("fake-bpy-module-") }
                .distinct()
                .toList()
        val requirements = (listOf(targetRequirement) + retainedRequirements).joinToString(", ") { requirement -> "\"$requirement\"" }
        val updatedSection = StringBuilder(section)
        existingDevGroups.asReversed().forEachIndexed { reversedIndex, match ->
          val replacement = if (reversedIndex == existingDevGroups.lastIndex) "dev = [$requirements]" else ""
          updatedSection.replace(match.range.first, match.range.last + 1, replacement)
        }
        return content.replaceRange(sectionStart, sectionEnd, updatedSection.toString())
      }

      return content.substring(0, sectionStart) + "\ndev = [\"$targetRequirement\"]" + content.substring(sectionStart)
    }

    return content.trimEnd() + "\n\n[dependency-groups]\ndev = [\"$targetRequirement\"]\n"
  }
}
