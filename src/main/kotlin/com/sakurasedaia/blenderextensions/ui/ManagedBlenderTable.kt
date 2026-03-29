package com.sakurasedaia.blenderextensions.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderVersions
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class ManagedBlenderTable(private val project: Project) : JBTable() {
    private val downloader = BlenderDownloader.getInstance(project)
    private val tableModel = ManagedBlenderTableModel()

    init {
        model = tableModel
        autoResizeMode = AUTO_RESIZE_LAST_COLUMN
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        columnModel.getColumn(0).preferredWidth = 100
        columnModel.getColumn(1).preferredWidth = 400

        preferredViewportSize = java.awt.Dimension(-1, getRowHeight() * 5)
    }

    fun refresh() {
        tableModel.refresh()
    }

    fun getSelectedVersion(): String? {
        val row = selectedRow
        if (row < 0) return null
        return tableModel.getVersionAt(row)
    }

    fun containsVersion(version: String): Boolean {
        for (i in 0 until tableModel.rowCount) {
            if (tableModel.getVersionAt(i) == version) return true
        }
        return false
    }

    fun isSelectedVersionDownloaded(): Boolean {
        val version = getSelectedVersion() ?: return false
        return downloader.isDownloaded(version)
    }

    private inner class ManagedBlenderTableModel : AbstractTableModel() {
        private val columnNames = arrayOf(
            LangManager.message("toolwindow.table.column.version"),
            LangManager.message("toolwindow.managed.table.column.status")
        )
        private val versions = BlenderVersions.SUPPORTED_VERSIONS

        override fun getRowCount(): Int = versions.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val version = versions[rowIndex].majorMinor
            return when (columnIndex) {
                0 -> listOf("Blender", version).joinToString(" ")
                1 -> if (downloader.isDownloaded(version))
                    downloader.getVersionDirectory(version).toAbsolutePath().toString()
                else
                    LangManager.message("toolwindow.managed.status.not.installed")
                else -> ""
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        fun refresh() {
            downloader.clearCache()
            fireTableDataChanged()
        }
        
        fun getVersionAt(row: Int) = versions[row].majorMinor
    }
}
