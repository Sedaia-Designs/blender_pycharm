@file:Suppress("SameParameterValue")

package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.awt.Container
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.swing.JButton

internal class BlenderSettingsComponentTest : BasePlatformTestCase() {
    fun testComposesSettingsAndVersionFeaturesBehindLifecycleApi() {
        val config =
            PluginConfig.getInstance().apply {
                setBlenderInstallPath("/original/blender")
                setDetectedBlenderInstalls(emptyList())
            }
        val component =
            BlenderSettingsComponent(
                config = config,
                operations = operations(),
            )

        component.reset()

        assertFalse(component.isModified())
        assertEquals(BlenderVersions.LIST.size, versionTable(component).rowCount)

        pathFields(component).first().text = "/applied/blender"
        assertTrue(component.isModified())

        component.apply()

        assertEquals("/applied/blender", config.getBlenderInstallPath())
        assertFalse(component.isModified())
    }

    fun testDisposalIsIdempotentAndClearsOperationCallbacks() {
        var refreshCount = 0
        val component =
            BlenderSettingsComponent(
                config = PluginConfig.getInstance(),
                operations =
                    operations(
                        refreshVersionCache = {
                            refreshCount++
                            BlenderVersions.LIST
                        }
                    ),
            )
        component.reset()

        component.dispose()
        component.dispose()
        textButton(component, "ui.settings.group.versions.refresh.button").apply {
            isEnabled = true
            doClick()
        }

        assertEquals(0, refreshCount)
        assertFalse(component.isModified())
    }

    fun testPendingOperationCannotRenderAfterDisposal() {
        var refreshAction: (() -> Unit)? = null
        val component =
            BlenderSettingsComponent(
                config = PluginConfig.getInstance(),
                operations =
                    operations(
                        executeInBackground = { action ->
                            refreshAction = action
                            CompletableFuture<Unit>()
                        }
                    ),
            )
        component.reset()
        val table = versionTable(component)
        val originalFirstVersion = table.getValueAt(0, 0)

        textButton(component, "ui.settings.group.versions.refresh.button").apply {
            isEnabled = true
            doClick()
        }
        component.dispose()
        refreshAction!!()

        assertEquals(originalFirstVersion, table.getValueAt(0, 0))
    }

    private fun operations(
        refreshVersionCache: () -> List<BlenderVersion> = { BlenderVersions.LIST },
        executeInBackground: (() -> Unit) -> java.util.concurrent.Future<*> = {
            it()
            CompletableFuture.completedFuture(Unit)
        },
    ): BlenderSettingsOperations =
        BlenderSettingsOperations(
            BlenderSettingsOperations.Dependencies(
                refreshVersionCache = refreshVersionCache,
                scanInstallations = { emptyList() },
                clearVersionCache = {},
                installVersion = { CompletableFuture.completedFuture(Path.of("/managed/blender")) },
                deleteVersion = { CompletableFuture.completedFuture(true) },
                markVersionUpdateChecked = {},
                log = {},
                sendInfo = {},
                sendError = { _, _ -> },
                executeInBackground = executeInBackground,
                invokeLater = { _, action -> action() },
                currentModalityState = ModalityState::any,
            )
        )

    private fun pathFields(component: BlenderSettingsComponent): List<TextFieldWithBrowseButton> =
        descendantsOf(component.component()).filterIsInstance<TextFieldWithBrowseButton>()

    private fun versionTable(component: BlenderSettingsComponent): JBTable =
        descendantsOf(component.component()).filterIsInstance<JBTable>().single { it.columnCount == 3 }

    private fun textButton(component: BlenderSettingsComponent, messageKey: String): JButton {
        val buttonText = MessageBundle.message(messageKey)
        return descendantsOf(component.component()).filterIsInstance<JButton>().single { it.text == buttonText }
    }

    private fun descendantsOf(container: Container): List<java.awt.Component> =
        container.components.flatMap { child ->
            listOf(child) + if (child is Container) descendantsOf(child) else emptyList()
        }
}
