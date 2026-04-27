package com.sakurasedaia.blenderextensions.python

import com.sakurasedaia.blenderextensions.python.PythonSdkService
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderScanner
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import com.intellij.openapi.application.PathManager
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderFinder

object PythonUtil {

    private fun getDownloadsBaseDirectory(project: Project? = null): Path {
        val configuredPath = project?.let { BlenderSettings.getInstance(it).state.downloadsPath }
            ?: Path.of(PathManager.getSystemPath(), "blender_downloads").toString()
        val configured = Path.of(configuredPath)

        // Backward compatibility: older versions stored interpreter downloads under py_interpreter.
        return if (configured.fileName?.toString() == "py_interpreter") {
            configured.parent ?: configured
        } else {
            configured
        }
    }

    fun getBlenderVersion(blenderExePath: String): String {
        val path = Path.of(blenderExePath)
        return if (blenderExePath.contains("blender_downloads")) {
            path.parent.name
        } else {
            com.sakurasedaia.blenderextensions.blender.services.BlenderFinder.tryGetVersion(blenderExePath).takeIf { it != LangManager.message("blender.version.unknown") } ?: "unknown"
        }
    }

    fun getLintDirectory(version: String, project: Project? = null): Path {
        val base = getDownloadsBaseDirectory(project)
        return base.resolve("linter").resolve(version)
    }

    fun findSystemPythonExecutable(targetVersion: String, project: Project? = null): Path? {
        BlenderLogger.debug(project, "findSystemPythonExecutable: Searching for system Python $targetVersion")
        val executableNames = if (BlenderHelper.isWindows()) {
            listOf("python.exe")
        } else {
            listOf("python$targetVersion", "python3", "python")
        }

        val pathEnv = System.getenv("PATH")
        if (pathEnv == null) {
            BlenderLogger.debug(project, "findSystemPythonExecutable: PATH environment variable is null")
            return null
        }
        val separator = if (BlenderHelper.isWindows()) ";" else ":"
        val pathDirs = pathEnv.split(separator).toMutableList()
        
        if (BlenderHelper.isWindows()) {
            val pyExe = pathDirs.asSequence()
                .mapNotNull { runCatching { Path.of(it) }.getOrNull() }
                .map { it.resolve("py.exe") }
                .firstOrNull { it.exists() }
            
            if (pyExe != null) {
                try {
                    val output = com.sakurasedaia.blenderextensions.common.utils.ExternalProcessUtil.execAndGetOutput(
                        com.intellij.execution.configurations.GeneralCommandLine(pyExe.toString(), "-$targetVersion", "-c", "import sys; print(sys.executable)")
                    )
                    if (output.exitCode == 0) {
                        val pyPath = Path.of(output.stdout.trim())
                        if (pyPath.exists()) {
                            return pyPath
                        }
                    }
                } catch (e: Exception) {
                    BlenderLogger.debug(project, "findSystemPythonExecutable: Error calling py.exe: ${e.message}")
                }
            }
        }

        for (dir in pathDirs) {
            val baseDir = runCatching { Path.of(dir) }.getOrNull() ?: continue
            for (name in executableNames) {
                val candidate = baseDir.resolve(name)
                if (!candidate.exists()) continue
                val version = getPythonVersion(candidate, project) ?: continue
                if (version == targetVersion || version.startsWith("$targetVersion.")) {
                    return candidate
                }
            }
        }

        // Search in common locations as fallback
        val commonLocations = if (BlenderHelper.isWindows()) {
            val locations = mutableListOf(
                Path.of(System.getenv("LocalAppData") ?: "", "Programs", "Python"),
                Path.of(System.getenv("ProgramFiles") ?: "", "Python")
            )
            System.getenv("SystemDrive")?.let { drive ->
                locations.add(Path.of(drive, "Python$targetVersion"))
                locations.add(Path.of(drive, "Python${targetVersion.replace(".", "")}"))
            }
            locations
        } else {
            listOf(Path.of("/usr/bin"), Path.of("/usr/local/bin"))
        }

        for (baseDir in commonLocations) {
            if (!baseDir.exists()) continue
            
            if (BlenderHelper.isWindows()) {
                try {
                    val directMatch = baseDir.resolve("python.exe")
                    if (directMatch.exists()) {
                        val version = getPythonVersion(directMatch, project)
                        if (version == targetVersion || version?.startsWith("$targetVersion.") == true) return directMatch
                    }

                    val found = Files.list(baseDir).use { stream ->
                        stream.filter { (it.toString().contains("Python", ignoreCase = true) || it.toString().contains(targetVersion)) && Files.isDirectory(it) }
                            .map { versionDir -> versionDir.resolve("python.exe") }
                            .filter { it.exists() }
                            .filter { candidate ->
                                val version = getPythonVersion(candidate, project)
                                version == targetVersion || version?.startsWith("$targetVersion.") == true
                            }
                            .findFirst().orElse(null)
                    }
                    if (found != null) return found
                } catch (e: Exception) {}
            } else {
                for (name in listOf("python$targetVersion", "python3", "python")) {
                    val candidate = baseDir.resolve(name)
                    if (candidate.exists()) {
                        val version = getPythonVersion(candidate, project)
                        if (version == targetVersion || version?.startsWith("$targetVersion.") == true) return candidate
                    }
                }
            }
        }

        return null
    }

    fun getPythonVersion(pythonExe: Path, project: Project? = null): String? {
        if (!pythonExe.exists()) {
            BlenderLogger.debug(project, "getPythonVersion: Executable does not exist: $pythonExe")
            return null
        }
        try {
            val commandLine = com.intellij.execution.configurations.GeneralCommandLine(pythonExe.toString(), "--version")
            val output = com.sakurasedaia.blenderextensions.common.utils.ExternalProcessUtil.execAndGetOutput(commandLine)
            if (output.exitCode != 0) {
                BlenderLogger.debug(project, "getPythonVersion: --version failed for $pythonExe (exit code: ${output.exitCode})")
                return null
            }
            
            val combinedOutput = (output.stdout + output.stderr).trim()
            if (combinedOutput.isEmpty()) return null

            val version = if (combinedOutput.startsWith("Python ", ignoreCase = true)) {
                combinedOutput.substring(7).trim().split(" ").firstOrNull()
            } else {
                combinedOutput.split(" ").firstOrNull()
            }
            
            if (version != null && version.all { it.isDigit() || it == '.' }) {
                BlenderLogger.debug(project, "getPythonVersion: $pythonExe version is $version")
                return version
            }
            
            BlenderLogger.debug(project, "getPythonVersion: $pythonExe version (extracted) is ${version ?: "unknown"}")
            return version
        } catch (e: Exception) {
            BlenderLogger.debug(project, "getPythonVersion: Error getting version for $pythonExe: ${e.message}")
            return null
        }
    }


    fun findBundledPython(blenderExePath: Path, project: Project? = null): Path? {
        BlenderLogger.debug(project, "findBundledPython: Searching for bundled Python in $blenderExePath")
        val blenderDir = getBlenderInternalDir(blenderExePath) ?: return null
        if (!blenderDir.exists()) {
            BlenderLogger.debug(project, "findBundledPython: Blender internal directory does not exist: $blenderDir")
            return null
        }

        try {
            val versionDirs = Files.list(blenderDir).use { stream ->
                stream.filter {
                    it.name.all { c -> c.isDigit() || c == '.' } && Files.isDirectory(it)
                }
                    .toList()
            }
            BlenderLogger.debug(project, "findBundledPython: Found versioned directories in Blender: $versionDirs")

            // First try versioned Blender paths: <root>/<version>/python/bin
            for (versionDir in versionDirs) {
                BlenderLogger.debug(project, "findBundledPython: Checking versioned dir: $versionDir")
                findPythonInDirectory(versionDir.resolve("python").resolve("bin"))?.let {
                    BlenderLogger.debug(project, "findBundledPython: Found bundled Python at $it")
                    return it
                }
                findPythonInDirectory(versionDir.resolve("python"))?.let {
                    BlenderLogger.debug(project, "findBundledPython: Found bundled Python at $it")
                    return it
                }
            }

            // Then try non-versioned layout fallbacks: <root>/python/bin
            BlenderLogger.debug(project, "findBundledPython: Checking non-versioned layout fallbacks in $blenderDir")
            findPythonInDirectory(blenderDir.resolve("python").resolve("bin"))?.let {
                BlenderLogger.debug(project, "findBundledPython: Found bundled Python at $it")
                return it
            }
            findPythonInDirectory(blenderDir.resolve("python"))?.let {
                BlenderLogger.debug(project, "findBundledPython: Found bundled Python at $it")
                return it
            }
        } catch (e: Exception) {
            BlenderLogger.debug(project, "findBundledPython: Error searching for bundled Python: ${e.message}")
            return null
        }
        BlenderLogger.debug(project, "findBundledPython: No bundled Python found in $blenderExePath")
        return null
    }

    fun addLinterToCurrentSdk(project: Project, blenderVersion: String) {
        val logger = BlenderLogger.getInstance(project)
        logger.debug("Adding linter to current SDK")
        logger.debug("Blender version: $blenderVersion")
        val sdk = ProjectRootManager.getInstance(project).projectSdk ?: return
        val lintDir = getLintDirectory(blenderVersion, project)
        if (!lintDir.exists()) return

        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                val sdkModificator = sdk.sdkModificator
                val vFile = VirtualFileManager.getInstance().findFileByNioPath(lintDir)
                if (vFile != null) {
                    logger.debug("Linter directory found: ${vFile.path}")
                    // Check if already present
                    val currentRoots = sdkModificator.getRoots(OrderRootType.CLASSES)
                    if (currentRoots.none { it.path == vFile.path }) {
                        sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                        sdkModificator.commitChanges()
                    }
                }
            }
        }
    }

    private fun findPythonInDirectory(directory: Path): Path? {
        if (!directory.exists() || !Files.isDirectory(directory)) return null

        if (BlenderHelper.isWindows()) {
            val candidate = directory.resolve("python.exe")
            return candidate.takeIf { it.exists() }
        }

        val preferredNames = listOf("python3", "python")
        for (name in preferredNames) {
            val candidate = directory.resolve(name)
            if (candidate.exists()) return candidate
        }

        return Files.list(directory).use { stream ->
            stream.filter {
                val fileName = it.fileName.toString()
                fileName.startsWith("python") && Files.isRegularFile(it)
            }.findFirst().orElse(null)
        }
    }
    private fun getBlenderInternalDir(blenderExePath: Path): Path? {
        return blenderExePath.parent
    }
}
