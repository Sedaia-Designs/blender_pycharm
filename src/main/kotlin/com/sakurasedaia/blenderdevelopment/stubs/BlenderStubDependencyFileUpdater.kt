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
  private val devGroupPattern = Regex("(?m)^dev\\s*=\\s*\\[([^\\]]*)][ \\t]*$")
  private val quotedRequirementPattern = Regex("[\"']([^\"']+)[\"']")

  fun update(project: Project, targetRequirement: String) {
    val projectDirectory = project.basePath?.let(LocalFileSystem.getInstance()::findFileByPath) ?: return
    val pyprojectFile = projectDirectory.findChild("pyproject.toml") ?: return
    VfsUtil.saveText(pyprojectFile, updateContent(VfsUtil.loadText(pyprojectFile), targetRequirement))
  }

  fun updateContent(content: String, targetRequirement: String): String {
    val existingDevGroup = devGroupPattern.find(content)
    if (existingDevGroup != null) {
      val retainedRequirements = quotedRequirementPattern.findAll(existingDevGroup.groupValues[1])
        .map { it.groupValues[1] }
        .filterNot { it.startsWith("fake-bpy-module-") }
        .toList()
      val requirements = (listOf(targetRequirement) + retainedRequirements)
        .joinToString(", ") { requirement -> "\"$requirement\"" }
      return content.replaceRange(existingDevGroup.range, "dev = [$requirements]")
    }

    val dependencyGroupHeader = "[dependency-groups]"
    val headerIndex = content.indexOf(dependencyGroupHeader)
    if (headerIndex >= 0) {
      val insertionIndex = headerIndex + dependencyGroupHeader.length
      return content.substring(0, insertionIndex) + "\ndev = [\"$targetRequirement\"]" + content.substring(insertionIndex)
    }

    return content.trimEnd() + "\n\n$dependencyGroupHeader\ndev = [\"$targetRequirement\"]\n"
  }
}
