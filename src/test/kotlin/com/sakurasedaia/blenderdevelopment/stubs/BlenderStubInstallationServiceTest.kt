/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sakurasedaia.blenderdevelopment.stubs

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonSdkType
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import kotlinx.coroutines.runBlocking

/** Tests ordered linting-stub replacement through an isolated package installer. */
class BlenderStubInstallationServiceTest : BasePlatformTestCase() {
  private lateinit var config: ProjectConfig
  private lateinit var installer: FakePackageInstaller
  private lateinit var service: BlenderStubInstallationService

  override fun runInDispatchThread(): Boolean = false

  /** Initializes clean project state and a deterministic installer fake. */
  override fun setUp() {
    super.setUp()
    config = ProjectConfig.getInstance(project)
    config.loadState(ProjectConfig.ProjectState())
    installer = FakePackageInstaller()
    service = BlenderStubInstallationService(project).withPackageInstaller(installer)
  }

  /** Verifies that replacement removes the old package before installing the target package. */
  fun testChangedVersionUninstallsPreviousRequirementBeforeInstallingNewRequirement() = runBlocking {
    config.setInstalledStubRequirement("fake-bpy-module-4.2")

    val status = service.replaceForChangedVersion(module, testSdk(), "4.5")

    assertEquals(BlenderStubOperationStatus.UPDATED, status)
    assertEquals(
      listOf("uninstall:fake-bpy-module-4.2", "install:fake-bpy-module-4.5"),
      installer.operations,
    )
    assertEquals("fake-bpy-module-4.5", config.getInstalledStubRequirement())
  }

  /** Verifies that a registry override replaces the previous version-specific package. */
  fun testRegistryOverrideReplacesPreviousRequirement() = runBlocking {
    config.setInstalledStubRequirement("fake-bpy-module-4.5")

    val status = service.replaceForChangedVersion(module, testSdk(), "5.2")

    assertEquals(BlenderStubOperationStatus.UPDATED, status)
    assertEquals(
      listOf("uninstall:fake-bpy-module-4.5", "install:fake-bpy-module-latest"),
      installer.operations,
    )
    assertEquals("fake-bpy-module-latest", config.getInstalledStubRequirement())
  }

  /** Verifies that a target absent from the registry leaves the previous package untouched. */
  fun testUnknownTargetDoesNotUninstallPreviousRequirement() = runBlocking {
    config.setInstalledStubRequirement("fake-bpy-module-4.5")

    val status = service.replaceForChangedVersion(module, testSdk(), "9.9")

    assertEquals(BlenderStubOperationStatus.UNSUPPORTED, status)
    assertTrue(installer.operations.isEmpty())
    assertEquals("fake-bpy-module-4.5", config.getInstalledStubRequirement())
  }

  /** Verifies that initial and repeated requests safely use the exact same requirement. */
  fun testRepeatedInstallationIsSafe() = runBlocking {
    val sdk = testSdk()
    assertEquals(
      BlenderStubOperationStatus.INSTALLED,
      service.installForGeneratedProject(module, sdk, "4.2"),
    )
    assertEquals(
      BlenderStubOperationStatus.INSTALLED,
      service.installForGeneratedProject(module, sdk, "4.2.19"),
    )

    assertEquals(
      listOf("install:fake-bpy-module-4.2", "install:fake-bpy-module-4.2"),
      installer.operations,
    )
  }

  /** Verifies that package-manager failure is reported without throwing into project generation. */
  fun testInstallationFailureReturnsNonfatalStatus() = runBlocking {
    installer.failInstallation = true

    val status = service.installForGeneratedProject(module, testSdk(), "4.5")

    assertEquals(BlenderStubOperationStatus.FAILED, status)
    assertEquals("", config.getInstalledStubRequirement())
  }

  /** Creates a lightweight Python SDK for installer-boundary tests. */
  private fun testSdk(): Sdk =
    ProjectJdkTable.getInstance().createSdk("Blender stub test SDK", PythonSdkType.getInstance())

  /** Records package operations while returning successful PyCharm results. */
  private class FakePackageInstaller : BlenderPythonPackageInstaller {
    val operations = mutableListOf<String>()
    var failInstallation: Boolean = false

    /** Records an installation request. */
    override suspend fun installDevelopmentPackage(
      project: Project,
      module: Module,
      sdk: Sdk,
      requirement: String,
    ): PyResult<Unit> {
      operations += "install:$requirement"
      if (failInstallation) return PyResult.localizedError("Simulated installation failure")
      return PyResult.success(Unit)
    }

    /** Records an uninstallation request. */
    override suspend fun uninstallDevelopmentPackage(
      project: Project,
      module: Module,
      sdk: Sdk,
      requirement: String,
    ): PyResult<Unit> {
      operations += "uninstall:$requirement"
      return PyResult.success(Unit)
    }
  }
}
