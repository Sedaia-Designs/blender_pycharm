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

package com.sakurasedaia.blenderdevelopment.lib

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Central icon registry for the Blender plugin. */
object IconBundle {
    private val vector: (String) -> String = { name: String -> "/images/$name.svg"}
    
    @JvmField
    val Blender: Icon = IconLoader.getIcon(vector("blenderGray"), IconBundle::class.java)
    
    @JvmField
    val BlenderColor: Icon = IconLoader.getIcon(vector("blenderColor"), IconBundle::class.java)
    
    @JvmField
    val PythonIcon: Icon = IconLoader.getIcon(vector("pythonFile"), IconBundle::class.java)
}