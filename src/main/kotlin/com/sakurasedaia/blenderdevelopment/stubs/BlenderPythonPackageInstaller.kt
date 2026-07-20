/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "UnstableApiUsage")

package com.sakurasedaia.blenderdevelopment.stubs

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle

/** Isolates PyCharm package-management APIs used for Blender linting dependencies. */
interface BlenderPythonPackageInstaller {
  /**
   * Installs a development-only package into the supplied Python SDK.
   *
   * @param project project that owns the Python environment.
   * @param module Python module whose dependency metadata should be updated.
   * @param sdk target Python SDK.
   * @param requirement exact package requirement.
   * @return successful unit result or the package-manager failure.
   */
  suspend fun installDevelopmentPackage(
    project: Project,
    module: Module,
    sdk: Sdk,
    requirement: String,
  ): PyResult<Unit>

  /**
   * Removes a previously installed development-only package from the supplied Python SDK.
   *
   * @param project project that owns the Python environment.
   * @param module Python module whose dependency metadata should be updated.
   * @param sdk target Python SDK.
   * @param requirement exact package requirement.
   * @return successful unit result or the package-manager failure.
   */
  suspend fun uninstallDevelopmentPackage(
    project: Project,
    module: Module,
    sdk: Sdk,
    requirement: String,
  ): PyResult<Unit>
}

/** Production installer backed by PyCharm's SDK-specific package manager. */
class PyCharmBlenderPythonPackageInstaller : BlenderPythonPackageInstaller {
  /** Installs the requirement without adding it to the project's runtime dependencies. */
  override suspend fun installDevelopmentPackage(
    project: Project,
    module: Module,
    sdk: Sdk,
    requirement: String,
  ): PyResult<Unit> {
    val manager = PythonPackageManager.forSdk(project, sdk)
    val specification = manager.findPackageSpecification(requirement)
      ?: return PyResult.localizedError(
        MessageBundle.message("notification.blender.stubs.package.not-found", requirement),
      )
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(specification))
    return manager.installPackageDetached(request).mapSuccess { Unit }
  }

  /** Removes the requirement from the SDK when present. */
  override suspend fun uninstallDevelopmentPackage(
    project: Project,
    module: Module,
    sdk: Sdk,
    requirement: String,
  ): PyResult<Unit> {
    val manager = PythonPackageManager.forSdk(project, sdk)
    val normalizedRequirement = PyPackageName.normalizePackageName(requirement)
    val isInstalled = manager.listInstalledPackages().any { installedPackage ->
      PyPackageName.normalizePackageName(installedPackage.name) == normalizedRequirement
    }
    if (!isInstalled) return Result.success(Unit)

    return manager.uninstallPackage(requirement).mapSuccess { Unit }
  }
}
