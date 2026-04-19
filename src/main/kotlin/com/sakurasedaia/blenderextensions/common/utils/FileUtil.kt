package com.sakurasedaia.blenderextensions.common.utils

import java.nio.file.Path
import kotlin.io.path.*

object FileUtil {
    fun copyDirectory(source: Path, target: Path) {
        source.walk().forEach { sourcePath ->
            val targetPath = target.resolve(source.relativize(sourcePath))
            if (sourcePath.isDirectory()) {
                targetPath.createDirectories()
            } else {
                sourcePath.copyTo(targetPath, overwrite = true)
            }
        }
    }
}
