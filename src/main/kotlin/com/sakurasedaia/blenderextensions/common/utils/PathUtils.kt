package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.common.utils.paths.*
import java.nio.file.Path

/**
 * Facade for path-related operations, delegating to specialized utilities in the [paths] submodule.
 * @deprecated Use specific functions from com.sakurasedaia.blenderextensions.common.utils.paths instead.
 */
object PathUtils {
    // region Constants
    const val VENV_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.VENV_NAME
    const val SANDBOX_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.SANDBOX_NAME
    const val SRC_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.SRC_NAME
    const val MANIFEST_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.MANIFEST_NAME
    const val LICENSE_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.LICENSE_NAME
    const val README_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.README_NAME
    const val GITIGNORE_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.GITIGNORE_NAME
    const val AGENT_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.AGENT_NAME
    const val SKILLS_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.SKILLS_NAME
    const val PYVENV_CFG_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.PYVENV_CFG_NAME
    const val INIT_PY_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.INIT_PY_NAME
    const val AUTO_LOAD_PY_NAME = com.sakurasedaia.blenderextensions.common.utils.paths.AUTO_LOAD_PY_NAME
    const val WIKI_EXEC_URL = com.sakurasedaia.blenderextensions.common.utils.paths.WIKI_EXEC_URL
    // endregion

    // region Project-Specific Paths
    fun getVenvDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getVenvDir(project)
    fun getPyvenvCfgPath(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getPyvenvCfgPath(project)
    fun getSandboxDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getSandboxDir(project)
    fun getSandboxConfigDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getSandboxConfigDir(project)
    fun getSandboxScriptsDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getSandboxScriptsDir(project)
    fun getSandboxExtensionsPycharmDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getSandboxExtensionsPycharmDir(project)
    fun getSandboxAppTemplatesDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getSandboxAppTemplatesDir(project)
    fun getSrcDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getSrcDir(project)
    fun getManifestPath(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getManifestPath(project)
    fun getAgentDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getAgentDir(project)
    fun getSkillsDir(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getSkillsDir(project)
    fun getLicensePath(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getLicensePath(project)
    fun getReadmePath(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getReadmePath(project)
    fun getGitignorePath(project: Project) = com.sakurasedaia.blenderextensions.common.utils.paths.getGitignorePath(project)
    // endregion

    // region System & Managed Paths
    fun getBaseDownloadDirectory(project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.getBaseDownloadDirectory(project)
    fun getAppDirectory(project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.getAppDirectory(project)
    fun getVersionDirectory(project: Project, version: String?) = com.sakurasedaia.blenderextensions.common.utils.paths.getVersionDirectory(project, version)
    fun getLintDirectory(version: String, project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.getLintDirectory(version, project)
    fun getSystemBlenderConfigDir(version: String) = com.sakurasedaia.blenderextensions.common.utils.paths.getSystemBlenderConfigDir(version)
    fun getBlenderRootConfigDir() = com.sakurasedaia.blenderextensions.common.utils.paths.getBlenderRootConfigDir()
    // endregion

    // region File Operations
    fun safelyDeleteRecursively(path: Path, project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.safelyDeleteRecursively(path, project)
    fun copyDirectory(source: Path, target: Path) = com.sakurasedaia.blenderextensions.common.utils.paths.copyDirectory(source, target)
    fun makeExecutable(path: Path) = com.sakurasedaia.blenderextensions.common.utils.paths.makeExecutable(path)
    // endregion

    // region Path Validation
    fun isSafeToDelete(path: Path, project: Project?) = com.sakurasedaia.blenderextensions.common.utils.paths.isSafeToDelete(path, project)
    fun isSystemPath(path: Path) = com.sakurasedaia.blenderextensions.common.utils.paths.isSystemPath(path)
    fun getExecutionRestrictionMessage(path: Path) = com.sakurasedaia.blenderextensions.common.utils.paths.getExecutionRestrictionMessage(path)
    // endregion

    // region Blender Discovery
    fun getBlenderExecutableName() = com.sakurasedaia.blenderextensions.common.utils.paths.getBlenderExecutableName()
    fun findBlenderExecutable(directory: Path) = com.sakurasedaia.blenderextensions.common.utils.paths.findBlenderExecutable(directory)
    fun findMacExecutable(directory: Path) = com.sakurasedaia.blenderextensions.common.utils.paths.findMacExecutable(directory)
    fun findSystemPythonExecutable(targetVersion: String, project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.findSystemPythonExecutable(targetVersion, project)
    fun findBundledPython(blenderExePath: Path, project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.findBundledPython(blenderExePath, project)
    fun findPythonInDirectory(directory: Path) = com.sakurasedaia.blenderextensions.common.utils.paths.findPythonInDirectory(directory)
    // endregion

    // region Path Analysis
    fun extractVersionFromPath(path: String) = com.sakurasedaia.blenderextensions.common.utils.paths.extractVersionFromPath(path)
    fun detectVersion(project: Project?, path: String) = com.sakurasedaia.blenderextensions.common.utils.paths.detectVersion(project, path)
    fun getBlenderVersion(blenderExePath: String, project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.getBlenderVersion(blenderExePath, project)
    fun getPythonVersion(pythonExe: Path, project: Project? = null) = com.sakurasedaia.blenderextensions.common.utils.paths.getPythonVersion(pythonExe, project)
    // endregion

    // region IDE Integration
    fun addLinterToCurrentSdk(project: Project, blenderVersion: String) = com.sakurasedaia.blenderextensions.common.utils.paths.addLinterToCurrentSdk(project, blenderVersion)
    // endregion
}
