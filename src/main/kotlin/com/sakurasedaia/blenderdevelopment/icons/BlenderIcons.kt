package com.sakurasedaia.blenderdevelopment.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object BlenderIcons {
    private val vector: (String) -> String = { name: String -> "/images/$name.svg"}
    
    @JvmField
    val Blender: Icon = IconLoader.getIcon(vector("blenderGray"), BlenderIcons::class.java)
    
}