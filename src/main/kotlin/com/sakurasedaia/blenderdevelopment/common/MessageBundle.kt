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

package com.sakurasedaia.blenderdevelopment.common

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.MessageBundle"

/** Accessor for localized plugin messages from `messages/MessageBundle.properties`. */
object MessageBundle : DynamicBundle(BUNDLE){
    /**
     * Resolves a localized message by key with optional replacement parameters.
     *
     * @param key message key in `messages/MessageBundle.properties`.
     * @param params optional replacement values used by the message pattern.
     * @return resolved localized message text.
     */
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: String?
    ): String {
        return getMessage(key, *params)
    }
    
}
