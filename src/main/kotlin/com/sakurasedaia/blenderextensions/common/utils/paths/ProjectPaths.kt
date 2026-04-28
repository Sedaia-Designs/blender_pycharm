package com.sakurasedaia.blenderextensions.common.utils.paths

import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Functions for resolving paths relative to the project root.
 */

/**
 * Gets the virtual environment directory path for the given project.
 */
fun getVenvDir(project: Project): Path {
    val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
    return Paths.get(basePath).resolve(VENV_NAME)
}

/**
 * Gets the virtual environment configuration file path.
 */
fun getPyvenvCfgPath(project: Project): Path {
    return getVenvDir(project).resolve(PYVENV_CFG_NAME)
}

/**
 * Gets the Blender sandbox directory path for the given project.
 */
fun getSandboxDir(project: Project): Path {
    val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
    return Paths.get(basePath).resolve(SANDBOX_NAME)
}

/**
 * Gets the configuration directory within the sandbox.
 */
fun getSandboxConfigDir(project: Project): Path {
    return getSandboxDir(project).resolve("config")
}

/**
 * Gets the scripts directory within the sandbox.
 */
fun getSandboxScriptsDir(project: Project): Path {
    return getSandboxDir(project).resolve("scripts")
}

/**
 * Gets the PyCharm extensions repository directory within the sandbox scripts folder.
 */
fun getSandboxExtensionsPycharmDir(project: Project): Path {
    return getSandboxScriptsDir(project).resolve("extensions").resolve("blender_pycharm")
}

/**
 * Gets the app templates directory within the sandbox scripts folder.
 */
fun getSandboxAppTemplatesDir(project: Project): Path {
    return getSandboxScriptsDir(project).resolve("app_templates")
}

/**
 * Gets the source directory path for the given project.
 */
fun getSrcDir(project: Project): Path {
    val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
    return Paths.get(basePath).resolve(SRC_NAME)
}

/**
 * Gets the Blender manifest file path.
 */
fun getManifestPath(project: Project): Path {
    return getSrcDir(project).resolve(MANIFEST_NAME)
}

/**
 * Gets the agent configuration directory path.
 */
fun getAgentDir(project: Project): Path {
    val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
    return Paths.get(basePath).resolve(AGENT_NAME)
}

/**
 * Gets the agent skills directory path.
 */
fun getSkillsDir(project: Project): Path {
    return getAgentDir(project).resolve(SKILLS_NAME)
}

/**
 * Gets the license file path.
 */
fun getLicensePath(project: Project): Path {
    val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
    return Paths.get(basePath).resolve(LICENSE_NAME)
}

/**
 * Gets the readme file path.
 */
fun getReadmePath(project: Project): Path {
    val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
    return Paths.get(basePath).resolve(README_NAME)
}

/**
 * Gets the gitignore file path.
 */
fun getGitignorePath(project: Project): Path {
    val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
    return Paths.get(basePath).resolve(GITIGNORE_NAME)
}
