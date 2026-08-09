package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.awt.Container
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.ListSelectionModel

internal class BlenderVersionManagementViewTest : BasePlatformTestCase() {
  fun testRenderCompletelyAndIdempotentlyAppliesState() {
    val view = BlenderVersionManagementView()
    val versions = BlenderVersions.LIST.take(2)
    val rows = versions.map(::row)
    var selectionIntentCount = 0
    view.setOnSelectionChanged { selectionIntentCount++ }
    val state = state(
      rows = rows,
      selectedVersion = versions.last(),
      lastRefreshedEpochMillis = 1_700_000_000_000L,
      isTableEnabled = false,
      isInstallEnabled = true,
      isDeleteEnabled = false,
    )

    view.render(state)
    view.render(state)

    val table = table(view)
    assertEquals(2, table.rowCount)
    assertEquals(versions.last().blVersion, table.getValueAt(table.selectedRow, 0))
    assertFalse(table.isEnabled)
    assertTrue(actionButton(view, "ui.settings.group.versions.install.button").isEnabled)
    assertFalse(actionButton(view, "ui.settings.group.versions.delete.button").isEnabled)
    assertEquals(0, selectionIntentCount)
    assertEquals(
      MessageBundle.message(
        "ui.settings.group.versions.last-refreshed.value",
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
          .withZone(ZoneId.systemDefault())
          .format(Instant.ofEpochMilli(state.lastRefreshedEpochMillis)),
      ),
      refreshedLabel(view).text,
    )
  }

  fun testRenderShowsRefreshingStateAndClearsMissingSelection() {
    val view = BlenderVersionManagementView()
    view.render(
      state(
        rows = listOf(row(BlenderVersions.LIST.first())),
        selectedVersion = BlenderVersions.LIST.last(),
        operation = BlenderVersionManagementState.Operation.REFRESHING,
      ),
    )

    assertEquals(-1, table(view).selectedRow)
    assertEquals(MessageBundle.message("ui.settings.group.versions.refreshing"), refreshedLabel(view).text)
  }

  fun testSelectionAndButtonsEmitSemanticIntents() {
    val view = BlenderVersionManagementView()
    val version = BlenderVersions.LIST.first()
    var selectedVersion: BlenderVersion? = null
    var installedVersion: BlenderVersion? = null
    var deletedVersion: BlenderVersion? = null
    var refreshCount = 0
    var scanCount = 0
    var clearCacheCount = 0
    view.setOnSelectionChanged { selectedVersion = it }
    view.setOnInstallRequested { installedVersion = it }
    view.setOnDeleteRequested { deletedVersion = it }
    view.setOnRefreshRequested { refreshCount++ }
    view.setOnScanRequested { scanCount++ }
    view.setOnClearCacheRequested { clearCacheCount++ }
    view.render(
      state(
        rows = listOf(row(version)),
        isInstallEnabled = true,
        isDeleteEnabled = true,
      ),
    )

    table(view).setRowSelectionInterval(0, 0)
    actionButton(view, "ui.settings.group.versions.install.button").doClick()
    actionButton(view, "ui.settings.group.versions.delete.button").doClick()
    textButton(view, "ui.settings.group.versions.refresh.button").doClick()
    textButton(view, "ui.settings.group.versions.scan.button").doClick()
    textButton(view, "ui.settings.group.versions.clear-cache.button").doClick()

    assertSame(version, selectedVersion)
    assertSame(version, installedVersion)
    assertSame(version, deletedVersion)
    assertEquals(1, refreshCount)
    assertEquals(1, scanCount)
    assertEquals(1, clearCacheCount)
  }

  fun testClearCallbacksStopsSemanticIntentDelivery() {
    val view = BlenderVersionManagementView()
    var callbackCount = 0
    view.setOnRefreshRequested { callbackCount++ }
    view.clearCallbacks()

    textButton(view, "ui.settings.group.versions.refresh.button").doClick()

    assertEquals(0, callbackCount)
  }

  fun testTableAndIconButtonsPreservePresentationAndAccessibility() {
    val view = BlenderVersionManagementView()
    val table = table(view)

    assertEquals(ListSelectionModel.SINGLE_SELECTION, table.selectionModel.selectionMode)
    assertFalse(table.showHorizontalLines)
    assertFalse(table.showVerticalLines)
    assertFalse(table.tableHeader.reorderingAllowed)
    assertEquals(MessageBundle.message("ui.settings.group.versions.empty"), table.emptyText.text)

    listOf(
      "ui.settings.group.versions.install.button",
      "ui.settings.group.versions.delete.button",
    ).forEach { messageKey ->
      val actionName = MessageBundle.message(messageKey)
      val button = actionButton(view, messageKey)
      assertTrue(button.text.isNullOrEmpty())
      assertNotNull(button.icon)
      assertEquals(actionName, button.toolTipText)
      assertEquals(actionName, button.accessibleContext.accessibleName)
    }
  }

  private fun state(
    rows: List<BlenderVersionSettingsRow> = emptyList(),
    selectedVersion: BlenderVersion? = null,
    lastRefreshedEpochMillis: Long = 0,
    operation: BlenderVersionManagementState.Operation? = null,
    isTableEnabled: Boolean = true,
    isInstallEnabled: Boolean = false,
    isDeleteEnabled: Boolean = false,
  ): BlenderVersionManagementState = BlenderVersionManagementState(
    rows = rows,
    selectedVersion = selectedVersion,
    lastRefreshedEpochMillis = lastRefreshedEpochMillis,
    operation = operation,
    isTableEnabled = isTableEnabled,
    isInstallEnabled = isInstallEnabled,
    isDeleteEnabled = isDeleteEnabled,
  )

  private fun row(version: BlenderVersion): BlenderVersionSettingsRow = BlenderVersionSettingsRow(
    version = version,
    pythonVersion = version.pyVersion,
    installStatus = MessageBundle.message("ui.settings.group.versions.status.not-detected"),
    isInstalled = false,
  )

  private fun table(view: BlenderVersionManagementView): JBTable =
    descendantsOf(view.component()).filterIsInstance<JBTable>().single()

  private fun refreshedLabel(view: BlenderVersionManagementView): JBLabel =
    descendantsOf(view.component()).filterIsInstance<JBLabel>().single {
      it.text.startsWith("Last refreshed:") || it.text == MessageBundle.message("ui.settings.group.versions.refreshing")
    }

  private fun actionButton(view: BlenderVersionManagementView, messageKey: String): JButton {
    val actionName = MessageBundle.message(messageKey)
    return descendantsOf(view.component()).filterIsInstance<JButton>().single { it.toolTipText == actionName }
  }

  private fun textButton(view: BlenderVersionManagementView, messageKey: String): JButton {
    val buttonText = MessageBundle.message(messageKey)
    return descendantsOf(view.component()).filterIsInstance<JButton>().single { it.text == buttonText }
  }

  private fun descendantsOf(container: Container): List<java.awt.Component> =
    container.components.flatMap { component ->
      listOf(component) + if (component is Container) descendantsOf(component) else emptyList()
    }
}
