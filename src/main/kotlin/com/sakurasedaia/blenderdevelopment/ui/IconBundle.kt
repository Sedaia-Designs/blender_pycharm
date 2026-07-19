package com.sakurasedaia.blenderdevelopment.ui

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