/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sakurasedaia.blenderdevelopment.stubs

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible

internal sealed interface BlenderPackageOperationResult {
    data object Success : BlenderPackageOperationResult

    data class Failure(val message: String) : BlenderPackageOperationResult
}

internal data class PythonPackageCommandResult(
    val exitCode: Int,
    val standardOutput: String,
    val standardError: String,
)

internal fun interface PythonPackageCommandExecutor {
    suspend fun execute(interpreterPath: Path, arguments: List<String>): PythonPackageCommandResult
}

/** Isolates PyCharm package-management APIs used for Blender linting dependencies. */
internal interface BlenderPythonPackageInstaller {
    /**
     * Installs a development-only package into the supplied Python SDK.
     *
     * @param project project that owns the Python environment.
     * @param module Python module whose dependency metadata should be updated.
     * @param sdk target Python SDK.
     * @param requirement exact package requirement.
     * @return project-owned success or failure result.
     */
    suspend fun installDevelopmentPackage(
        project: Project,
        module: Module,
        sdk: Sdk,
        requirement: String,
    ): BlenderPackageOperationResult

    /**
     * Removes a previously installed development-only package from the supplied Python SDK.
     *
     * @param project project that owns the Python environment.
     * @param module Python module whose dependency metadata should be updated.
     * @param sdk target Python SDK.
     * @param requirement exact package requirement.
     * @return project-owned success or failure result.
     */
    suspend fun uninstallDevelopmentPackage(
        project: Project,
        module: Module,
        sdk: Sdk,
        requirement: String,
    ): BlenderPackageOperationResult
}

/** Production installer backed by the selected SDK interpreter and pip. */
internal class PlatformBlenderPythonPackageInstaller(
    private val commandExecutor: PythonPackageCommandExecutor = PlatformPythonPackageCommandExecutor
) : BlenderPythonPackageInstaller {
    /** Installs the requirement without adding it to the project's runtime dependencies. */
    override suspend fun installDevelopmentPackage(
        project: Project,
        module: Module,
        sdk: Sdk,
        requirement: String,
    ): BlenderPackageOperationResult =
        execute(
            sdk = sdk,
            arguments = listOf("-m", "pip", "--disable-pip-version-check", "install", requirement),
        )

    /** Removes the requirement from the SDK when present. */
    override suspend fun uninstallDevelopmentPackage(
        project: Project,
        module: Module,
        sdk: Sdk,
        requirement: String,
    ): BlenderPackageOperationResult =
        execute(
            sdk = sdk,
            arguments = listOf("-m", "pip", "--disable-pip-version-check", "uninstall", "--yes", requirement),
        )

    private suspend fun execute(sdk: Sdk, arguments: List<String>): BlenderPackageOperationResult {
        val homePath =
            sdk.homePath?.takeIf(String::isNotBlank)
                ?: return BlenderPackageOperationResult.Failure(MessageBundle.message("notification.blender.stubs.interpreter.missing"))

        return try {
            val result = commandExecutor.execute(Path.of(homePath), arguments)
            if (result.exitCode == 0) {
                BlenderPackageOperationResult.Success
            } else {
                val diagnostic =
                    sequenceOf(result.standardError, result.standardOutput).map(String::trim).firstOrNull(String::isNotEmpty)
                        ?: MessageBundle.message("notification.blender.stubs.command.exit", result.exitCode.toString())
                BlenderPackageOperationResult.Failure(diagnostic.take(MAX_DIAGNOSTIC_LENGTH))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            BlenderPackageOperationResult.Failure(
                MessageBundle.message(
                    "notification.blender.stubs.command.failed",
                    exception.message ?: exception.javaClass.simpleName,
                )
            )
        }
    }

    private companion object {
        const val MAX_DIAGNOSTIC_LENGTH = 2_000
    }
}

private object PlatformPythonPackageCommandExecutor : PythonPackageCommandExecutor {
    override suspend fun execute(interpreterPath: Path, arguments: List<String>): PythonPackageCommandResult {
        val commandLine = GeneralCommandLine(interpreterPath.toString()).withParameters(arguments).withRedirectErrorStream(false)
        val process = commandLine.createProcess()

        return coroutineScope {
            val standardOutput = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText() } }
            val standardError = async(Dispatchers.IO) { process.errorStream.bufferedReader().use { it.readText() } }
            try {
                PythonPackageCommandResult(
                    exitCode = runInterruptible(Dispatchers.IO) { process.waitFor() },
                    standardOutput = standardOutput.await(),
                    standardError = standardError.await(),
                )
            } catch (exception: CancellationException) {
                process.destroyForcibly()
                throw exception
            }
        }
    }
}
