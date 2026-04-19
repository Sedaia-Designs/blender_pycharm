package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderFinder
import com.sakurasedaia.blenderextensions.blender.utils.BlenderHelper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import com.sakurasedaia.blenderextensions.blender.utils.BlenderPathUtil

data class BlenderInstallation(
    val name: String,
    val path: String,
    val version: String,
    val isManaged: Boolean = false,
    val isCustom: Boolean = false,
    val originPath: String? = null
)

object BlenderScanner {
    private var cachedInstallations: List<BlenderInstallation>? = null

    fun getCachedInstallations(): List<BlenderInstallation>? = cachedInstallations

    fun scanSystemInstallations(
        force: Boolean = false,
        customPaths: Map<String, String> = emptyMap()
    ): List<BlenderInstallation> {
        if (!force && cachedInstallations != null) return cachedInstallations!!

        val installations = mutableListOf<BlenderInstallation>()
        
        when {
            BlenderHelper.isWindows() -> installations.addAll(scanWindows())
            BlenderHelper.isLinux() -> installations.addAll(scanLinux())
            BlenderHelper.isMac() -> installations.addAll(scanMac())
        }

        customPaths.forEach { (pathStr, customName) ->
            val path = Path.of(pathStr)
            if (path.exists()) {
                val exe = if (path.isDirectory()) findBlenderExecutable(path) else path
                if (exe != null && exe.exists()) {
                    val version = BlenderFinder.tryGetVersion(exe.toString())
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
                it.copy(version = BlenderFinder.tryGetVersion(it.path))
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
                return stream.filter { it.name == (if (BlenderHelper.isWindows()) "blender.exe" else "blender") && !it.isDirectory() }
                    .findFirst()
                    .orElse(null)
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun addIfValid(list: MutableList<BlenderInstallation>, pathStr: String, suffix: String) {
        val path = Path.of(pathStr)
        if (path.exists() && Files.isExecutable(path)) {
            val version = BlenderFinder.tryGetVersion(path.toString())
            list.add(BlenderInstallation(LangManager.message("blender.installation.system", version), path.toString(), version))
        }
    }

    private fun tryWhich(exec: String): String? {
        return BlenderFinder.tryWhich(exec)
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

    private fun scanLinux(): List<BlenderInstallation> {
        val installations = mutableListOf<BlenderInstallation>()

        // 1. Try which command
        tryWhich("blender")?.let { addIfValid(installations, it, LangManager.message("blender.installation.manual")) }

        // 2. Common binaries in PATH
        listOf("/usr/bin/blender", "/usr/local/bin/blender", BlenderHelper.getUserHome() + "/bin/blender")
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

    private fun scanMac(): List<BlenderInstallation> {
        val installations = mutableListOf<BlenderInstallation>()
        val appPath = Path.of("/Applications/Blender.app")
        if (appPath.exists()) {
            val exe = appPath.resolve("Contents/MacOS/Blender")
            if (exe.exists()) {
                val version = BlenderFinder.tryGetVersion(exe.toString())
                installations.add(BlenderInstallation(LangManager.message("blender.installation.system", version), exe.toString(), version))
            }
        }
        return installations
    }
}

