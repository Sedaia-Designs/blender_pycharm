package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.writeText

class HashUtilTest : BasePlatformTestCase() {

    @Test
    fun testCalculateSha256() {
        val tempFile = Files.createTempFile("hash-test", ".txt")
        try {
            tempFile.writeText("Hello, World!")
            // echo -n "Hello, World!" | shasum -a 256
            // dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f
            val expectedHash = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f"
            val actualHash = HashUtil.calculateSha256(tempFile)
            assertEquals(expectedHash, actualHash)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun testVerifySha256Success() {
        val tempFile = Files.createTempFile("hash-test-success", ".txt")
        try {
            tempFile.writeText("Blender PyCharm")
            // echo -n "Blender PyCharm" | shasum -a 256
            // 4a82c0c6ce82d3649fa154a9a67c36b6a42917b66db0a8aa849fd3953798187a (on macOS/Unix with default charset)
            val hash = HashUtil.calculateSha256(tempFile)
            assertTrue(HashUtil.verifySha256(tempFile, hash))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun testVerifySha256Failure() {
        val tempFile = Files.createTempFile("hash-test-failure", ".txt")
        try {
            tempFile.writeText("Blender PyCharm")
            val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"
            assertFalse(HashUtil.verifySha256(tempFile, wrongHash))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
