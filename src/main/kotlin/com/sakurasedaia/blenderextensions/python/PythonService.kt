package com.sakurasedaia.blenderextensions.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderScanner
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class PythonService(private val project: Project) {

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

            val venvPath = Path.of(project.basePath ?: return, ".blender-sandbox", "venv")
            val venvPython = if (SystemInfo.isWindows) venvPath.resolve("Scripts").resolve("python.exe") else venvPath.resolve("bin").resolve("python")

            if (!venvPython.exists()) {
                val createVenvCmd = com.intellij.execution.configurations.GeneralCommandLine(bundledPythonExe.toString(), "-m", "venv", venvPath.toString())
                val output = com.intellij.execution.util.ExecUtil.execAndGetOutput(createVenvCmd)
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

                // IMPORTANT: Add VENV site-packages to ensure user-installed packages are available
                val venvLib = if (SystemInfo.isWindows) venvPath.resolve("Lib").resolve("site-packages") else {
                    // On Linux/Mac, it's usually venv/lib/python3.x/site-packages
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

                // Add linting paths if available
                val version = if (blenderExePath.contains("blender_downloads")) {
                    path.parent.name
                } else {
                    BlenderScanner.tryGetVersion(blenderExePath).takeIf { it != LangManager.message("blender.version.unknown") } ?: "unknown"
                }
                
                if (version != "unknown") {
                    val downloader = BlenderDownloader.getInstance(project)
                    val lintDir = downloader.getLintDirectory(version)
                    if (lintDir.exists()) {
                        VirtualFileManager.getInstance().findFileByNioPath(lintDir)?.let { vFile ->
                            sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                        }
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

    companion object {
        fun getInstance(project: Project): PythonService = project.getService(PythonService::class.java)
    }
}
