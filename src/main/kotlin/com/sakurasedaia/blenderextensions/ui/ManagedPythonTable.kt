package com.sakurasedaia.blenderextensions.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.python.PythonUtil
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import kotlin.io.path.exists

class ManagedPythonTable(private val project: Project) : JBTable() {
    private val tableModel = ManagedPythonTableModel()

    init {
        model = tableModel
        autoResizeMode = AUTO_RESIZE_LAST_COLUMN
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        columnModel.getColumn(0).preferredWidth = 100
        columnModel.getColumn(1).preferredWidth = 400

        preferredViewportSize = java.awt.Dimension(-1, getRowHeight() * 5)
    }

    fun refresh() {
        tableModel.fireTableDataChanged()
    }

    fun getSelectedVersion(): String? {
        val row = selectedRow
        if (row < 0) return null
        return tableModel.getVersionAt(row)
    }

    fun isSelectedVersionDownloaded(): Boolean {
        val version = getSelectedVersion() ?: return false
        return PythonUtil.getPythonInterpreterDirectory(version, project).exists()
    }

    private inner class ManagedPythonTableModel : AbstractTableModel() {
        private val columnNames = arrayOf(
            LangManager.message("toolwindow.python.table.column.version"),
            LangManager.message("toolwindow.python.table.column.status")
        )
        // Standard Python versions we support installing
        private val versions = listOf("3.10", "3.11", "3.12", "3.13")

        override fun getRowCount(): Int = versions.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val version = versions[rowIndex]
            val path = PythonUtil.getPythonInterpreterDirectory(version, project)
            return when (columnIndex) {
                0 -> "Python $version"
                1 -> if (path.exists())
                    path.toAbsolutePath().toString()
                else
                    LangManager.message("toolwindow.python.status.not.installed")
                else -> ""
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        fun getVersionAt(row: Int) = versions[row]
    }
}
