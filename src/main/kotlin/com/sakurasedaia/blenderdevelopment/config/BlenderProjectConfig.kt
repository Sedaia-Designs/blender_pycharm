package com.sakurasedaia.blenderdevelopment.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "BlenderProjectConfig", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class BlenderProjectConfig(private val project: Project): PersistentStateComponent<BlenderProjectConfig.ProjectState> {
	data class ProjectState(
		var blenderPath: String = "",
		var addonSymlinkName: String = "",
		var sandbox: Boolean = true,
		var sourceFolder: String = "src/",
	)
	
	private var state: ProjectState = ProjectState()
	
	fun getBlenderPath(): String = state.blenderPath
	fun getAddonSymlinkName(): String = state.addonSymlinkName
	fun getSandbox(): Boolean = state.sandbox
	fun getSourceFolder(): String = state.sourceFolder
	
	fun setBlenderPath(path: String) {
		state.blenderPath = path
	}
	fun setAddonSymlinkName(name: String) {
		state.addonSymlinkName = name
	}
	fun setSandbox(sandbox: Boolean) {
		state.sandbox = sandbox
	}
	fun setSourceFolder(path: String) {
		state.sourceFolder = path
	}
	
	override fun getState(): ProjectState {
		return state
	}
	
	
	override fun loadState(p0: ProjectState) {
		state = p0
	}
	
	
	companion object {
		fun getInstance(project: Project): BlenderProjectConfig = project.service()
	}
}
