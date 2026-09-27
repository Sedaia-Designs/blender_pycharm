package com.sakurasedaia.blenderdevelopment.ui.toolwindow

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig.BlenderLogLevel
import java.awt.Container
import javax.swing.JButton
import javax.swing.ScrollPaneConstants

class BlenderToolWindowViewTest : BasePlatformTestCase() {
    fun testRenderDoesNotEmitChangeCallbacks() {
        val view = BlenderToolWindowView(project)
        var changeCount = 0
        view.onBlenderPathChanged = { changeCount++ }
        view.onAddonSymlinkNameChanged = { changeCount++ }
        view.onSourceFolderChanged = { changeCount++ }
        view.onRunArgumentsChanged = { changeCount++ }
        view.onBlenderLogLevelChanged = { changeCount++ }
        view.onReloadOnSaveChanged = { changeCount++ }
        view.onJustMyCodeChanged = { changeCount++ }
        view.onSaveWorkspaceConfigRequested = { changeCount++ }
        view.onEnvironmentVariablesChanged = { changeCount++ }
        view.onScriptDirectoriesChanged = { changeCount++ }
        view.onBlendFileToOpenChanged = { changeCount++ }

        view.render(state(blenderPath = "/Applications/Blender.app"))

        assertEquals(0, changeCount)
        assertEquals("/Applications/Blender.app", view.blenderPath)
    }

    fun testBlendFileRenderAndManualEditEmitImmediately() {
        val view = BlenderToolWindowView(project)
        var changedValue: String? = null
        view.onBlendFileToOpenChanged = { changedValue = it }
        view.render(state(blenderPath = "/Applications/Blender.app", blendFileToOpen = "scenes/initial.blend"))
        val blendFileField = descendantsOf(view.component).filterIsInstance<TextFieldWithBrowseButton>().last()

        blendFileField.text = "scenes/edited.blend"

        assertEquals("scenes/edited.blend", changedValue)
    }

    fun testRefreshSelectsFirstInstallWhenPreviousInstallDisappears() {
        val view = BlenderToolWindowView(project)
        view.render(state(blenderPath = "/Applications/Blender.app"))

        view.render(
            state(
                blenderPath = "/Applications/Blender 4.2.app",
                installs = listOf(install(name = "Blender 4.2", path = "/Applications/Blender 4.2.app")),
            )
        )

        assertEquals("/Applications/Blender 4.2.app", view.blenderPath)
    }

    fun testComponentUsesVerticalOnlyScrollPane() {
        val view = BlenderToolWindowView(project)
        val scrollPane = view.component as JBScrollPane

        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, scrollPane.verticalScrollBarPolicy)
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)
    }

    fun testInstallStubsButtonUsesSelectedBlenderVersion() {
        val view = BlenderToolWindowView(project)
        var requestedVersion: String? = null
        view.onInstallStubsRequested = { requestedVersion = it }
        view.render(state(blenderPath = "/Applications/Blender.app"))

        val button = descendantsOf(view.component).filterIsInstance<JButton>().single { it.toolTipText == "Install Stubs" }
        button.doClick()

        assertEquals("4.5.0", requestedVersion)
        assertNotNull(button.icon)
        assertEquals("Install Stubs", button.accessibleContext.accessibleName)
    }

    fun testWorkspaceButtonEmitsSaveIntentAndReflectsEnabledState() {
        val view = BlenderToolWindowView(project)
        var saveRequested = false
        view.onSaveWorkspaceConfigRequested = { saveRequested = true }
        view.render(state(blenderPath = "/Applications/Blender.app"))

        val saveButton = descendantsOf(view.component).filterIsInstance<JButton>().single { it.text == "Save Workspace Configuration" }
        saveButton.doClick()
        view.render(state(blenderPath = "/Applications/Blender.app", workspaceConfigEnabled = true))

        assertTrue(saveRequested)
        assertEquals("Update Workspace Configuration", saveButton.text)
        assertNotNull(saveButton.toolTipText)
    }

    private fun state(
        blenderPath: String,
        installs: List<PluginConfig.BlendInstallInfo> = listOf(install(name = "Blender 4.5", path = "/Applications/Blender.app")),
        workspaceConfigEnabled: Boolean = false,
        blendFileToOpen: String = "",
    ): BlenderToolWindowState {
        return BlenderToolWindowState(
            blenderPath = blenderPath,
            detectedBlenderInstalls = installs,
            addonSymlinkName = "example_addon",
            sourceFolder = "src",
            runArguments = "--background",
            blenderLogLevel = BlenderLogLevel.DEBUG,
            reloadOnSave = true,
            justMyCode = true,
            workspaceConfigEnabled = workspaceConfigEnabled,
            environmentVariables = mapOf("EXAMPLE" to "value"),
            scriptDirectories = listOf("/project/scripts"),
            blendFileToOpen = blendFileToOpen,
        )
    }

    private fun install(name: String, path: String): PluginConfig.BlendInstallInfo {
        return PluginConfig.BlendInstallInfo(name = name, version = "4.5.0", path = path)
    }

    private fun descendantsOf(container: Container): List<java.awt.Component> =
        container.components.flatMap { component ->
            listOf(component) + if (component is Container) descendantsOf(component) else emptyList()
        }
}
