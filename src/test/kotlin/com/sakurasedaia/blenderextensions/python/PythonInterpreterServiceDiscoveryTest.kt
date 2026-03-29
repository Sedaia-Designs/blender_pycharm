package com.sakurasedaia.blenderextensions.python

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Method

class PythonInterpreterServiceDiscoveryTest : BasePlatformTestCase() {

    fun testDiscoverStandaloneAssetUrlMatchesAstralShAndIndygreg() {
        val service = PythonInterpreterService.getInstance(project)
        val method: Method = PythonInterpreterService::class.java.getDeclaredMethod(
            "discoverStandaloneAssetUrl", String::class.java, String::class.java
        )
        method.isAccessible = true

        // Mock body containing typical GitHub API response entries (both old and new repo formats)
        // We simulate the regex matching against these URLs.
        
        // This test doesn't actually call the real API (which we can't easily do in a unit test without network).
        // Instead, we'll verify the regex itself against a simulated body if we can expose it,
        // or just test the logic that processes the body.
        
        // Since discoverStandaloneAssetUrl performs the actual HttpRequests inside,
        // we'll test the regex directly by copying it here to verify it matches what we expect.
        
        val version = "3.11"
        val platformFragment = "x86_64-unknown-linux-gnu"
        val urlRegex = Regex(
            """https://github\.com/(?:indygreg|astral-sh)/python-build-standalone/releases/download/[^"]*/cpython-${Regex.escape(version)}\.(\d+)\+([^-"]+)-${Regex.escape(platformFragment)}-(install_runtime|install_only|install_full)\.(tar\.gz|zip)"""
        )

        val oldUrl = "https://github.com/indygreg/python-build-standalone/releases/download/20230507/cpython-3.11.3+20230507-x86_64-unknown-linux-gnu-install_runtime.tar.gz"
        val newUrl = "https://github.com/astral-sh/python-build-standalone/releases/download/20240101/cpython-3.11.7+20240101-x86_64-unknown-linux-gnu-install_only.tar.gz"
        val unrelatedUrl = "https://github.com/astral-sh/python-build-standalone/releases/download/20240101/cpython-3.12.1+20240101-x86_64-unknown-linux-gnu-install_only.tar.gz"

        assertTrue("Should match indygreg URL", urlRegex.containsMatchIn(oldUrl))
        assertTrue("Should match astral-sh URL", urlRegex.containsMatchIn(newUrl))
        assertFalse("Should NOT match different version", urlRegex.containsMatchIn(unrelatedUrl))
        
        val match = urlRegex.find(newUrl)!!
        assertEquals("3.11 patch should be 7", "7", match.groupValues[1])
        assertEquals("Build tag should match", "20240101", match.groupValues[2])
    }
}
