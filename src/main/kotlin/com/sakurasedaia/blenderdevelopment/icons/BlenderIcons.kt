package com.sakurasedaia.blenderdevelopment.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object BlenderIcons {
    private val vector: (String) -> String = { name: String -> "/images/$name.svg"}
    
    @JvmField
    val Blender: Icon = IconLoader.getIcon(vector("blenderGray"), BlenderIcons::class.java)
    
    @JvmField
    val BlenderColor: Icon = IconLoader.getIcon(vector("blenderColor"), BlenderIcons::class.java)
    
    @JvmField
    val PythonIcon: Icon = IconLoader.getIcon(vector("pythonFile"), BlenderIcons::class.java)
}