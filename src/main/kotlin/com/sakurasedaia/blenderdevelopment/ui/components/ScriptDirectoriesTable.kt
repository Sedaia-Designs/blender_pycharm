/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.ui.components

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractCellEditor
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/** Table-based editor for script directory paths with per-row browse controls. */
internal class ScriptDirectoriesTable(private val project: Project) {
    private companion object {
        const val PATH_COLUMN_INDEX = 0
        const val BROWSE_COLUMN_INDEX = 1
    }

    private val browseLabel = MessageBundle.message("ui.common.browse")

    private val model = object : DefaultTableModel(
        arrayOf(
            MessageBundle.message("ui.common.script.directories.path"),
            ""
        ),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }

    private val table = JBTable(model).apply {
        fillsViewportHeight = true
    }
    private var isApplyingState = false
    private var onChange: ((List<String>) -> Unit)? = null

    private val browseButtonRenderer = TableCellRenderer { _, _, _, _, _, _ ->
        JButton(browseLabel)
    }

    private val browseButtonEditor = object : AbstractCellEditor(), TableCellEditor {
        private val button = JButton(browseLabel)
        private var editingViewRow: Int = -1

        init {
            button.addActionListener(::onBrowseClicked)
        }
        
        
        private fun onBrowseClicked(event: ActionEvent) {
            val selected = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project,
                null
            )
            if (selected != null && editingViewRow >= 0) {
                val modelRow = table.convertRowIndexToModel(editingViewRow)
                if (modelRow in 0 until model.rowCount) {
                    model.setValueAt(selected.path, modelRow, PATH_COLUMN_INDEX)
                }
            }
            fireEditingStopped()
        }

        override fun getCellEditorValue(): Any = browseLabel

        override fun getTableCellEditorComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int
        ): Component {
            editingViewRow = row
            return button
        }
    }

    private val root: JComponent = ToolbarDecorator.createDecorator(table)
        .setAddAction {
            model.addRow(arrayOf("", browseLabel))
            val lastRow = model.rowCount - 1
            if (lastRow >= 0) {
                table.selectionModel.setSelectionInterval(lastRow, lastRow)
                table.editCellAt(lastRow, PATH_COLUMN_INDEX)
            }
        }
        .setRemoveAction {
            if (table.isEditing) {
                table.cellEditor?.stopCellEditing()
            }
            table.selectedRows
                .sortedDescending()
                .forEach { selectedRow ->
                    val modelRow = table.convertRowIndexToModel(selectedRow)
                    if (modelRow in 0 until model.rowCount) {
                        model.removeRow(modelRow)
                    }
                }
        }
        .setRemoveActionUpdater { table.selectedRowCount > 0 }
        .setPreferredSize(Dimension(-1, 160))
        .createPanel()
        .also {
            val browseColumn = table.columnModel.getColumn(BROWSE_COLUMN_INDEX)
            browseColumn.cellRenderer = browseButtonRenderer
            browseColumn.cellEditor = browseButtonEditor
            browseColumn.maxWidth = 110
            browseColumn.minWidth = 90

            table.columnModel.getColumn(PATH_COLUMN_INDEX).cellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    return super.getTableCellRendererComponent(
                        table,
                        value?.toString().orEmpty(),
                        isSelected,
                        hasFocus,
                        row,
                        column
                    )
                }
            }

            model.addTableModelListener {
              if (!isApplyingState) {
                onChange?.invoke(readDirectories())
              }
            }
        }

    fun component(): JComponent = root

    fun setOnChangeListener(listener: (List<String>) -> Unit) {
        onChange = listener
    }

    fun setDirectories(directories: List<String>) {
        isApplyingState = true
        model.rowCount = 0
        directories.forEach { directory ->
            model.addRow(arrayOf(directory, browseLabel))
        }
        isApplyingState = false
    }

    fun getDirectories(): List<String> {
        if (table.isEditing) {
            table.cellEditor?.stopCellEditing()
        }

        return readDirectories()
    }

    private fun readDirectories(): List<String> {
        return (0 until model.rowCount)
            .map { row -> model.getValueAt(row, PATH_COLUMN_INDEX)?.toString()?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
    }
}
