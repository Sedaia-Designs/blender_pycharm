package com.sakurasedaia.blenderextensions.blender.services

import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.intellij.openapi.project.Project
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
        project: Project? = null,
        force: Boolean = false,
        customPaths: Map<String, String> = emptyMap()
    ): List<BlenderInstallation> {
        if (!force && cachedInstallations != null) return cachedInstallations!!

        val installations = mutableListOf<BlenderInstallation>()
        
        when {
            BlenderHelper.isWindows() -> installations.addAll(scanWindows(project))
            BlenderHelper.isLinux() -> installations.addAll(scanLinux(project))
            BlenderHelper.isMac() -> installations.addAll(scanMac(project))
        }

        customPaths.forEach { (pathStr, _) ->
            val path = Path.of(pathStr)
            if (path.exists()) {
                val exe = if (path.isDirectory()) BlenderPathUtil.findBlenderExecutable(path) else path
                if (exe != null && exe.exists()) {
                    val version = BlenderPathUtil.detectVersion(project, exe.toString()) ?: LangManager.message("blender.version.unknown")
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
                it.copy(version = BlenderPathUtil.detectVersion(project, it.path) ?: LangManager.message("blender.version.unknown"))
            } else {
                it
            }
        }
        cachedInstallations = result
        return result
    }


    private fun addIfValid(list: MutableList<BlenderInstallation>, pathStr: String, project: Project? = null) {
        val path = Path.of(pathStr)
        if (path.exists() && Files.isExecutable(path)) {
            val version = BlenderPathUtil.detectVersion(project, path.toString()) ?: LangManager.message("blender.version.unknown")
            list.add(BlenderInstallation(LangManager.message("blender.installation.system", version), path.toString(), version))
        }
    }

    private fun tryWhich(exec: String): String? {
        return BlenderFinder.tryWhich(exec)
    }

    private fun scanWindows(project: Project? = null): List<BlenderInstallation> {
        val paths = mutableListOf<BlenderInstallation>()
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        val localAppData = System.getenv("LOCALAPPDATA")

        val bases = mutableListOf(programFiles, programFilesX86)
        if (localAppData != null) {
            bases.add(Path.of(localAppData, "Programs").toString())
        }

        bases.forEach { base ->
            val blenderFoundation = Path.of(base, "Blender Foundation")
            if (blenderFoundation.exists() && blenderFoundation.isDirectory()) {
                Files.list(blenderFoundation).use { stream ->
                    stream.filter { it.isDirectory() && it.name.contains("Blender", ignoreCase = true) }
                        .forEach { dir ->
                            val exe = dir.resolve("blender.exe")
                            if (exe.exists()) {
                                var version = dir.name.replace("Blender", "", ignoreCase = true).trim()
                                if (version.isEmpty()) {
                                    version = BlenderPathUtil.detectVersion(project, exe.toString()) ?: LangManager.message("blender.version.unknown")
                                }
                                paths.add(BlenderInstallation(LangManager.message("blender.installation.system", version), exe.toString(), version))
                            }
                        }
                }
            }
        }
        return paths
    }

    private fun scanLinux(project: Project? = null): List<BlenderInstallation> {
        val installations = mutableListOf<BlenderInstallation>()

        // 1. Try which command
        tryWhich("blender")?.let { addIfValid(installations, it, project) }

        // 2. Common binaries in PATH
        listOf("/usr/bin/blender", "/usr/local/bin/blender", BlenderHelper.getUserHome() + "/bin/blender")
            .forEach { addIfValid(installations, it, project) }

        // Check /opt
        val opt = Path.of("/opt")
        if (opt.exists() && opt.isDirectory()) {
            Files.list(opt).use { stream ->
                stream.filter { it.isDirectory() && it.name.lowercase().contains("blender") }
                    .forEach { dir ->
                        val exe = dir.resolve("blender")
                        addIfValid(installations, exe.toString(), project)
                    }
            }
        }

        return installations
    }

    private fun scanMac(project: Project? = null): List<BlenderInstallation> {
        val installations = mutableListOf<BlenderInstallation>()
        val searchPaths = listOf(
            Path.of("/Applications"),
            Path.of(System.getProperty("user.home"), "Applications")
        )

        searchPaths.forEach { appsDir ->
            if (appsDir.exists() && appsDir.isDirectory()) {
                Files.list(appsDir).use { stream ->
                    stream.filter { it.isDirectory() && it.name.contains("Blender", ignoreCase = true) && it.name.endsWith(".app") }
                        .forEach { appPath ->
                            val exe = appPath.resolve("Contents/MacOS/Blender")
                            if (exe.exists()) {
                                val version = BlenderPathUtil.detectVersion(project, exe.toString()) ?: LangManager.message("blender.version.unknown")
                                installations.add(BlenderInstallation(LangManager.message("blender.installation.system", version), exe.toString(), version))
                            }
                        }
                }
            }
        }
        return installations
    }
}

