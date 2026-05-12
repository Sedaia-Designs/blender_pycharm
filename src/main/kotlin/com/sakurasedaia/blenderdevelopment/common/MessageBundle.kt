package com.sakurasedaia.blenderdevelopment.common

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.MessageBundle"

object MessageBundle : DynamicBundle(BUNDLE){
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: String?
    ): String {
        return getMessage(key, *params)
    }
    
}