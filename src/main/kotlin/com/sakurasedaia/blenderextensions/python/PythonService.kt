package com.sakurasedaia.blenderextensions.python
 
import com.sakurasedaia.blenderextensions.system.ExternalProcessUtil
import com.sakurasedaia.blenderextensions.LangManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.GeneralCommandLine
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class PythonService(private val project: Project) {
    
    fun installFakeBpyModule(version: String) =
        PythonLinterService.getInstance(project).installFakeBpyModule(version)
    
    fun setupLinter(version: String) =
        PythonLinterService.getInstance(project).setupLinter(version)

    fun getBlenderPythonInfo(blenderPath: String): Pair<String, Boolean> {
        return try {
            val script = "import sys; import importlib.util; has_fake = importlib.util.find_spec('bpy') is not null; print(f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}|{has_fake}')"
            val commandLine = GeneralCommandLine(blenderPath, "--background", "--python-expr", script)
            val output = ExternalProcessUtil.execAndGetOutput(commandLine)
            if (output.exitCode == 0) {
                val lastLine = output.stdoutLines.lastOrNull { it.contains("|") }
                if (lastLine != null) {
                    val parts = lastLine.split("|")
                    return Pair(parts[0], parts[1].toBoolean())
                }
            }
            Pair("Unknown", false)
        } catch (e: Exception) {
            Pair(LangManager.message("blender.status.error") + ": ${e.message}", false)
        }
    }

    companion object {
        fun getInstance(project: Project): PythonService = project.getService(PythonService::class.java)
    }
}
