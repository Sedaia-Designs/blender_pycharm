package com.sakurasedaia.blenderextensions.blender.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path

@Service
class BlenderScriptGenerator {

    fun generateStartupScriptContent(port: Int, extensionName: String?, isDebugMode: Boolean = false): String {
        return BlenderScriptTemplates.getReloadScript(port, isDebugMode)
    }

    fun createStartupScript(port: Int, repoDir: Path?, extensionName: String?, isDebugMode: Boolean = false): Path {
        val repoPath = repoDir?.toAbsolutePath()?.toString()?.replace("\\", "\\\\") ?: ""
        val extName = extensionName ?: ""
        
        val sb = StringBuilder()
        sb.append(BlenderScriptTemplates.getRepoSetupScript("blender_pycharm", repoPath))
        sb.append("\n")
        
        if (extName.isNotEmpty()) {
            sb.append(BlenderScriptTemplates.getAutoEnableScript(extName))
            sb.append("\n")
        }
        
        sb.append(BlenderScriptTemplates.getReloadScript(port, isDebugMode))

        val scriptContent = sb.toString()
        
        val scratchDir = Path.of(PathManager.getConfigPath(), "scratches")
        if (!Files.exists(scratchDir)) {
            Files.createDirectories(scratchDir)
        }
        
        val tempFile = Files.createTempFile(scratchDir, "blender_start", ".py")
        Files.writeString(tempFile, scriptContent)
        return tempFile
    }

    fun cleanupStartupScript(scriptPath: Path?) {
        if (scriptPath == null) return
        try {
            Files.deleteIfExists(scriptPath)
        } catch (_: Exception) {
            // Best effort cleanup: script file may already be gone or still locked by the OS.
        }
    }

    companion object {
        fun getInstance(): BlenderScriptGenerator = com.intellij.openapi.application.ApplicationManager.getApplication().getService(BlenderScriptGenerator::class.java)
    }
}
