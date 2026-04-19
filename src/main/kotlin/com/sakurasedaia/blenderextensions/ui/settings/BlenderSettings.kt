package com.sakurasedaia.blenderextensions.ui.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "BlenderSettings", storages = [Storage("blender_settings.xml")])
class BlenderSettings(private val project: Project) : PersistentStateComponent<BlenderSettings.State> {
    data class State(
        var autoReload: Boolean = true,
        var blenderSourceFolders: MutableSet<String> = mutableSetOf(),
        var customBlenderPaths: MutableMap<String, String> = mutableMapOf(), // path -> name
        var downloadsPath: String = ""
    )

    private var myState = State()

    init {
        // Initialize default paths if empty
        if (myState.downloadsPath.isEmpty()) {
            myState.downloadsPath = java.nio.file.Path.of(com.intellij.openapi.application.PathManager.getSystemPath(), "blender_downloads").toString()
        }
    }

    override fun getState(): State {
        // Double check initialization
        if (myState.downloadsPath.isEmpty()) {
            myState.downloadsPath = java.nio.file.Path.of(com.intellij.openapi.application.PathManager.getSystemPath(), "blender_downloads").toString()
        }
        return myState
    }
    override fun loadState(state: State) {
        myState = state
    }

    fun isSourceFolder(path: String): Boolean = myState.blenderSourceFolders.contains(path)

    fun addSourceFolder(path: String) {
        myState.blenderSourceFolders.add(path)
    }

    fun removeSourceFolder(path: String) {
        myState.blenderSourceFolders.remove(path)
    }

    fun getSourceFolders(): Set<String> = myState.blenderSourceFolders

    fun getCustomBlenderPaths(): Map<String, String> = myState.customBlenderPaths

    fun addCustomBlenderPath(path: String, name: String? = null) {
        val pathOf = java.nio.file.Path.of(path)
        val finalName = name ?: pathOf.fileName?.toString() ?: "Custom Blender"
        myState.customBlenderPaths[pathOf.toString()] = finalName
    }

    fun removeCustomBlenderPath(path: String) {
        myState.customBlenderPaths.remove(path)
    }

    companion object {
        fun getInstance(project: Project): BlenderSettings = project.getService(BlenderSettings::class.java)
    }
}
