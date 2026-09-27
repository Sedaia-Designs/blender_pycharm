package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.sakurasedaia.blenderdevelopment.ui.settings.versions.BlenderVersionManagementView
import java.awt.Container
import javax.swing.ScrollPaneConstants

internal class BlenderSettingsViewTest : BasePlatformTestCase() {
    fun testRenderAndReadFormRoundTripEveryField() {
        val view = createView()
        val form = form()

        view.renderForm(form)

        assertEquals(form, view.readForm())
    }

    fun testUsesVerticalOnlyScrollingAndEmbedsVersionComponent() {
        val versionView = BlenderVersionManagementView()
        val view = createView(versionView)
        val scrollPane = view.component() as JBScrollPane

        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, scrollPane.verticalScrollBarPolicy)
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)
        assertTrue(descendantsOf(scrollPane).contains(versionView.component()))
    }

    private fun form(): BlenderSettingsForm =
        BlenderSettingsForm(
            blenderInstallPath = "/blender",
            codeCompletionPath = "/completion",
            logPath = "/logs",
            downloadPath = "/downloads",
            clearDownloadsAfterInstall = false,
            minimumBlenderVersion = "4.5",
            globalEnvironmentVariables = mapOf("BLENDER_USER_SCRIPTS" to "/scripts"),
        )

    private fun createView(versionView: BlenderVersionManagementView = BlenderVersionManagementView()): BlenderSettingsView =
        BlenderSettingsView(versionView)

    private fun descendantsOf(container: Container): List<java.awt.Component> =
        container.components.flatMap { component ->
            listOf(component) + if (component is Container) descendantsOf(component) else emptyList()
        }
}
