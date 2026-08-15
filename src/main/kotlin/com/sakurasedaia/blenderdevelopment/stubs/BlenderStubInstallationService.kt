/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sakurasedaia.blenderdevelopment.stubs

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkUtil
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.NotificationModal
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import kotlinx.coroutines.CancellationException

/** Summarizes the outcome of a Blender linting-stub installation request. */
enum class BlenderStubOperationStatus {
  INSTALLED,
  UPDATED,
  UNSUPPORTED,
  FAILED,
}

/** Coordinates safe version-specific stub installation for a project Python SDK. */
@Service(Service.Level.PROJECT)
class BlenderStubInstallationService(private val project: Project) {
  private var packageInstaller: BlenderPythonPackageInstaller = PlatformBlenderPythonPackageInstaller()

  /**
   * Installs or replaces stubs using the first project module with a configured Python SDK.
   *
   * @param blenderVersion selected Blender version.
   * @return operation status for UI feedback.
   */
  suspend fun installForProject(blenderVersion: String): BlenderStubOperationStatus {
    val moduleAndSdk =
        ModuleManager.getInstance(project).modules.firstNotNullOfOrNull { module ->
          PythonSdkUtil.findPythonSdk(module)?.let { sdk -> module to sdk }
        }
    if (moduleAndSdk == null) {
      val message = MessageBundle.message("notification.blender.stubs.interpreter.missing")
      PluginLogger.getInstance(project).warn(ErrorTypes.STUB_INTERPRETER_MISSING.toString())
      NotificationModal.getInstance(project).sendWarning(message)
      return BlenderStubOperationStatus.FAILED
    }
    return replaceForChangedVersion(moduleAndSdk.first, moduleAndSdk.second, blenderVersion)
  }

  /**
   * Installs stubs for a generated project without making scaffolding depend on network success.
   *
   * @param module generated Python module.
   * @param sdk Python SDK assigned by PyCharm.
   * @param blenderVersion selected Blender version.
   * @return operation status for diagnostics and tests.
   */
  suspend fun installForGeneratedProject(
      module: Module,
      sdk: Sdk,
      blenderVersion: String,
  ): BlenderStubOperationStatus =
      try {
        installOrReplace(module, sdk, blenderVersion, replaceExisting = false)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        val message =
            MessageBundle.message(
                "notification.blender.stubs.unexpected.failure",
                exception.message ?: exception.javaClass.simpleName,
            )
        PluginLogger.getInstance(project)
            .warn(
                ErrorTypes.STUB_OPERATION_FAILED.format(exception.message ?: exception.javaClass.simpleName),
                exception,
            )
        NotificationModal.getInstance(project).sendError(message)
        BlenderStubOperationStatus.FAILED
      }

  /**
   * Replaces the recorded stub package after the target Blender version changes.
   *
   * The target requirement is resolved before removal so unsupported versions cannot remove a working package.
   *
   * @param module project Python module.
   * @param sdk Python SDK assigned to the module.
   * @param blenderVersion newly selected Blender version.
   * @return operation status for UI refresh.
   */
  suspend fun replaceForChangedVersion(
      module: Module,
      sdk: Sdk,
      blenderVersion: String,
  ): BlenderStubOperationStatus = installOrReplace(module, sdk, blenderVersion, replaceExisting = true)

  /**
   * Replaces the package installer for a test and returns this service for fluent setup.
   *
   * @param installer deterministic installer fake.
   * @return this service.
   */
  internal fun withPackageInstaller(installer: BlenderPythonPackageInstaller): BlenderStubInstallationService {
    packageInstaller = installer
    return this
  }

  /**
   * Performs installation or ordered uninstall/install replacement.
   *
   * @param module project Python module.
   * @param sdk target Python SDK.
   * @param blenderVersion requested Blender version.
   * @param replaceExisting whether the recorded previous package should be removed first.
   * @return final operation status.
   */
  private suspend fun installOrReplace(
      module: Module,
      sdk: Sdk,
      blenderVersion: String,
      replaceExisting: Boolean,
  ): BlenderStubOperationStatus {
    val config = ProjectConfig.getInstance(project)
    val logger = PluginLogger.getInstance(project)
    val notifications = NotificationModal.getInstance(project)
    val requirement = BlenderStubRequirementResolver.resolve(blenderVersion)
    if (requirement == null) {
      val message = MessageBundle.message("notification.blender.stubs.unsupported", blenderVersion)
      logger.warn(ErrorTypes.STUB_VERSION_UNSUPPORTED.format(blenderVersion))
      notifications.sendWarning(message)
      return BlenderStubOperationStatus.UNSUPPORTED
    }

    val previousRequirement = config.getInstalledStubRequirement().takeIf(String::isNotBlank)
    val shouldRemovePrevious = replaceExisting && previousRequirement != null && previousRequirement != requirement
    // TODO(V1): Make replacement transactional so a failed install preserves or restores the previously working requirement.
    if (shouldRemovePrevious) {
      when (
          val uninstallResult =
              packageInstaller.uninstallDevelopmentPackage(
                  project,
                  module,
                  sdk,
                  previousRequirement,
              )
      ) {
        BlenderPackageOperationResult.Success -> config.setInstalledStubRequirement("")
        is BlenderPackageOperationResult.Failure -> {
          val message =
              MessageBundle.message(
                  "notification.blender.stubs.uninstall.failed",
                  previousRequirement,
                  uninstallResult.message,
              )
          logger.warn(ErrorTypes.STUB_UNINSTALL_FAILED.format(previousRequirement, uninstallResult.message))
          notifications.sendError(message)
          return BlenderStubOperationStatus.FAILED
        }
      }
    }

    return when (val installResult = packageInstaller.installDevelopmentPackage(project, module, sdk, requirement)) {
      BlenderPackageOperationResult.Success -> {
        config.setInstalledStubRequirement(requirement)
        try {
          edtWriteAction {
            BlenderStubDependencyFileUpdater.update(project, requirement)
          }
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: Exception) {
          val message =
              MessageBundle.message(
                  "notification.blender.stubs.metadata.failed",
                  requirement,
                  exception.message ?: exception.javaClass.simpleName,
              )
          logger.warn(
              ErrorTypes.STUB_METADATA_FAILED.format(requirement, exception.message ?: exception.javaClass.simpleName),
              exception,
          )
          notifications.sendWarning(message)
        }
        val status = if (shouldRemovePrevious) BlenderStubOperationStatus.UPDATED else BlenderStubOperationStatus.INSTALLED
        if (replaceExisting) {
          notifications.sendInfo(MessageBundle.message("notification.blender.stubs.install.succeeded", requirement))
        }
        status
      }
      is BlenderPackageOperationResult.Failure -> {
        val message =
            MessageBundle.message(
                "notification.blender.stubs.install.failed",
                requirement,
                installResult.message,
            )
        logger.warn(ErrorTypes.STUB_INSTALL_FAILED.format(requirement, installResult.message))
        notifications.sendError(message)
        BlenderStubOperationStatus.FAILED
      }
    }
  }

  companion object {
    /**
     * Returns the project-scoped installation service.
     *
     * @param project target project.
     * @return installation service.
     */
    fun getInstance(project: Project): BlenderStubInstallationService = project.service()
  }
}
