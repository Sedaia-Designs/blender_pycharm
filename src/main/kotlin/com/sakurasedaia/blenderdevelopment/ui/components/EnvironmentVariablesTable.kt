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

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

/** Table-based editor for key/value environment variables. */
internal class EnvironmentVariablesTable {
  private val model =
      object :
          DefaultTableModel(
              arrayOf(
                  MessageBundle.message("ui.common.environment.variables.key"),
                  MessageBundle.message("ui.common.environment.variables.value"),
              ),
              0,
          ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
      }

  private val table =
      JBTable(model).apply {
        fillsViewportHeight = true
      }
  private var isApplyingState = false
  private var onChange: ((Map<String, String>) -> Unit)? = null

  private val root: JComponent =
      ToolbarDecorator.createDecorator(table)
          .setAddAction {
            model.addRow(arrayOf("", ""))
            val lastRow = model.rowCount - 1
            if (lastRow >= 0) {
              table.selectionModel.setSelectionInterval(lastRow, lastRow)
              table.editCellAt(lastRow, 0)
            }
          }
          .setRemoveAction {
            if (table.isEditing) {
              table.cellEditor?.stopCellEditing()
            }

            table.selectedRows.sortedDescending().forEach { selectedRow ->
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
            model.addTableModelListener {
              if (!isApplyingState) {
                onChange?.invoke(readVariables())
              }
            }
          }

  fun component(): JComponent = root

  fun setOnChangeListener(listener: (Map<String, String>) -> Unit) {
    onChange = listener
  }

  fun setVariables(variables: Map<String, String>) {
    isApplyingState = true
    model.rowCount = 0
    variables.toSortedMap().forEach { (key, value) ->
      model.addRow(arrayOf(key, value))
    }
    isApplyingState = false
  }

  fun getVariables(): Map<String, String> {
    if (table.isEditing) {
      table.cellEditor?.stopCellEditing()
    }

    return readVariables()
  }

  private fun readVariables(): Map<String, String> {
    val variables = linkedMapOf<String, String>()
    for (row in 0 until model.rowCount) {
      val key = model.getValueAt(row, 0)?.toString()?.trim().orEmpty()
      if (key.isEmpty()) continue
      val value = model.getValueAt(row, 1)?.toString().orEmpty()
      variables[key] = value
    }
    return variables
  }
}
