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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil

internal class ScriptDirectoriesTableTest : BasePlatformTestCase() {
    fun testBrowseResultWhileBrowseButtonIsEditingDoesNotReenterEditorCommit() {
        val directoriesTable = ScriptDirectoriesTable(project)
        directoriesTable.setDirectories(listOf("/initial"))
        var observedDirectories = emptyList<String>()
        directoriesTable.setOnChangeListener { observedDirectories = it }
        val table = UIUtil.findComponentOfType(directoriesTable.component(), JBTable::class.java)

        assertNotNull(table)
        assertTrue(table!!.editCellAt(0, 1))

        table.model.setValueAt("/selected/from/browse", 0, 0)

        assertEquals(listOf("/selected/from/browse"), observedDirectories)
        assertTrue(table.isEditing)
    }
}
