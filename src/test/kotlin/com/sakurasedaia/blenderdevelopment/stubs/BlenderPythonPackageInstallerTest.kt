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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.stubs

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.sdk.PythonSdkType
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

internal class BlenderPythonPackageInstallerTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testInstallUsesSelectedInterpreterAndPip() = runBlocking {
    val executor = RecordingCommandExecutor()
    val installer = PlatformBlenderPythonPackageInstaller(executor)

    val result = installer.installDevelopmentPackage(project, module, testSdk(), "fake-bpy-module-4.5")

    assertSame(BlenderPackageOperationResult.Success, result)
    assertEquals(Path.of("/test/python"), executor.interpreterPath)
    assertEquals(
        listOf("-m", "pip", "--disable-pip-version-check", "install", "fake-bpy-module-4.5"),
        executor.arguments,
    )
  }

  fun testUninstallIsIdempotentPipRequest() = runBlocking {
    val executor = RecordingCommandExecutor()
    val installer = PlatformBlenderPythonPackageInstaller(executor)

    val result = installer.uninstallDevelopmentPackage(project, module, testSdk(), "fake-bpy-module-4.2")

    assertSame(BlenderPackageOperationResult.Success, result)
    assertEquals(
        listOf("-m", "pip", "--disable-pip-version-check", "uninstall", "--yes", "fake-bpy-module-4.2"),
        executor.arguments,
    )
  }

  fun testNonZeroExitUsesStandardErrorAsFailureDiagnostic() = runBlocking {
    val executor =
        RecordingCommandExecutor(
            result =
                PythonPackageCommandResult(
                    exitCode = 1,
                    standardOutput = "ignored output",
                    standardError = "package installation failed",
                )
        )
    val installer = PlatformBlenderPythonPackageInstaller(executor)

    val result = installer.installDevelopmentPackage(project, module, testSdk(), "fake-bpy-module-4.5")

    assertEquals(
        BlenderPackageOperationResult.Failure("package installation failed"),
        result,
    )
  }

  fun testMissingInterpreterReturnsFailureWithoutExecutingCommand() = runBlocking {
    val executor = RecordingCommandExecutor()
    val installer = PlatformBlenderPythonPackageInstaller(executor)
    val sdk = ProjectJdkTable.getInstance().createSdk("Missing interpreter SDK", PythonSdkType.getInstance())

    val result = installer.installDevelopmentPackage(project, module, sdk, "fake-bpy-module-4.5")

    assertTrue(result is BlenderPackageOperationResult.Failure)
    assertNull(executor.interpreterPath)
  }

  fun testSdk(): Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk("Package installer test SDK", PythonSdkType.getInstance())
    WriteAction.runAndWait<RuntimeException> {
      sdk.sdkModificator.apply {
        homePath = "/test/python"
        commitChanges()
      }
    }
    return sdk
  }

  private class RecordingCommandExecutor(private val result: PythonPackageCommandResult = PythonPackageCommandResult(0, "", "")) :
      PythonPackageCommandExecutor {
    var interpreterPath: Path? = null
    var arguments: List<String> = emptyList()

    override suspend fun execute(interpreterPath: Path, arguments: List<String>): PythonPackageCommandResult {
      this.interpreterPath = interpreterPath
      this.arguments = arguments
      return result
    }
  }
}
