package com.sakurasedaia.blenderdevelopment.uvPython

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.sakurasedaia.blenderdevelopment.logging.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.sakurasedaia.blenderdevelopment.model.SystemHelper
import com.sakurasedaia.blenderdevelopment.system.ExternalProcessUtil
import com.sakurasedaia.blenderdevelopment.system.ExternalToolResult
import io.github.z4kn4fein.semver.Version
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class UvHelper(private val project: Project) : Disposable {

    private val processUtil by lazy { ExternalProcessUtil(project) }

    suspend fun initializeVenv(majorMinor: String, projectPath: String): Sdk? {
        val workingDir = Path.of(projectPath)

        // Ensure uv is on disk before delegating to the platform (the platform
        // assumes the binary already exists).
        if (getUvExecutable() == null) {
            PluginLogger.getInstance(project).warn("uv binary not found; running installer first")
            installer() ?: return null
            if (getUvExecutable() == null) {
                PluginLogger.getInstance(project).error(ErrorTypes.UNSUPPORTED_OS)
                return null
            }
        }

        val existingSdks: List<Sdk> = ProjectJdkTable.getInstance().allJdks.toList()
        val version = parseVersion(majorMinor)

        return when (val result = setupNewUvSdkAndEnv(workingDir, existingSdks, version)) {
            is Result.Success -> {
                val sdk = result.result
                edtWriteAction {
                    ProjectRootManager.getInstance(project).projectSdk = sdk
                }
                PluginLogger.debug(project, "uv venv initialized at $workingDir with Python $majorMinor")
                sdk
            }
            is Result.Failure -> {
                PluginLogger.getInstance(project).warn("setupNewUvSdkAndEnv failed: ${result.error}")
                null
            }
        }
    }

    suspend fun sync(): String? {
        val basePath = project.basePath
        if (basePath == null) {
            PluginLogger.getInstance(project).warn("sync(): project has no basePath")
            return null
        }
        val cwd = Path.of(basePath)

        return when (val uvResult = createUvLowLevel(cwd)) {
            is Result.Success -> when (val syncResult = uvResult.result.sync()) {
                is Result.Success -> {
                    PluginLogger.debug(project, "uv sync succeeded")
                    syncResult.result
                }
                is Result.Failure -> {
                    PluginLogger.getInstance(project).warn("uv sync failed: ${syncResult.error}")
                    null
                }
            }
            is Result.Failure -> {
                PluginLogger.getInstance(project).warn("createUvLowLevel failed: ${uvResult.error}")
                null
            }
        }
    }

    suspend fun setupLibraries(libraries: List<String>): Boolean {
        if (libraries.isEmpty()) return true

        val sdk = ProjectRootManager.getInstance(project).projectSdk
        if (sdk == null) {
            PluginLogger.getInstance(project).warn("setupLibraries(): no project SDK is configured")
            return false
        }

        val ui = PythonPackageManagerUI.forSdk(project, sdk)
        val installed = ui.installWithConfirmation(libraries)
        return installed != null
    }
    
    suspend fun checkUvVersion(): Boolean {
        val workDir = project.basePath ?: System.getProperty("user.home")

        if (getUvExecutable() == null) {
            PluginLogger.debug(project, "uv not detected; running installer")
            return installer() != null
        }

        val updateResult = runCatching {
            processUtil.runExternalToolAsync(
                executable = "uv",
                arguments = listOf("self", "update"),
                workingDir = workDir,
            )
        }.getOrNull()

        if (updateResult == null || updateResult.exitCode != 0) {
            PluginLogger.getInstance(project).warn(
                "uv self update exited with code ${updateResult?.exitCode}"
            )
            return false
        }
        return true
    }

    override fun dispose() {
        // No owned long-lived resources; ExternalProcessUtil only holds a Project ref.
    }

    internal suspend fun installer(): ExternalToolResult? {
        val os = SystemHelper.sysInfo.osName // 'win' | 'mac' | 'linux' | 'unknown'

        val invocation: Pair<String, List<String>>? = when (os) {
            "win" -> "powershell" to listOf(
                "-ExecutionPolicy", "ByPass",
                "-c", "irm https://astral.sh/uv/install.ps1 | iex"
            )
            "unknown" -> null
            else -> "sh" to listOf(
                "-c", "curl -LsSf https://astral.sh/uv/install.sh | sh"
            )
        }

        if (invocation == null) {
            PluginLogger.getInstance(project).error(ErrorTypes.UNSUPPORTED_OS)
            return null
        }

        val (executable, args) = invocation
        val workDir = project.basePath ?: System.getProperty("user.home")

        return processUtil.runExternalToolAsync(
            executable = executable,
            arguments = args,
            workingDir = workDir,
        )
    }

    private fun parseVersion(majorMinor: String): Version? = runCatching {
        // `Version` requires a full SemVer. Pad missing components with 0.
        val parts = majorMinor.split('.').mapNotNull { it.toIntOrNull() }
        when (parts.size) {
            0 -> null
            1 -> Version(parts[0], 0, 0)
            2 -> Version(parts[0], parts[1], 0)
            else -> Version(parts[0], parts[1], parts[2])
        }
    }.getOrNull()
    
    suspend fun pipInstall(packages: List<String>) = processUtil.runExternalToolAsync(
        executable = "uv",
        arguments = listOf("pip", "install", *packages.toTypedArray()),
        workingDir = project.basePath ?: System.getProperty("user.home"),
    )
    
    companion object {
        fun pipInstall(listOf: List<String>) {}
    }
}
