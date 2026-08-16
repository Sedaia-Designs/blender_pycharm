package com.sakurasedaia.blenderdevelopment.ui.settings.versions

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersions
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.IconBundle
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

internal class BlenderVersionManagementView(isValidMinorVersion: (String) -> Boolean = { true }) {
  private lateinit var minimumBlenderVersion: JBTextField
  private val tableModel =
      ListTableModel<BlenderVersionSettingsRow>(
          object : ColumnInfo<BlenderVersionSettingsRow, String>(MessageBundle.message("ui.settings.group.versions.column.version")) {
            override fun valueOf(item: BlenderVersionSettingsRow): String = item.version.blVersion
          },
          object : ColumnInfo<BlenderVersionSettingsRow, String>(MessageBundle.message("ui.settings.group.versions.column.python")) {
            override fun valueOf(item: BlenderVersionSettingsRow): String = item.pythonVersion
          },
          object : ColumnInfo<BlenderVersionSettingsRow, String>(MessageBundle.message("ui.settings.group.versions.column.status")) {
            override fun valueOf(item: BlenderVersionSettingsRow): String = item.installStatus
          },
      )
  private val table =
      JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        emptyText.text = MessageBundle.message("ui.settings.group.versions.empty")
        setShowGrid(false)
        tableHeader.reorderingAllowed = false
      }
  private val lastRefreshedLabel = JBLabel(MessageBundle.message("ui.settings.group.versions.last-refreshed.never"))
  private val selectedVersionInstallPath =
      JBLabel(MessageBundle.message("ui.settings.group.versions.selected-install-path.none")).apply {
        border = JBUI.Borders.emptyBottom(12)
      }
  private val installButton =
      iconButton(
          icon = IconBundle.Install,
          actionName = MessageBundle.message("ui.settings.group.versions.install.button"),
      ) {
        selectedVersion()?.let { onInstallRequested?.invoke(it) }
      }
  private val deleteButton =
      iconButton(
          icon = IconBundle.Uninstall,
          actionName = MessageBundle.message("ui.settings.group.versions.delete.button"),
      ) {
        selectedVersion()?.let { onDeleteRequested?.invoke(it) }
      }
  private val scanInstallButton =
      iconButton(
          icon = IconBundle.Scan,
          actionName = MessageBundle.message("ui.settings.group.versions.scan.button"),
      ) {
        onScanRequested?.invoke()
      }
  private val refreshVersionCacheButton =
      iconButtonWithLabel(
          icon = IconBundle.Refresh,
          label = MessageBundle.message("ui.settings.group.versions.refresh.button"),
      ) {
        onRefreshRequested?.invoke()
      }
  private val clearVersionCacheButton =
      iconButtonWithLabel(
          icon = IconBundle.Delete,
          label = MessageBundle.message("ui.settings.group.versions.clear-cache.button"),
      ) {
        onClearCacheRequested?.invoke()
      }

  private var onSelectionChanged: ((BlenderVersion?) -> Unit)? = null
  private var onInstallRequested: ((BlenderVersion) -> Unit)? = null
  private var onDeleteRequested: ((BlenderVersion) -> Unit)? = null
  private var onRefreshRequested: (() -> Unit)? = null
  private var onScanRequested: (() -> Unit)? = null

  private var onClearCacheRequested: (() -> Unit)? = null
  private var isRendering = false

  private val root = panel {
    row {
      label(MessageBundle.message("ui.settings.group.versions.management.comment"))
    }
    row {
      cell(ScrollPaneFactory.createScrollPane(table, false)).align(AlignX.FILL).resizableColumn()
      panel {
            row {
              cell(installButton)
            }
            row {
              cell(deleteButton)
            }
            row {
              cell(scanInstallButton)
            }
          }
          .align(AlignY.TOP)
    }
    row {
      cell(
              ScrollPaneFactory.createScrollPane(selectedVersionInstallPath, true).apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
              }
          )
          .align(AlignX.FILL)
    }
    row {
      textField()
          .label(MessageBundle.message("ui.settings.group.versions.minimum-version.label"))
          .columns(8)
          .validationOnInput {
            if (isValidMinorVersion(it.text)) null
            else error(MessageBundle.message("ui.settings.group.versions.minimum-version.validation"))
          }
          .validationOnApply {
            if (isValidMinorVersion(it.text)) null
            else error(MessageBundle.message("ui.settings.group.versions.minimum-version.validation"))
          }
          .applyToComponent { minimumBlenderVersion = this }
      cell(refreshVersionCacheButton)
      cell(clearVersionCacheButton)
    }
    row {
      cell(lastRefreshedLabel)
    }
    row {
      label(MessageBundle.message("ui.settings.group.versions.management.indev.warning"))
    }
  }

  init {
    table.selectionModel.addListSelectionListener {
      if (!it.valueIsAdjusting && !isRendering) {
        onSelectionChanged?.invoke(selectedVersion())
      }
    }
  }

  fun component(): JComponent = root

  fun renderMinimumVersion(value: String) {
    minimumBlenderVersion.text = value
  }

  fun readMinimumVersion(): String = minimumBlenderVersion.text

  fun setOnSelectionChanged(callback: (BlenderVersion?) -> Unit) {
    onSelectionChanged = callback
  }

  fun setOnInstallRequested(callback: (BlenderVersion) -> Unit) {
    onInstallRequested = callback
  }

  fun setOnDeleteRequested(callback: (BlenderVersion) -> Unit) {
    onDeleteRequested = callback
  }

  fun setOnRefreshRequested(callback: () -> Unit) {
    onRefreshRequested = callback
  }

  fun setOnScanRequested(callback: () -> Unit) {
    onScanRequested = callback
  }

  fun setOnClearCacheRequested(callback: () -> Unit) {
    onClearCacheRequested = callback
  }

  fun render(state: BlenderVersionManagementState) {
    isRendering = true
    try {
      tableModel.items = state.rows
      restoreSelection(state.selectedVersion)
      table.isEnabled = state.isTableEnabled
      installButton.isEnabled = state.isInstallEnabled
      deleteButton.isEnabled = state.isDeleteEnabled
      scanInstallButton.isEnabled = state.isScanEnabled
      refreshVersionCacheButton.isEnabled = state.isRefreshVersionCacheEnabled
      clearVersionCacheButton.isEnabled = state.isClearVersionCacheEnabled
      lastRefreshedLabel.text =
          if (state.operation == BlenderVersionManagementState.Operation.REFRESHING) {
            MessageBundle.message("ui.settings.group.versions.refreshing")
          } else {
            formatLastRefreshed(state.lastRefreshedEpochMillis)
          }
      selectedVersionInstallPath.text = selectedVersionPath(state.selectedVersion)
    } finally {
      isRendering = false
    }
  }

  fun clearCallbacks() {
    onSelectionChanged = null
    onInstallRequested = null
    onDeleteRequested = null
    onRefreshRequested = null
    onScanRequested = null
    onClearCacheRequested = null
  }

  private fun restoreSelection(selectedVersion: BlenderVersion?) {
    val selectedRow =
        selectedVersion?.let { version ->
          tableModel.items.indexOfFirst { it.version.blMajorMinor == version.blMajorMinor }
        } ?: -1
    if (selectedRow >= 0) {
      table.setRowSelectionInterval(selectedRow, selectedRow)
    } else {
      table.clearSelection()
    }
  }

  private fun selectedVersion(): BlenderVersion? {
    val selectedRow = table.selectedRow
    return if (selectedRow < 0) null else tableModel.getItem(selectedRow).version
  }

  private fun selectedVersionPath(version: BlenderVersion?): String {
    val installPath = version?.let { selectedInstall ->
      PluginConfig.getInstance()
          .getDetectedBlenderInstalls()
          .firstOrNull { install ->
            BlenderVersions.normalizeVersion(install.version) == selectedInstall.blMajorMinor
          }
          ?.path
    }

    return if (installPath == null) {
      MessageBundle.message("ui.settings.group.versions.selected-install-path.selected.none", version?.blMajorMinor)
    } else {
      MessageBundle.message("ui.settings.group.versions.selected-install-path.selected.found", version.blMajorMinor, installPath)
    }
  }

  private fun formatLastRefreshed(epochMillis: Long): String {
    return if (epochMillis <= 0) {
      MessageBundle.message("ui.settings.group.versions.last-refreshed.never")
    } else {
      MessageBundle.message(
          "ui.settings.group.versions.last-refreshed.value",
          LAST_REFRESHED_FORMATTER.format(Instant.ofEpochMilli(epochMillis)),
      )
    }
  }

  private fun iconButton(
      icon: javax.swing.Icon,
      actionName: String,
      action: () -> Unit,
  ): JButton =
      JButton(icon).apply {
        val buttonSize = JBUI.size(VERSION_ACTION_BUTTON_SIZE)
        toolTipText = actionName
        accessibleContext.accessibleName = actionName
        minimumSize = buttonSize
        preferredSize = buttonSize
        maximumSize = buttonSize
        isEnabled = false
        addActionListener { action() }
      }

  private fun iconButtonWithLabel(
      icon: javax.swing.Icon,
      label: String,
      actionName: String? = null,
      action: () -> Unit,
  ): JButton =
      JButton(label, icon).apply {
        val buttonSize = JBUI.size(VERSION_ACTION_BUTTON_SIZE)
        toolTipText = actionName
        accessibleContext.accessibleName = label
        minimumSize = buttonSize
        isEnabled = false
        addActionListener { action() }
      }

  companion object {
    private const val VERSION_ACTION_BUTTON_SIZE = 28
    private val LAST_REFRESHED_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
  }
}
