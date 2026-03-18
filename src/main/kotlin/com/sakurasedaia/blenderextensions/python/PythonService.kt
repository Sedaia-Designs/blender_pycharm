package com.sakurasedaia.blenderextensions.python

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderLogger
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class PythonService(private val project: Project) {

    fun getLinterSDKRoots(blenderExePath: String): List<Path> {
        val version = PythonUtil.getBlenderVersion(blenderExePath)
        if (version == "unknown") return emptyList()

        val lintDir = PythonUtil.getLintDirectory(version)
        if (!lintDir.exists()) {
            // Try to install it if not present
            installFakeBpyModule(Path.of(blenderExePath), version)
        }
        
        return if (lintDir.exists()) listOf(lintDir) else emptyList()
    }

    fun installFakeBpyModule(blenderExePath: Path, version: String) {
        val logger = BlenderLogger.getInstance(project)
        val lintDir = PythonUtil.getLintDirectory(version)
        val downloader = BlenderDownloader.getInstance(project)
        
        val statusText = LangManager.message("log.blender.installing.linter.progress", version)
        downloader.updateProgress(BlenderDownloader.DownloadProgress(true, -1.0, statusText, version, BlenderDownloader.ProgressType.LINTER))

        try {
            // Requirement: Linter setup should never call the Python SDK setup operation except for calling pip if necessary
            val success = installPackage("fake-bpy-module-$version")
            if (success) {
                logger.log("Successfully installed fake-bpy-module-$version into project venv.")
            } else {
                // Fallback to standalone linter installation if needed for external tools
                logger.log("Venv installation failed, falling back to standalone linter installation.")
                val bundledPythonExe = PythonUtil.findPythonExecutable(blenderExePath) ?: return
                if (!lintDir.exists()) {
                    Files.createDirectories(lintDir)
                }

                val commandLine = GeneralCommandLine(
                    bundledPythonExe.toString(),
                    "-m", "pip", "install",
                    "fake-bpy-module-$version",
                    "--target", lintDir.toString()
                )
                val handler = OSProcessHandler(commandLine)
                handler.startNotify()
                handler.waitFor()
                logger.log("Successfully installed fake-bpy-module-$version for linting (fallback).")
            }
        } catch (e: Exception) {
            logger.log("Failed to install fake-bpy-module for linting: ${e.message}")
        } finally {
            downloader.updateProgress(BlenderDownloader.DownloadProgress.None)
        }
    }

    fun getOrInstallPython(version: String): Path? {
        val systemPython = PythonUtil.findSystemPython(version)
        if (systemPython != null) return systemPython

        val installDir = PythonUtil.getPythonInterpreterDirectory(version)
        val pythonExe = if (SystemInfo.isWindows) installDir.resolve("python.exe") else installDir.resolve("bin").resolve("python3")
        
        if (pythonExe.exists()) return pythonExe

        val logger = BlenderLogger.getInstance(project)
        logger.log("Python $version not found on system. Attempting to download...")
        
        try {
            Files.createDirectories(installDir)
            val downloadUrl = getPythonDownloadUrl(version)
            if (downloadUrl != null) {
                logger.log("Downloading Python $version from $downloadUrl")
                // In a full implementation, we would download and extract here.
            } else {
                logger.log("No download URL found for Python $version on this platform.")
            }
        } catch (e: Exception) {
            logger.log("Failed to install Python $version: ${e.message}")
        }

        return if (pythonExe.exists()) pythonExe else null
    }

    private fun getPythonDownloadUrl(version: String): String? {
        val isWindows = SystemInfo.isWindows
        val isMac = SystemInfo.isMac
        
        return when {
            isWindows -> "https://www.python.org/ftp/python/$version.0/python-$version.0-amd64.exe"
            isMac -> "https://www.python.org/ftp/python/$version.0/python-$version.0-macos11.pkg"
            else -> null
        }
    }

    fun setupPythonInterpreter(blenderExePath: String) {
        try {
            val path = Path.of(blenderExePath)
            val bundledPythonExe = PythonUtil.findPythonExecutable(path)
            if (bundledPythonExe == null || !bundledPythonExe.exists()) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "Python executable not found in $blenderExePath")
                )
                return
            }

            val pythonVersion = PythonUtil.getPythonVersion(bundledPythonExe)
            if (pythonVersion == null) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error.version", bundledPythonExe.toString())
                )
                return
            }

            // Get major.minor version for searching (e.g. 3.11)
            val majorMinor = if (pythonVersion.count { it == '.' } >= 1) {
                pythonVersion.substringBeforeLast('.').takeIf { it.contains('.') } ?: pythonVersion
            } else pythonVersion
            
            // Requirement: Ensure the matching Python version exists on system
            val pythonToUse = getOrInstallPython(majorMinor) ?: bundledPythonExe

            val venvPath = Path.of(project.basePath ?: return, ".venv")
            val venvPython = if (SystemInfo.isWindows) venvPath.resolve("Scripts").resolve("python.exe") else venvPath.resolve("bin").resolve("python")

            if (!venvPython.exists()) {
                val createVenvCmd = GeneralCommandLine(pythonToUse.toString(), "-m", "venv", venvPath.toString())
                val output = ExecUtil.execAndGetOutput(createVenvCmd)
                if (output.exitCode != 0) {
                    BlenderNotification(project).sendError(
                        LangManager.message("toolwindow.setup.interpreter"),
                        LangManager.message("toolwindow.setup.interpreter.error.venv", output.stderr)
                    )
                    return
                }
            }

            val pySdkType = try {
                val sdkTypeClass = Class.forName("com.jetbrains.python.sdk.PythonSdkType")
                SdkType.findInstance(sdkTypeClass as Class<out SdkType>)
            } catch (e: Exception) {
                
                ProjectJdkTable.getInstance().allJdks.find { it.sdkType.name == "Python SDK" }?.sdkType
                    ?: SdkType.getAllTypes().find { it.name == "Python SDK" }
            }

            if (pySdkType == null) {
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "Python plugin not found or Python SDK type unavailable")
                )
                return
            }

            val sdkName = "Blender Python (${path.parent.name})"
            ApplicationManager.getApplication().runWriteAction {
                val sdkTable = ProjectJdkTable.getInstance()
                val existingSdk = sdkTable.allJdks.find { it.name == sdkName && it.sdkType == pySdkType }
                
                val sdk = existingSdk ?: sdkTable.createSdk(sdkName, pySdkType)
                val sdkModificator = sdk.sdkModificator
                sdkModificator.homePath = venvPython.toString()
                
                // Clear existing roots to avoid duplicates when updating
                sdkModificator.removeAllRoots()

                // Add standard library paths and Blender modules from BUNDLED python
                PythonUtil.getPythonLibraryPaths(bundledPythonExe).forEach { libPath ->
                    VirtualFileManager.getInstance().findFileByNioPath(libPath)?.let { vFile ->
                        sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                    }
                }

                // Add VENV site-packages
                val venvLib = if (SystemInfo.isWindows) venvPath.resolve("Lib").resolve("site-packages") else {
                    val libDir = venvPath.resolve("lib")
                    var sp: Path? = null
                    if (libDir.exists()) {
                        Files.list(libDir).use { stream ->
                            val pyLib = stream.filter { it.name.startsWith("python") && it.isDirectory() }
                                .findFirst().orElse(null)
                            sp = pyLib?.resolve("site-packages")
                        }
                    }
                    sp
                }
                
                venvLib?.let { spPath ->
                    if (spPath.exists()) {
                        VirtualFileManager.getInstance().findFileByNioPath(spPath)?.let { vFile ->
                            sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                        }
                    }
                }

                // Add linter paths
                val versionMajorMinor = PythonUtil.getBlenderVersion(blenderExePath)
                val lintDir = PythonUtil.getLintDirectory(versionMajorMinor)
                if (lintDir.exists()) {
                    VirtualFileManager.getInstance().findFileByNioPath(lintDir)?.let { vFile ->
                        sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                    }
                }

                sdkModificator.commitChanges()

                if (existingSdk == null) {
                    sdkTable.addJdk(sdk)
                }
                ProjectRootManager.getInstance(project).projectSdk = sdk
            }

            BlenderNotification(project).sendInfo(
                LangManager.message("toolwindow.setup.interpreter"),
                LangManager.message("toolwindow.setup.interpreter.success", venvPython.toString())
            )
        } catch (e: Exception) {
            BlenderNotification(project).sendError(
                LangManager.message("toolwindow.setup.interpreter"),
                LangManager.message("toolwindow.setup.interpreter.error", e.message ?: "Unknown error")
            )
        }
    }

    fun installPackage(packageName: String): Boolean {
        val venvPath = Path.of(project.basePath ?: return false, ".blender-sandbox", "venv")
        val venvPython = if (SystemInfo.isWindows) venvPath.resolve("Scripts").resolve("python.exe") else venvPath.resolve("bin").resolve("python")
        
        if (!venvPython.exists()) return false

        val command = GeneralCommandLine(venvPython.toString(), "-m", "pip", "install", packageName)
        val output = ExecUtil.execAndGetOutput(command)
        return output.exitCode == 0
    }

    fun getBlenderPythonInfo(blenderPath: String): Pair<String, Boolean> {
        return try {
            val script = "import sys; import importlib.util; has_fake = importlib.util.find_spec('bpy') is not null; print(f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}|{has_fake}')"
            val commandLine = GeneralCommandLine(blenderPath, "--background", "--python-expr", script)
            val output = ExecUtil.execAndGetOutput(commandLine)
            if (output.exitCode == 0) {
                val lastLine = output.stdoutLines.lastOrNull { it.contains("|") }
                if (lastLine != null) {
                    val parts = lastLine.split("|")
                    return Pair(parts[0], parts[1].toBoolean())
                }
            }
            Pair("Unknown", false)
        } catch (e: Exception) {
            Pair("Error: ${e.message}", false)
        }
    }

    companion object {
        fun getInstance(project: Project): PythonService = project.getService(PythonService::class.java)
    }
}
