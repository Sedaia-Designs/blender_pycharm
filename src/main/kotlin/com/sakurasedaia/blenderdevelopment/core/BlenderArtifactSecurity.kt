package com.sakurasedaia.blenderdevelopment.core

import com.sakurasedaia.blenderdevelopment.lib.BlenderVersion
import com.sakurasedaia.blenderdevelopment.lib.ErrorTypes
import com.sakurasedaia.blenderdevelopment.util.SystemInfo
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/** The official Blender release artifact and its version-wide checksum manifest. */
internal data class BlenderArtifactDescriptor(
    val version: String,
    val platform: String,
    val architecture: String,
    val filename: String,
    val archiveUri: URI,
    val checksumUri: URI,
    val expectedSha256: ByteArray? = null,
) {
    init {
        require(expectedSha256 == null || expectedSha256.size == 32)
        require(FILE.matches(filename))
        require(filename.startsWith("blender-$version-$platform-$architecture."))
        requireTrustedUri(archiveUri, filename)
        requireTrustedUri(checksumUri, "blender-$version.sha256")
    }

    companion object {
        private val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
        private val FILE =
            Regex("blender-[0-9]+\\.[0-9]+\\.[0-9]+-(?:windows-(?:x64|arm64)\\.zip|macos-(?:x64|arm64)\\.dmg|linux-x64\\.tar\\.xz)")

        /** Creates the canonical descriptor for the current host when the release supports it. */
        fun forHost(version: BlenderVersion): BlenderArtifactDescriptor =
            forPlatform(version, SystemInfo.getSysInfo.osName, SystemInfo.getSysInfo.osArch)

        /** Maps a supported platform and architecture to its exact official archive filename. */
        fun forPlatform(version: BlenderVersion, platform: String, architecture: String): BlenderArtifactDescriptor {
            if (version.compatWithOs[platform]?.contains(architecture) != true) {
                throw SecurityException(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN.toString())
            }
            val filename = "blender-${version.blVersion}-$platform-$architecture.${SystemInfo.normalizeBundleFileType(platform)}"
            require(FILE.matches(filename)) { ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN.toString() }
            require(VERSION.matches(version.blVersion)) { ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN.toString() }
            val base = "https://download.blender.org/release/Blender${version.blMajorMinor}/"
            return BlenderArtifactDescriptor(
                version.blVersion,
                platform,
                architecture,
                filename,
                URI.create(base + filename),
                URI.create(base + "blender-${version.blVersion}.sha256"),
            )
        }

        /** Rejects any URI outside the canonical HTTPS release URL for one filename. */
        fun requireTrustedUri(uri: URI, expectedFilename: String) {
            val version =
                Regex("blender-(\\d+\\.\\d+\\.\\d+)").find(expectedFilename)?.groupValues?.get(1)
                    ?: throw SecurityException(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN.toString())
            val majorMinor = version.substringBeforeLast('.')
            if (
                uri.scheme != "https" ||
                    uri.host != "download.blender.org" ||
                    uri.port != -1 ||
                    uri.userInfo != null ||
                    uri.query != null ||
                    uri.fragment != null ||
                    uri.rawPath != "/release/Blender$majorMinor/$expectedFilename" ||
                    uri.toASCIIString() != "https://download.blender.org/release/Blender$majorMinor/$expectedFilename"
            ) {
                throw SecurityException(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN.toString())
            }
        }
    }
}

/** Reads only Blender's release origin, including every redirect destination. */
internal object BlenderReleaseTransport {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build()

    /** Opens a release file while validating each redirect before requesting its bytes. */
    fun open(uri: URI, expectedFilename: String): HttpResponse<InputStream> {
        var current = uri
        repeat(4) {
            BlenderArtifactDescriptor.requireTrustedUri(current, expectedFilename)
            val request = HttpRequest.newBuilder(current).timeout(Duration.ofMinutes(10)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() in 300..399) {
                response.body().close()
                val location =
                    response.headers().firstValue("Location").orElseThrow {
                        SecurityException(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN.toString())
                    }
                current = trustedRedirect(current, location, expectedFilename)
            } else {
                if (response.statusCode() != 200) {
                    response.body().close()
                    if (response.statusCode() == 404 && expectedFilename.endsWith(".sha256")) {
                        throw MissingOfficialChecksumException()
                    }
                    throw IOException("Blender download returned HTTP ${response.statusCode()}")
                }
                return response
            }
        }
        throw SecurityException(ErrorTypes.UNSUPPORTED_DOWNLOAD_ORIGIN.toString())
    }
}

/** Resolves a redirect only when it stays on the exact official artifact URL. */
internal fun trustedRedirect(current: URI, location: String, expectedFilename: String): URI =
    current.resolve(location).also { BlenderArtifactDescriptor.requireTrustedUri(it, expectedFilename) }

/** Streams a response into an unpublished partial file and removes it on cancellation or failure. */
internal fun streamBlenderDownload(input: InputStream, partial: Path, checkCancellation: () -> Unit) {
    var completed = false
    try {
        Files.newOutputStream(partial).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
        checkCancellation()
        completed = true
    } finally {
        if (!completed) runCatching { Files.deleteIfExists(partial) }
    }
}

/** The official release does not publish a SHA-256 manifest. */
internal class MissingOfficialChecksumException : IOException(ErrorTypes.MISSING_OFFICIAL_BLENDER_CHECKSUM.toString())

/** Resolves the SHA-256 for one exact filename from Blender's version-wide manifest. */
internal fun officialDigest(manifest: String, filename: String): ByteArray? {
    val match =
        manifest
            .lineSequence()
            .mapNotNull { line ->
                Regex("^([0-9a-fA-F]{64})\\s+\\*?([^\\s]+)\\s*$").matchEntire(line)?.destructured
            }
            .singleOrNull { (_, name) -> name == filename } ?: return null
    return HexFormat.of().parseHex(match.component1())
}

/** Fetches the small official checksum manifest without retaining unbounded response data. */
internal fun fetchOfficialDigest(descriptor: BlenderArtifactDescriptor): ByteArray? {
    BlenderReleaseTransport.open(descriptor.checksumUri, "blender-${descriptor.version}.sha256").body().use { stream ->
        val bytes = stream.readNBytes(65_537)
        if (bytes.size > 65_536) throw IOException("Blender checksum manifest exceeds 64 KiB")
        return officialDigest(bytes.toString(Charsets.UTF_8), descriptor.filename)
    }
}

/** Streams the local archive through SHA-256 and compares it with the official value. */
internal fun verifyArchiveDigest(archive: Path, expected: ByteArray): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(archive).use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return MessageDigest.isEqual(digest.digest(), expected)
}
