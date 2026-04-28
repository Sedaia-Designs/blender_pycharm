package com.sakurasedaia.blenderextensions.common

import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Defines common paths used within the Blender project environment.
 */
object BlenderProjectPaths {
    /** The name of the virtual environment directory. */
    const val VENV_NAME = ".venv"
    
    /** The name of the Blender sandbox directory. */
    const val SANDBOX_NAME = ".blender_sandbox"

    /** The name of the source directory. */
    const val SRC_NAME = "src"

    /** The name of the Blender manifest file. */
    const val MANIFEST_NAME = "blender_manifest.toml"

    /** The name of the license file. */
    const val LICENSE_NAME = "LICENSE"

    /** The name of the readme file. */
    const val README_NAME = "README.md"

    /** The name of the gitignore file. */
    const val GITIGNORE_NAME = ".gitignore"

    /** The name of the agent configuration directory. */
    const val AGENT_NAME = ".junie"

    /** The name of the agent skills directory. */
    const val SKILLS_NAME = "skills"

    /** The name of the Python venv configuration file. */
    const val PYVENV_CFG_NAME = "pyvenv.cfg"

    /** The name of the Python init file. */
    const val INIT_PY_NAME = "__init__.py"

    /** The name of the auto-load Python file. */
    const val AUTO_LOAD_PY_NAME = "auto_load.py"

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
     * Gets the PyCharm app templates directory within the sandbox scripts folder.
     */
    fun getSandboxAppTemplatesDir(project: Project): Path {
        return getSandboxScriptsDir(project).resolve("startup/bl_app_templates/pycharm")
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
     * Gets the agent configuration directory.
     */
    fun getAgentDir(project: Project): Path {
        val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
        return Paths.get(basePath).resolve(AGENT_NAME)
    }

    /**
     * Gets the agent skills directory.
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
}
