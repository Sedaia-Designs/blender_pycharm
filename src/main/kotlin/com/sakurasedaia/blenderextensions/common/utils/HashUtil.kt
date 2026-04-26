package com.sakurasedaia.blenderextensions.common.utils

import com.sakurasedaia.blenderextensions.telemetry.BlenderLogger
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream

object HashUtil {
    /**
     * Verifies if the file matches the expected SHA-256 hash.
     *
     * @param file The path to the file to verify.
     * @param expectedHash The expected SHA-256 hash in hexadecimal format.
     * @param logger Optional logger for reporting results.
     * @return true if the hash matches, false otherwise.
     */
    fun verifySha256(file: Path, expectedHash: String, logger: BlenderLogger? = null): Boolean {
        return try {
            val actualHash = calculateSha256(file)
            val matches = actualHash.equals(expectedHash, ignoreCase = true)
            
            if (matches) {
                logger?.log("SHA-256 verification successful for ${file.fileName}")
            } else {
                logger?.error("SHA-256 verification failed for ${file.fileName}. Expected: $expectedHash, Actual: $actualHash")
            }
            matches
        } catch (e: Exception) {
            logger?.error("Failed to verify SHA-256 for ${file.fileName}: ${e.message}", e)
            false
        }
    }

    /**
     * Calculates the SHA-256 hash of a file.
     *
     * @param file The path to the file.
     * @return The SHA-256 hash in hexadecimal format.
     */
    fun calculateSha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return bytesToHex(digest.digest())
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
