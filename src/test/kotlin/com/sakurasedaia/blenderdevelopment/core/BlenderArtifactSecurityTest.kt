package com.sakurasedaia.blenderdevelopment.core

import com.intellij.openapi.progress.ProcessCanceledException
import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderArtifactSecurityTest {
    private val version =
        BlenderVersion(
            installName = "Blender 4.5.8",
            blVersionList = listOf(4, 5, 8),
            compatWithOs = mapOf("windows" to listOf("x64", "arm64"), "macos" to listOf("x64", "arm64"), "linux" to listOf("x64")),
        )

    @Test
    fun supportedPlatformsMapToDistinctOfficialManifestEntries() {
        val expected = mapOf("windows" to listOf("x64", "arm64"), "macos" to listOf("x64", "arm64"), "linux" to listOf("x64"))
        expected.forEach { (platform, architectures) ->
            architectures.forEach { architecture ->
                val descriptor = BlenderArtifactDescriptor.forPlatform(version, platform, architecture)
                assertTrue(descriptor.archiveUri.toString().endsWith(descriptor.filename))
                assertTrue(descriptor.checksumUri.toString().endsWith("blender-4.5.8.sha256"))
            }
        }
        assertThrows(SecurityException::class.java) { BlenderArtifactDescriptor.forPlatform(version, "linux", "arm64") }
    }

    @Test
    fun everyConfiguredReleaseMapsOnlyItsSupportedPlatforms() {
        val releases =
            listOf(
                BlenderVersion(
                    "Blender 4.2.19",
                    listOf(4, 2, 19),
                    compatWithOs =
                        mapOf(
                            "windows" to listOf("x64"),
                            "macos" to listOf("x64", "arm64"),
                            "linux" to listOf("x64"),
                        ),
                ),
                version,
                BlenderVersion(
                    "Blender 5.2.0",
                    listOf(5, 2, 0),
                    compatWithOs =
                        mapOf(
                            "windows" to listOf("x64", "arm64"),
                            "macos" to listOf("arm64"),
                            "linux" to listOf("x64"),
                        ),
                ),
            )
        releases.forEach { release ->
            release.compatWithOs.forEach { (platform, architectures) ->
                architectures.forEach { architecture ->
                    val descriptor = BlenderArtifactDescriptor.forPlatform(release, platform, architecture)
                    assertTrue(descriptor.filename.startsWith("blender-${release.blVersion}-$platform-$architecture."))
                    assertTrue(descriptor.checksumUri.path.endsWith("blender-${release.blVersion}.sha256"))
                }
            }
        }
        assertThrows(SecurityException::class.java) { BlenderArtifactDescriptor.forPlatform(releases.first(), "windows", "arm64") }
        assertThrows(SecurityException::class.java) { BlenderArtifactDescriptor.forPlatform(releases.last(), "macos", "x64") }
    }

    @Test
    fun officialManifestSelectsOnlyExactFilenameAndRejectsMissingOrDuplicateEntries() {
        val descriptor = BlenderArtifactDescriptor.forPlatform(version, "linux", "x64")
        val digest = MessageDigest.getInstance("SHA-256").digest("archive".toByteArray())
        val hex = HexFormat.of().formatHex(digest)
        val entry = "$hex  ${descriptor.filename}"
        assertArrayEquals(digest, officialDigest("$entry\n$hex  other.tar.xz\n", descriptor.filename))
        assertTrue(officialDigest("$hex  other.tar.xz", descriptor.filename) == null)
        assertTrue(officialDigest("$entry\n$entry", descriptor.filename) == null)
        assertTrue(officialDigest("not-a-hash  ${descriptor.filename}", descriptor.filename) == null)
    }

    @Test
    fun uriPolicyRejectsInsecureHostsPathsFilenamesAndRedirects() {
        val descriptor = BlenderArtifactDescriptor.forPlatform(version, "linux", "x64")
        val name = descriptor.filename
        assertThrows(IllegalArgumentException::class.java) { descriptor.copy(filename = "$name.exe") }
        listOf(
                "http://download.blender.org/release/Blender4.5/$name",
                "https://download.blender.org.evil.example/release/Blender4.5/$name",
                "https://download.blender.org/release/Blender4.5/other.tar.xz",
                "https://download.blender.org/release/Blender4.5/%62$name",
                "https://download.blender.org/release/Blender4.5/$name?mirror=1",
            )
            .forEach { candidate ->
                assertThrows(SecurityException::class.java) { BlenderArtifactDescriptor.requireTrustedUri(URI.create(candidate), name) }
            }
        assertThrows(SecurityException::class.java) { trustedRedirect(descriptor.archiveUri, "https://evil.example/$name", name) }
        assertThrows(SecurityException::class.java) { trustedRedirect(descriptor.archiveUri, "../other/$name", name) }
        assertTrue(trustedRedirect(descriptor.archiveUri, name, name) == descriptor.archiveUri)
    }

    @Test
    fun streamedDigestRejectsTruncationAndChangedContent() {
        val archive = Files.createTempFile("blender-digest-test", ".zip")
        try {
            val content = "complete Blender archive".toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(content)
            Files.write(archive, content)
            assertTrue(verifyArchiveDigest(archive, digest))
            Files.write(archive, content.copyOf(content.size - 1))
            assertFalse(verifyArchiveDigest(archive, digest))
            Files.write(archive, "tampered".toByteArray())
            assertFalse(verifyArchiveDigest(archive, digest))
        } finally {
            Files.deleteIfExists(archive)
        }
    }

    @Test
    fun cancelledStreamRemovesPartiallyWrittenArchive() {
        val partial = Files.createTempFile("blender-cancel-test", ".part")
        var checks = 0
        assertThrows(ProcessCanceledException::class.java) {
            streamBlenderDownload(ByteArrayInputStream(ByteArray(128 * 1024)), partial) {
                if (++checks == 2) throw ProcessCanceledException()
            }
        }
        assertFalse(Files.exists(partial))
    }
}
