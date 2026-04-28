package com.sakurasedaia.blenderextensions.common.utils.paths

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

private val LOG = Logger.getInstance("com.sakurasedaia.blenderextensions.common.utils.paths.FileOperations")

/**
 * High-level file and directory operations with safety checks.
 */

/**
 * Safely deletes a directory or file recursively, ensuring it's within allowed boundaries.
 */
fun safelyDeleteRecursively(path: Path, project: Project? = null): Boolean {
    if (!path.exists()) return true

    if (!isSafeToDelete(path, project)) {
        val message = "Safety refusal: Attempted to delete protected path: $path"
        if (project != null) {
            BlenderLogger.warn(project, message)
        } else {
            LOG.warn(message)
        }
        return false
    }

    return try {
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
        true
    } catch (e: IOException) {
        LOG.error("Failed to delete $path", e)
        false
    }
}

/**
 * Copies a directory recursively.
 */
fun copyDirectory(source: Path, target: Path) {
    Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val targetDir = target.resolve(source.relativize(dir))
            if (!targetDir.exists()) Files.createDirectories(targetDir)
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
            return FileVisitResult.CONTINUE
        }
    })
}

/**
 * Ensures a file is executable on Unix-like systems.
 */
fun makeExecutable(path: Path) {
    if (!path.exists() || path.isDirectory()) return
    try {
        val perms = Files.getPosixFilePermissions(path).toMutableSet()
        perms.add(PosixFilePermission.OWNER_EXECUTE)
        perms.add(PosixFilePermission.GROUP_EXECUTE)
        perms.add(PosixFilePermission.OTHERS_EXECUTE)
        Files.setPosixFilePermissions(path, perms)
    } catch (e: UnsupportedOperationException) {
        // Not a POSIX system (e.g. Windows)
    } catch (e: Exception) {
        LOG.warn("Failed to set executable permissions on $path: ${e.message}")
    }
}
