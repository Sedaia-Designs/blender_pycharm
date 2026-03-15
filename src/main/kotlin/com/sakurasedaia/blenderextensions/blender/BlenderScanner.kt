package com.sakurasedaia.blenderextensions.blender

import com.sakurasedaia.blenderextensions.LangManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

data class BlenderInstallation(
    val name: String,
    val path: String,
    val version: String,
    val isManaged: Boolean = false,
    val isCustom: Boolean = false,
    val originPath: String? = null
)

object BlenderScanner {
    private const val VERSION_CACHE_PREFIX = "com.sakurasedaia.blenderextensions.version."
    private var cachedInstallations: List<BlenderInstallation>? = null

    fun scanSystemInstallations(
        force: Boolean = false,
        customPaths: Map<String, String> = emptyMap()
    ): List<BlenderInstallation> {
        if (!force && cachedInstallations != null) return cachedInstallations!!

        val installations = mutableListOf<BlenderInstallation>()

        when {
            SystemInfo.isWindows -> installations.addAll(scanWindows())
            SystemInfo.isMac -> installations.addAll(scanMac())
            SystemInfo.isLinux -> installations.addAll(scanLinux())
        }

        customPaths.forEach { (pathStr, customName) ->
            val path = Path.of(pathStr)
            if (path.exists()) {
                val exe = if (path.isDirectory()) findBlenderExecutable(path) else path
                if (exe != null && exe.exists()) {
                    val version = tryGetVersion(exe.toString())
                    installations.add(
                        BlenderInstallation(
                            LangManager.message("blender.installation.custom", version),
                            exe.toString(),
                            version,
                            isCustom = true,
                            originPath = pathStr
                        )
                    )
                }
            }
        }

        val result = installations.distinctBy { it.path }.map {
            if (it.version == LangManager.message("blender.version.unknown")) {
                it.copy(version = tryGetVersion(it.path))
            } else {
                it
            }
        }
        cachedInstallations = result
        return result
    }

    private fun findBlenderExecutable(path: Path): Path? {
        val exeName = BlenderPathUtil.getBlenderExecutableName()
        val exe = path.resolve(exeName)
        if (exe.exists()) return exe

        // Deep search if not immediately found in root
        try {
            Files.walk(path, 3).use { stream ->
                return stream.filter { it.name == (if (SystemInfo.isWindows) "blender.exe" else "blender") && !it.isDirectory() }
                    .findFirst()
                    .orElse(null)
            }
        } catch (e: Exception) {
            return null
        }
    }

    private val versionRegex = Regex("Blender (\\d+\\.\\d+)")

    fun tryGetVersion(path: String): String {
        val cacheKey = VERSION_CACHE_PREFIX + path.hashCode()
        val cachedVersion = PropertiesComponent.getInstance().getValue(cacheKey)
        val unknown = LangManager.message("blender.version.unknown")
        if (cachedVersion != null && cachedVersion != unknown) {
            return cachedVersion
        }

        try {
            val commandLine = com.intellij.execution.configurations.GeneralCommandLine(path, "--version")
            val output = com.intellij.execution.util.ExecUtil.execAndGetOutput(commandLine)
            if (output.exitCode != 0) return unknown
            
            // Output looks like "Blender 4.2.0\nbuild date: ..."
            val match = versionRegex.find(output.stdout)
            val version = match?.groupValues?.get(1) ?: unknown

            if (version != unknown) {
                PropertiesComponent.getInstance().setValue(cacheKey, version)
            }

            return version
        } catch (e: Exception) {
            return unknown
        }
    }

    private fun scanWindows(): List<BlenderInstallation> {
        val paths = mutableListOf<BlenderInstallation>()
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"

        listOf(programFiles, programFilesX86).forEach { base ->
            val blenderFoundation = Path.of(base, "Blender Foundation")
            if (blenderFoundation.exists() && blenderFoundation.isDirectory()) {
                Files.list(blenderFoundation).use { stream ->
                    stream.filter { it.isDirectory() && it.name.startsWith("Blender") }
                        .forEach { dir ->
                            val exe = dir.resolve("blender.exe")
                            if (exe.exists()) {
                                val version = dir.name.removePrefix("Blender").trim()
                                paths.add(BlenderInstallation(LangManager.message("blender.installation.system", version), exe.toString(), version))
                            }
                        }
                }
            }
        }
        return paths
    }

    private fun scanMac(): List<BlenderInstallation> {
        val installations = mutableListOf<BlenderInstallation>()

        // 1. Try which command
        tryWhich("Blender")?.let { addIfValid(installations, it, LangManager.message("blender.installation.manual")) }

        // 2. Scan standard Application folders
        listOf("/Applications", System.getProperty("user.home") + "/Applications").forEach { base ->
            val basePath = Path.of(base)
            if (basePath.exists() && basePath.isDirectory()) {
                Files.list(basePath).use { stream ->
                    stream.filter { it.isDirectory() && it.name == "Blender.app" }
                        .forEach { app ->
                            val exe = app.resolve("Contents/MacOS/Blender")
                            addIfValid(installations, exe.toString(), "System: $base")
                        }
                }
            }
        }
        return installations
    }

    private fun scanLinux(): List<BlenderInstallation> {
        val installations = mutableListOf<BlenderInstallation>()

        // 1. Try which command
        tryWhich("blender")?.let { addIfValid(installations, it, LangManager.message("blender.installation.manual")) }

        // 2. Common binaries in PATH
        listOf("/usr/bin/blender", "/usr/local/bin/blender", System.getProperty("user.home") + "/bin/blender")
            .forEach { addIfValid(installations, it, "System") }

        // Check /opt
        val opt = Path.of("/opt")
        if (opt.exists() && opt.isDirectory()) {
            Files.list(opt).use { stream ->
                stream.filter { it.isDirectory() && it.name.lowercase().contains("blender") }
                    .forEach { dir ->
                        val exe = dir.resolve("blender")
                        addIfValid(installations, exe.toString(), dir.name)
                    }
            }
        }

        return installations
    }

    private fun addIfValid(list: MutableList<BlenderInstallation>, pathStr: String, suffix: String) {
        val path = Path.of(pathStr)
        if (path.exists() && Files.isExecutable(path)) {
            val version = tryGetVersion(path.toString())
            list.add(BlenderInstallation(LangManager.message("blender.installation.system", version), path.toString(), version))
        }
    }

    private fun tryWhich(exec: String): String? {
        return try {
            val commandLine = com.intellij.execution.configurations.GeneralCommandLine("which", exec)
            val output = com.intellij.execution.util.ExecUtil.execAndGetOutput(commandLine)
            val result = output.stdout.trim()
            if (output.exitCode == 0 && result.isNotEmpty() && !result.contains("not found")) {
                result
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

