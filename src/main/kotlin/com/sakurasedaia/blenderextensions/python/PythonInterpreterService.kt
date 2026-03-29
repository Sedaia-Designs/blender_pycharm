package com.sakurasedaia.blenderextensions.python

import com.sakurasedaia.blenderextensions.system.ExternalProcessUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.HttpRequests
import com.intellij.openapi.progress.ProgressManager
import com.sakurasedaia.blenderextensions.system.ArchiveUtil
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderLogger
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class PythonInterpreterService(private val project: Project) {

    fun getOrInstallPython(version: String): Path? {
        val logger = BlenderLogger.getInstance(project)
        println("[DEBUG_LOG] getOrInstallPython: Searching for Python $version in blender_downloads")
        logger.log("Searching for Python $version in blender_downloads...")
        
        // 1. Check managed/local cache first - THIS IS THE REQUIRED PATH
        val installDir = PythonUtil.getPythonInterpreterDirectory(version, project)
        val cachedPython = if (SystemInfo.isWindows) installDir.resolve("python.exe") else installDir.resolve("bin").resolve("python3")
        if (cachedPython.exists()) {
            val installedVersion = PythonUtil.getPythonVersion(cachedPython)
            if (installedVersion != null && (installedVersion.startsWith(version) || installedVersion.startsWith("$version."))) {
                println("[DEBUG_LOG] getOrInstallPython: Found cached Python $version at $cachedPython (Actual: $installedVersion)")
                logger.log("Found cached Python $version at $cachedPython")
                return cachedPython
            } else {
                println("[DEBUG_LOG] getOrInstallPython: Cached Python at $cachedPython has wrong version: $installedVersion. Deleting.")
                logger.log("Cached Python at $cachedPython has wrong version: $installedVersion. Deleting.")
                FileUtil.delete(installDir.toFile())
            }
        }

        // 1. Primary installation method: Download to blender_downloads/python/$version
        println("[DEBUG_LOG] getOrInstallPython: Python $version not found in blender_downloads. Installing to $installDir as primary method.")
        logger.log("Python $version not found. Installing to $installDir...")
        val installedPython = installPythonToBlenderDir(version, logger)
        if (installedPython != null) {
            return installedPython
        }

        // 2. Fallback to system Python if manual installation failed
        val systemPython = PythonUtil.findSystemPython(version)
        if (systemPython != null) {
            println("[DEBUG_LOG] getOrInstallPython: Manual installation failed. Found system Python $version at $systemPython as fallback.")
            logger.log("Found system Python $version at $systemPython")
            return systemPython
        }

        // 3. Final fallback: IntelliJ Python SDK
        println("[DEBUG_LOG] getOrInstallPython: Manual installation and system check failed. Attempting installation via IntelliJ Python SDK.")
        logger.log("Attempting installation via IntelliJ Python SDK...")
        val sdkPython = installPythonViaSdk(version, logger)
        if (sdkPython != null) {
            println("[DEBUG_LOG] getOrInstallPython: Successfully installed Python $version via IntelliJ Python SDK at $sdkPython")
            return sdkPython
        }

        println("[DEBUG_LOG] getOrInstallPython: All Python installation methods failed for $version.")
        logger.log("All Python installation methods failed for $version.")
        return null
    }

    fun setupPythonInterpreter(blenderExePath: String): Boolean {
        val logger = BlenderLogger.getInstance(project)
        println("[DEBUG_LOG] setupPythonInterpreter: Starting setup for Blender at $blenderExePath")
        try {
            val path = Path.of(blenderExePath)
            logger.log("Setting up Python interpreter for Blender at $blenderExePath")
            val bundledPythonExe = PythonUtil.findPythonExecutable(path)
            if (bundledPythonExe == null || !bundledPythonExe.exists()) {
                val errorMsg = "Bundled Python executable not found in $blenderExePath"
                println("[DEBUG_LOG] setupPythonInterpreter: $errorMsg")
                logger.log(errorMsg)
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "Python executable not found in $blenderExePath")
                )
                return false
            }

            println("[DEBUG_LOG] setupPythonInterpreter: Found bundled Python: $bundledPythonExe")
            logger.log("Found bundled Python: $bundledPythonExe")
            val pythonVersion = PythonUtil.getPythonVersion(bundledPythonExe)
            if (pythonVersion == null) {
                val errorMsg = "Could not determine Python version for $bundledPythonExe"
                println("[DEBUG_LOG] setupPythonInterpreter: $errorMsg")
                logger.log(errorMsg)
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error.version", bundledPythonExe.toString())
                )
                return false
            }

            println("[DEBUG_LOG] setupPythonInterpreter: Bundled Python version: $pythonVersion")
            logger.log("Bundled Python version: $pythonVersion")
            val majorMinor = toMajorMinor(pythonVersion)
            
            println("[DEBUG_LOG] setupPythonInterpreter: Targeting system Python version: $majorMinor")
            logger.log("Targeting system Python version: $majorMinor")
            // Requirement: use a matching external/system Python for environment creation.
            val pythonToUse = getOrInstallPython(majorMinor)
            if (pythonToUse == null) {
                val errorMsg = "No matching system Python found for Blender Python $majorMinor"
                println("[DEBUG_LOG] setupPythonInterpreter: $errorMsg")
                logger.log(errorMsg)
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", "No matching system Python found for Blender Python $majorMinor")
                )
                return false
            }

            val venvPath = Path.of(project.basePath ?: return false, ".venv")
            val venvPython = if (SystemInfo.isWindows) venvPath.resolve("Scripts").resolve("python.exe") else venvPath.resolve("bin").resolve("python")

            println("[DEBUG_LOG] setupPythonInterpreter: Using .venv path: $venvPath")
            
            // Validate and clean existing venv if it doesn't match Blender's Python version
            ensureMatchingVenv(venvPath, venvPython, majorMinor, logger)

            if (!venvPython.exists()) {
                println("[DEBUG_LOG] setupPythonInterpreter: Creating venv at $venvPath using $pythonToUse")
                val createVenvCmd = GeneralCommandLine(pythonToUse.toString(), "-m", "venv", venvPath.toString())
                val venvOutput = ExternalProcessUtil.execAndGetOutput(createVenvCmd)
                if (venvOutput.exitCode != 0) {
                    val errorMsg = "Venv creation failed: ${venvOutput.stderr}"
                    println("[DEBUG_LOG] setupPythonInterpreter: $errorMsg")
                    BlenderNotification(project).sendError(
                        LangManager.message("toolwindow.setup.interpreter"),
                        LangManager.message("toolwindow.setup.interpreter.error.venv", venvOutput.stderr)
                    )
                    return false
                }
            }

            val verifiedVenvVersion = PythonUtil.getPythonVersion(venvPython)
            val verifiedMatch = verifiedVenvVersion == majorMinor || verifiedVenvVersion?.startsWith("$majorMinor.") == true
            if (!verifiedMatch) {
                val errorMsg = "Created venv Python version (${verifiedVenvVersion ?: "unknown"}) does not match Blender Python $majorMinor"
                println("[DEBUG_LOG] setupPythonInterpreter: $errorMsg")
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message(
                        "toolwindow.setup.interpreter.error",
                        errorMsg
                    )
                )
                return false
            }

            println("[DEBUG_LOG] setupPythonInterpreter: Venv verified at $venvPython with version $verifiedVenvVersion")
            val pySdkType = resolvePythonSdkType()

            if (pySdkType == null) {
                val errorMsg = "Python plugin not found or Python SDK type unavailable"
                println("[DEBUG_LOG] setupPythonInterpreter: $errorMsg")
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.setup.interpreter"),
                    LangManager.message("toolwindow.setup.interpreter.error", errorMsg)
                )
                return false
            }

            val sdkName = "Blender Python (${path.parent.name})"
            println("[DEBUG_LOG] setupPythonInterpreter: Configuring SDK: $sdkName")
            ApplicationManager.getApplication().runWriteAction {
                val sdkTable = ProjectJdkTable.getInstance()
                val existingSdk = sdkTable.allJdks.find { it.name == sdkName && it.sdkType == pySdkType }
                
                val sdk = existingSdk ?: sdkTable.createSdk(sdkName, pySdkType)
                val sdkModificator = sdk.sdkModificator
                sdkModificator.homePath = venvPython.toString()
                
                // Clear existing roots to avoid duplicates when updating
                sdkModificator.removeAllRoots()

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
                
                println("[DEBUG_LOG] setupPythonInterpreter: Venv site-packages path: $venvLib")
                venvLib?.let { spPath ->
                    if (spPath.exists()) {
                        VirtualFileManager.getInstance().findFileByNioPath(spPath)?.let { vFile ->
                            sdkModificator.addRoot(vFile, OrderRootType.CLASSES)
                        }
                    }
                }

                // Add linter paths
                val versionMajorMinor = PythonUtil.getBlenderVersion(blenderExePath)
                val lintDir = PythonUtil.getLintDirectory(versionMajorMinor, project)
                println("[DEBUG_LOG] setupPythonInterpreter: Linter path: $lintDir")
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

            println("[DEBUG_LOG] setupPythonInterpreter: Setup successful. SDK Home: $venvPython")
            BlenderNotification(project).sendInfo(
                LangManager.message("toolwindow.setup.interpreter"),
                LangManager.message("toolwindow.setup.interpreter.success", venvPython.toString())
            )

            // Automatically install linter
            val blenderVersion = PythonUtil.getBlenderVersion(blenderExePath)
            if (blenderVersion != "unknown") {
                PythonLinterService.getInstance(project).installFakeBpyModule(blenderVersion)
            }

            return true
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            println("[DEBUG_LOG] setupPythonInterpreter: Error during setup: $errorMsg")
            BlenderNotification(project).sendError(
                LangManager.message("toolwindow.setup.interpreter"),
                LangManager.message("toolwindow.setup.interpreter.error", errorMsg)
            )
            return false
        }
    }

    fun setProjectInterpreterForBlenderVersion(blenderExePath: String): Boolean {
        val logger = BlenderLogger.getInstance(project)
        println("[DEBUG_LOG] setProjectInterpreterForBlenderVersion: Starting for Blender at $blenderExePath")
        return try {
            val path = Path.of(blenderExePath)
            val bundledPythonExe = PythonUtil.findPythonExecutable(path)
            if (bundledPythonExe == null || !bundledPythonExe.exists()) {
                val errorMsg = "Bundled Python executable not found in $blenderExePath"
                logger.log(errorMsg)
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.set.python.version"),
                    LangManager.message("toolwindow.set.python.version.error", errorMsg)
                )
                return false
            }

            val pythonVersion = PythonUtil.getPythonVersion(bundledPythonExe)
            if (pythonVersion == null) {
                val errorMsg = "Could not determine Python version for $bundledPythonExe"
                logger.log(errorMsg)
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.set.python.version"),
                    LangManager.message("toolwindow.set.python.version.error.version", bundledPythonExe.toString())
                )
                return false
            }

            val majorMinor = toMajorMinor(pythonVersion)
            val pythonToUse = getOrInstallPython(majorMinor)
            if (pythonToUse == null) {
                val errorMsg = "No matching Python found for Blender Python $majorMinor"
                logger.log(errorMsg)
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.set.python.version"),
                    LangManager.message("toolwindow.set.python.version.error", errorMsg)
                )
                return false
            }

            val pySdkType = resolvePythonSdkType()
            if (pySdkType == null) {
                val errorMsg = "Python plugin not found or Python SDK type unavailable"
                logger.log(errorMsg)
                BlenderNotification(project).sendError(
                    LangManager.message("toolwindow.set.python.version"),
                    LangManager.message("toolwindow.set.python.version.error", errorMsg)
                )
                return false
            }

            ApplicationManager.getApplication().runWriteAction {
                val sdkTable = ProjectJdkTable.getInstance()
                val existingSdk = sdkTable.allJdks.find {
                    it.sdkType == pySdkType && it.homePath == pythonToUse.toString()
                }
                val sdk = existingSdk ?: sdkTable.createSdk("Blender Python $majorMinor", pySdkType)
                if (existingSdk == null || sdk.homePath != pythonToUse.toString()) {
                    val sdkModificator = sdk.sdkModificator
                    sdkModificator.homePath = pythonToUse.toString()
                    sdkModificator.commitChanges()
                }
                if (existingSdk == null) {
                    sdkTable.addJdk(sdk)
                }
                ProjectRootManager.getInstance(project).projectSdk = sdk
            }

            BlenderNotification(project).sendInfo(
                LangManager.message("toolwindow.set.python.version"),
                LangManager.message("toolwindow.set.python.version.success", majorMinor, pythonToUse.toString())
            )
            true
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            logger.log("setProjectInterpreterForBlenderVersion failed: $errorMsg")
            BlenderNotification(project).sendError(
                LangManager.message("toolwindow.set.python.version"),
                LangManager.message("toolwindow.set.python.version.error", errorMsg)
            )
            false
        }
    }

    fun installPackage(packageName: String): Boolean {
        return try {
            val venvPath = Path.of(project.basePath ?: return false, ".venv")
            val venvPython = if (SystemInfo.isWindows) venvPath.resolve("Scripts").resolve("python.exe") else venvPath.resolve("bin").resolve("python")
            if (!venvPython.exists()) return false

            val command = GeneralCommandLine(venvPython.toString(), "-m", "pip", "install", packageName)
            val pipOutput = ExternalProcessUtil.execAndGetOutput(command)
            pipOutput.exitCode == 0
        } catch (e: Exception) {
            BlenderLogger.getInstance(project).log("Failed to install package '$packageName': ${e.message}")
            false
        }
    }

    private fun ensureMatchingVenv(venvPath: Path, venvPython: Path, requiredVersion: String, logger: BlenderLogger) {
        if (!venvPython.exists()) {
            println("[DEBUG_LOG] ensureMatchingVenv: No existing venv at $venvPython")
            return
        }

        val existingVenvVersion = PythonUtil.getPythonVersion(venvPython)
        val hasMatchingVenv = existingVenvVersion == requiredVersion || existingVenvVersion?.startsWith("$requiredVersion.") == true

        if (!hasMatchingVenv) {
            val rebuildMsg = "Existing .venv Python version (${existingVenvVersion ?: "unknown"}) does not match required Blender Python $requiredVersion. Rebuilding .venv."
            println("[DEBUG_LOG] ensureMatchingVenv: $rebuildMsg")
            logger.log(rebuildMsg)
            FileUtil.delete(venvPath.toFile())
        } else {
            println("[DEBUG_LOG] ensureMatchingVenv: Existing .venv version ($existingVenvVersion) matches required $requiredVersion.")
        }
    }

    private fun toMajorMinor(version: String): String {
        val parts = version.split(".")
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
    }

    private fun resolvePythonSdkType(): SdkTypeId? {
        return try {
            val sdkTypeClass = Class.forName("com.jetbrains.python.sdk.PythonSdkType")
            @Suppress("UNCHECKED_CAST")
            SdkType.findInstance(sdkTypeClass as Class<out SdkType>)
        } catch (e: Exception) {
            ProjectJdkTable.getInstance().allJdks.find { it.sdkType.name == "Python SDK" }?.sdkType
                ?: SdkType.getAllTypes().find { it.name == "Python SDK" }
        }
    }

    fun installPythonViaSdk(version: String, logger: BlenderLogger): Path? {
        println("[DEBUG_LOG] installPythonViaSdk: Attempting to install Python $version via IntelliJ SDK")
        try {
            val pySdkSettingsClass = Class.forName("com.jetbrains.python.sdk.PySdkSettings")
            val getInstanceMethod = pySdkSettingsClass.getMethod("getInstance")
            val settings = getInstanceMethod.invoke(null)
            
            val getRenderableSdksMethod = pySdkSettingsClass.getMethod("getRenderableSdksToInstall")
            val sdksToInstall = getRenderableSdksMethod.invoke(settings) as List<*>
            
            // Find a matching SDK to install.
            var sdkToInstall: Any? = null
            for (sdk in sdksToInstall) {
                if (sdk == null) continue
                val getNameMethod = sdk.javaClass.getMethod("getName")
                val sdkName = getNameMethod.invoke(sdk) as String
                if (sdkName.contains(version) || sdkName.startsWith(version)) {
                    sdkToInstall = sdk
                    break
                }
            }

            if (sdkToInstall == null) {
                println("[DEBUG_LOG] installPythonViaSdk: No matching SDK found to install for version $version")
                return null
            }

            val getNameMethod = sdkToInstall.javaClass.getMethod("getName")
            println("[DEBUG_LOG] installPythonViaSdk: Found SDK to install: ${getNameMethod.invoke(sdkToInstall)}")
            
            // Invoke install(Project, Consumer<Double>)
            // We'll try to find if there's an install method that accepts Project and a Consumer/Function for progress.
            // py-252/2025.2 has install(Project?, (Double) -> Unit) or similar.
            
            val installMethod = sdkToInstall.javaClass.methods.find { 
                it.name == "install" && it.parameterCount == 2 
            }
            
            if (installMethod != null) {
                // Try passing project and null for the callback
                val installedSdkPath = try {
                    installMethod.invoke(sdkToInstall, project, null) as? String
                } catch (e: Exception) {
                    // Try with null for project too
                    installMethod.invoke(sdkToInstall, null, null) as? String
                }
                
                if (installedSdkPath != null) {
                    val path = Path.of(installedSdkPath)
                    if (path.exists()) {
                        println("[DEBUG_LOG] installPythonViaSdk: Successfully installed Python $version at $path")
                        return path
                    }
                }
            }
        } catch (e: Exception) {
            println("[DEBUG_LOG] installPythonViaSdk: Error during SDK installation: ${e.message}")
        }
        return null
    }

    fun installPythonToBlenderDir(version: String, logger: BlenderLogger): Path? {
        val url = resolvePythonStandaloneDownloadUrl(version, logger) ?: return null

        val installDir = PythonUtil.getPythonInterpreterDirectory(version, project)
        if (!installDir.exists()) Files.createDirectories(installDir)
        
        val fileName = url.substringAfterLast("/")
        val downloadFile = installDir.resolve(fileName)
        
        try {
            println("[DEBUG_LOG] installPythonToBlenderDir: Downloading $url to $downloadFile")
            HttpRequests.request(url).saveToFile(downloadFile, ProgressManager.getInstance().progressIndicator)
            
            println("[DEBUG_LOG] installPythonToBlenderDir: Extracting $downloadFile to $installDir")
            if (fileName.endsWith(".zip")) {
                ArchiveUtil.extractZip(downloadFile, installDir, flatten = true, logger = logger)
            } else {
                ArchiveUtil.extractTar(downloadFile, installDir, stripComponents = 1, logger = logger)
            }
            
            val pythonExe = findInstalledPythonExecutable(installDir)
            if (pythonExe.exists()) {
                if (!SystemInfo.isWindows) {
                    ExternalProcessUtil.execAndGetOutput(GeneralCommandLine("chmod", "+x", pythonExe.absolutePathString()))
                }
                println("[DEBUG_LOG] installPythonToBlenderDir: Successfully installed Python $version at $pythonExe")
                
                // Final verification of the installed version
                val installedVersion = PythonUtil.getPythonVersion(pythonExe)
                if (installedVersion == null || (!installedVersion.startsWith(version) && !installedVersion.startsWith("$version."))) {
                    val warnMsg = "Installed Python version ($installedVersion) does not match expected version $version. Cleaning up."
                    println("[DEBUG_LOG] installPythonToBlenderDir: $warnMsg")
                    logger.log(warnMsg)
                    FileUtil.delete(installDir.toFile())
                    return null
                }
                
                return pythonExe
            }
        } catch (e: Exception) {
            println("[DEBUG_LOG] installPythonToBlenderDir: Error during manual installation: ${e.message}")
            logger.log("Manual installation error: ${e.message}")
        }
        return null
    }

    private fun resolvePythonStandaloneDownloadUrl(version: String, logger: BlenderLogger): String? {
        val arch = System.getProperty("os.arch").lowercase()
        val isArm64 = arch == "aarch64" || arch == "arm64"
        val platformFragment = when {
            SystemInfo.isWindows -> "x86_64-pc-windows-msvc-shared"
            SystemInfo.isMac -> if (isArm64) "aarch64-apple-darwin" else "x86_64-apple-darwin"
            else -> if (isArm64) "aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-gnu"
        }

        // Resolve the latest patch/build for the requested major.minor.
        val discovered = discoverStandaloneAssetUrl(version, platformFragment)
        if (discovered != null) {
            logger.log("Resolved python-build-standalone URL for Python $version: $discovered")
            return discovered
        }

        logger.log("Could not resolve python-build-standalone URL for Python $version on $platformFragment")
        return null
    }

    private fun discoverStandaloneAssetUrl(version: String, platformFragment: String): String? {
        return try {
            data class Candidate(val url: String, val patch: Int, val buildTag: Long, val flavorScore: Int)
            val candidates = mutableListOf<Candidate>()

            val urlRegex = Regex(
                """https://github\.com/indygreg/python-build-standalone/releases/download/[^"]*/cpython-${Regex.escape(version)}\.(\d+)\+([^-"]+)-${Regex.escape(platformFragment)}-(install_runtime|install_only|install_full)\.(tar\.gz|zip)"""
            )

            // Scan multiple pages to ensure older major.minor lines (e.g. 3.10/3.11) are still found.
            for (page in 1..3) {
                val apiUrl = "https://api.github.com/repos/indygreg/python-build-standalone/releases?per_page=100&page=$page"
                val body = HttpRequests.request(apiUrl).connect { request -> request.readString() }
                if (body.trim().isEmpty() || body.trim() == "[]") break

                urlRegex.findAll(body).forEach {
                    val patch = it.groupValues[1].toIntOrNull() ?: -1
                    val buildTag = it.groupValues[2]
                        .filter { ch -> ch.isDigit() }
                        .toLongOrNull() ?: -1L
                    val flavor = it.groupValues[3]
                    val flavorScore = when (flavor) {
                        "install_runtime" -> 3
                        "install_only" -> 2
                        else -> 1
                    }
                    candidates.add(Candidate(it.value, patch, buildTag, flavorScore))
                }
            }

            candidates
                .sortedWith(
                    compareByDescending<Candidate> { it.patch }
                        .thenByDescending { it.buildTag }
                        .thenByDescending { it.flavorScore }
                )
                .firstOrNull()
                ?.url
        } catch (_: Exception) {
            null
        }
    }

    private fun findInstalledPythonExecutable(installDir: Path): Path {
        if (SystemInfo.isWindows) {
            return installDir.resolve("python.exe")
        }

        val binDir = installDir.resolve("bin")
        if (!binDir.exists()) return installDir.resolve("bin").resolve("python3")

        val preferred = listOf("python3", "python")
        for (name in preferred) {
            val candidate = binDir.resolve(name)
            if (candidate.exists()) return candidate
        }

        val discovered = Files.list(binDir).use { stream ->
            stream.filter {
                val fileName = it.fileName.toString()
                fileName.startsWith("python") && Files.isRegularFile(it)
            }.findFirst().orElse(null)
        }
        return discovered ?: binDir.resolve("python3")
    }

    companion object {
        fun getInstance(project: Project): PythonInterpreterService = project.getService(PythonInterpreterService::class.java)
    }
}
