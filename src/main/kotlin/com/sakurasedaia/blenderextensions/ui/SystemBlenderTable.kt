package com.sakurasedaia.blenderextensions.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderInstallation
import com.sakurasedaia.blenderextensions.blender.BlenderScanner
import com.sakurasedaia.blenderextensions.blender.BlenderService
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
import java.awt.Component
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class SystemBlenderTable(private val project: Project) : JBTable() {
    private val tableModel = SystemBlenderTableModel()

    init {
        model = tableModel
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        
        columnModel.getColumn(0).maxWidth = 60
        columnModel.getColumn(0).preferredWidth = 40
        columnModel.getColumn(1).preferredWidth = 100

        preferredViewportSize = java.awt.Dimension(-1, getRowHeight() * 2)

        columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                if (value is Icon) {
                    icon = value
                    text = ""
                    horizontalAlignment = SwingConstants.CENTER
                    
                    val inst = tableModel.getInstallationAt(row)
                    toolTipText = if (inst.isCustom) LangManager.message("toolwindow.system.table.status.manual.tooltip") else LangManager.message("toolwindow.system.table.status.auto.tooltip")
                }
                return component
            }
        }
    }

    fun getSelectedInstallation(): BlenderInstallation? {
        val selectedRow = selectedRow
        if (selectedRow != -1) {
            return tableModel.getInstallationAt(convertRowIndexToModel(selectedRow))
        }
        return null
    }

    fun containsVersion(version: String): Boolean {
        for (i in 0 until tableModel.rowCount) {
            val inst = tableModel.getInstallationAt(i)
            if (inst.name.contains(version) || inst.path.contains(version)) return true
        }
        return false
    }

    fun refresh() {
        tableModel.refresh()
    }

    private inner class SystemBlenderTableModel : AbstractTableModel() {
        private val columnNames = arrayOf(
            LangManager.message("toolwindow.system.table.column.status"),
            LangManager.message("toolwindow.table.column.version")
        )
        private var installations = listOf<BlenderInstallation>()

        init {
            refresh()
        }

        override fun getRowCount(): Int = installations.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val inst = installations[rowIndex]
            return when (columnIndex) {
                0 -> if (inst.isCustom) BlenderIcons.Custom else BlenderIcons.System
                1 -> inst.name
                else -> ""
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            val inst = installations[rowIndex]
            return (columnIndex == 1 && inst.isCustom)
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 1) {
                val inst = installations[rowIndex]
                if (inst.isCustom && inst.originPath != null) {
                    val settings = BlenderSettings.getInstance(project)
                    settings.addCustomBlenderPath(inst.originPath, aValue.toString())
                    refresh()
                }
            }
        }

        fun refresh() {
            val customPaths = BlenderSettings.getInstance(project).getCustomBlenderPaths()
            val newInstallations = BlenderScanner.getCachedInstallations() ?: emptyList()
            installations = newInstallations
            fireTableDataChanged()
        }

        fun getInstallationAt(row: Int) = installations[row]
    }
}
