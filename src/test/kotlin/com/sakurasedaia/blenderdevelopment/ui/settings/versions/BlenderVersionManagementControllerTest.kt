package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.IconBundle
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.awt.Container
import java.nio.file.Path
import javax.swing.JButton

internal class BlenderVersionManagementControllerTest : BasePlatformTestCase() {
    fun testSelectionAndCompatibilityDriveActionState() {
        val view = BlenderVersionManagementView()
        val installable = BlenderVersions.LIST[0]
        val incompatible = BlenderVersions.LIST[1]
        val installed = BlenderVersions.LIST[2]
        val controller = controller(view, isCompatible = { it == installable.blMajorMinor })
        controller.reset(
            versions = listOf(installable, incompatible, installed),
            installs = listOf(installInfo(installed)),
            lastRefreshedEpochMillis = 0,
        )
        val table = table(view)

        assertTrue(installButton(view).isEnabled)
        assertFalse(deleteButton(view).isEnabled)

        table.setRowSelectionInterval(1, 1)
        assertFalse(installButton(view).isEnabled)
        assertFalse(deleteButton(view).isEnabled)

        table.setRowSelectionInterval(2, 2)
        assertFalse(installButton(view).isEnabled)
        assertTrue(deleteButton(view).isEnabled)
    }

    fun testInstallFailureRestoresStateAndPreventsConflictingOperations() {
        val view = BlenderVersionManagementView()
        val version = BlenderVersions.LIST.first()
        var installCompletion: ((Result<Path>) -> Unit)? = null
        var refreshCount = 0
        val controller =
            controller(
                view = view,
                refreshVersions = { refreshCount++ },
                installVersion = { _, completion -> installCompletion = completion },
                isCompatible = { true },
            )
        controller.reset(listOf(version), emptyList(), 0)

        installButton(view).doClick()
        textButton(view, "ui.settings.group.versions.refresh.button").doClick()

        assertFalse(table(view).isEnabled)
        assertFalse(installButton(view).isEnabled)
        assertFalse(scanButton(view).isEnabled)
        assertFalse(textButton(view, "ui.settings.group.versions.refresh.button").isEnabled)
        assertFalse(textButton(view, "ui.settings.group.versions.clear-cache.button").isEnabled)
        assertEquals(0, refreshCount)

        installCompletion!!(Result.failure(IllegalStateException("install failed")))

        assertTrue(table(view).isEnabled)
        assertTrue(installButton(view).isEnabled)
        assertTrue(scanButton(view).isEnabled)
        assertTrue(textButton(view, "ui.settings.group.versions.refresh.button").isEnabled)
        assertTrue(textButton(view, "ui.settings.group.versions.clear-cache.button").isEnabled)
    }

    fun testSuccessfulInstallTargetsRowBeforeAuthoritativeScan() {
        val view = BlenderVersionManagementView()
        val version = BlenderVersions.LIST.first()
        var installCompletion: ((Result<Path>) -> Unit)? = null
        var scanCompletion: ((Result<List<PluginConfig.BlendInstallInfo>>) -> Unit)? = null
        val controller =
            controller(
                view = view,
                scanInstallations = { completion -> scanCompletion = completion },
                installVersion = { _, completion -> installCompletion = completion },
                isCompatible = { true },
            )
        controller.reset(listOf(version), emptyList(), 0)

        installButton(view).doClick()
        installCompletion!!(Result.success(Path.of("/managed/blender")))

        assertNotNull(scanCompletion)
        assertEquals(
            MessageBundle.message("ui.settings.group.versions.status.installed", version.blVersion),
            table(view).getValueAt(0, 2),
        )
        assertFalse(table(view).isEnabled)

        scanCompletion!!(Result.success(emptyList()))

        assertEquals(MessageBundle.message("ui.settings.group.versions.status.not-detected"), table(view).getValueAt(0, 2))
        assertTrue(table(view).isEnabled)
    }

    fun testDeleteFailureRecoversAndSuccessStartsScan() {
        val view = BlenderVersionManagementView()
        val version = BlenderVersions.LIST.first()
        var deleteCompletion: ((Result<Boolean>) -> Unit)? = null
        var scanCount = 0
        val controller =
            controller(
                view = view,
                scanInstallations = { scanCount++ },
                deleteVersion = { _, completion -> deleteCompletion = completion },
            )
        controller.reset(listOf(version), listOf(installInfo(version)), 0)

        deleteButton(view).doClick()
        deleteCompletion!!(Result.failure(IllegalStateException("delete failed")))
        assertTrue(deleteButton(view).isEnabled)

        deleteButton(view).doClick()
        deleteCompletion(Result.success(true))

        assertEquals(1, scanCount)
        assertFalse(table(view).isEnabled)
    }

    fun testResetAndDisposalRejectLateRefreshResults() {
        val view = BlenderVersionManagementView()
        val original = BlenderVersions.LIST.first()
        val replacement = BlenderVersions.LIST.last()
        var refreshCompletion: ((Result<List<BlenderVersion>>) -> Unit)? = null
        var refreshCount = 0
        val controller =
            controller(
                view = view,
                refreshVersions = { completion ->
                    refreshCount++
                    refreshCompletion = completion
                },
            )
        controller.reset(listOf(original), emptyList(), 0)

        textButton(view, "ui.settings.group.versions.refresh.button").doClick()
        controller.reset(listOf(replacement), emptyList(), 0)
        refreshCompletion!!(Result.success(listOf(original)))

        assertEquals(replacement.blVersion, table(view).getValueAt(0, 0))

        textButton(view, "ui.settings.group.versions.refresh.button").doClick()
        controller.dispose()
        refreshCompletion(Result.success(listOf(original)))
        textButton(view, "ui.settings.group.versions.refresh.button").doClick()

        assertEquals(replacement.blVersion, table(view).getValueAt(0, 0))
        assertEquals(2, refreshCount)
    }

    fun testRefreshScanAndClearCacheUpdateAuthoritativeState() {
        val view = BlenderVersionManagementView()
        val cachedVersion = BlenderVersions.LIST.last()
        val builtInVersion = BlenderVersions.LIST.first()
        var refreshCompletion: ((Result<List<BlenderVersion>>) -> Unit)? = null
        var scanCompletion: ((Result<List<PluginConfig.BlendInstallInfo>>) -> Unit)? = null
        var clearCount = 0
        val controller =
            controller(
                view = view,
                refreshVersions = { refreshCompletion = it },
                scanInstallations = { scanCompletion = it },
                clearVersionCache = {
                    clearCount++
                    Result.success(Unit)
                },
                lastRefreshedEpochMillis = { 1_700_000_000_000L },
            )
        controller.reset(listOf(cachedVersion), emptyList(), 0)

        textButton(view, "ui.settings.group.versions.refresh.button").doClick()
        refreshCompletion!!(Result.success(listOf(builtInVersion)))
        assertEquals(builtInVersion.blVersion, table(view).getValueAt(0, 0))

        scanButton(view).doClick()
        scanCompletion!!(Result.success(listOf(installInfo(builtInVersion))))
        assertEquals(
            MessageBundle.message("ui.settings.group.versions.status.installed", builtInVersion.blVersion),
            table(view).getValueAt(0, 2),
        )

        textButton(view, "ui.settings.group.versions.clear-cache.button").doClick()
        assertEquals(1, clearCount)
        assertEquals(BlenderVersions.LIST.size, table(view).rowCount)
    }

    fun testRefreshScanAndClearCacheFailuresRestoreIdleState() {
        val view = BlenderVersionManagementView()
        val version = BlenderVersions.LIST.first()
        var refreshCompletion: ((Result<List<BlenderVersion>>) -> Unit)? = null
        var scanCompletion: ((Result<List<PluginConfig.BlendInstallInfo>>) -> Unit)? = null
        var clearResult: Result<Unit> = Result.failure(IllegalStateException("clear failed"))
        val controller =
            controller(
                view = view,
                refreshVersions = { refreshCompletion = it },
                scanInstallations = { scanCompletion = it },
                clearVersionCache = { clearResult },
                isCompatible = { true },
            )
        controller.reset(listOf(version), emptyList(), 0)

        textButton(view, "ui.settings.group.versions.refresh.button").doClick()
        refreshCompletion!!(Result.failure(IllegalStateException("refresh failed")))
        assertTrue(table(view).isEnabled)
        assertTrue(installButton(view).isEnabled)

        scanButton(view).doClick()
        scanCompletion!!(Result.failure(IllegalStateException("scan failed")))
        assertTrue(table(view).isEnabled)
        assertTrue(installButton(view).isEnabled)
        assertTrue(scanButton(view).isEnabled)
        assertTrue(textButton(view, "ui.settings.group.versions.refresh.button").isEnabled)
        assertTrue(textButton(view, "ui.settings.group.versions.clear-cache.button").isEnabled)

        textButton(view, "ui.settings.group.versions.clear-cache.button").doClick()
        assertTrue(table(view).isEnabled)
        assertTrue(installButton(view).isEnabled)

        clearResult = Result.success(Unit)
        textButton(view, "ui.settings.group.versions.clear-cache.button").doClick()
        assertEquals(BlenderVersions.LIST.size, table(view).rowCount)
    }

    private fun controller(
        view: BlenderVersionManagementView,
        refreshVersions: (((Result<List<BlenderVersion>>) -> Unit) -> Unit) = {},
        scanInstallations: (((Result<List<PluginConfig.BlendInstallInfo>>) -> Unit) -> Unit) = {},
        clearVersionCache: () -> Result<Unit> = { Result.success(Unit) },
        installVersion: (BlenderVersion, (Result<Path>) -> Unit) -> Unit = { _, _ -> },
        deleteVersion: (BlenderVersion, (Result<Boolean>) -> Unit) -> Unit = { _, _ -> },
        lastRefreshedEpochMillis: () -> Long = { 0 },
        isCompatible: (String) -> Boolean = { true },
    ): BlenderVersionManagementController =
        BlenderVersionManagementController(
            view = view,
            refreshVersions = refreshVersions,
            scanInstallations = scanInstallations,
            clearVersionCache = clearVersionCache,
            installVersion = installVersion,
            deleteVersion = deleteVersion,
            lastRefreshedEpochMillis = lastRefreshedEpochMillis,
            isCompatible = isCompatible,
        )

    private fun installInfo(version: BlenderVersion): PluginConfig.BlendInstallInfo =
        PluginConfig.BlendInstallInfo(version = version.blVersion)

    private fun table(view: BlenderVersionManagementView): JBTable = descendantsOf(view.component()).filterIsInstance<JBTable>().single()

    private fun installButton(view: BlenderVersionManagementView): JButton = actionButton(view, "ui.settings.group.versions.install.button")

    private fun deleteButton(view: BlenderVersionManagementView): JButton = actionButton(view, "ui.settings.group.versions.delete.button")

    private fun actionButton(view: BlenderVersionManagementView, messageKey: String): JButton {
        val actionName = MessageBundle.message(messageKey)
        return descendantsOf(view.component()).filterIsInstance<JButton>().single { it.toolTipText == actionName }
    }

    private fun scanButton(view: BlenderVersionManagementView): JButton =
        descendantsOf(view.component()).filterIsInstance<JButton>().single { it.icon == IconBundle.Scan }

    private fun textButton(view: BlenderVersionManagementView, messageKey: String): JButton {
        val buttonText = MessageBundle.message(messageKey)
        return descendantsOf(view.component()).filterIsInstance<JButton>().single { it.text == buttonText }
    }

    private fun descendantsOf(container: Container): List<java.awt.Component> =
        container.components.flatMap { component ->
            listOf(component) + if (component is Container) descendantsOf(component) else emptyList()
        }
}
