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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.core.installs

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class ScannerUtilsTest : BasePlatformTestCase() {
    private lateinit var macScanner: MacScanner
    private lateinit var support: ScannerSupport
    private lateinit var testRoot: Path

    override fun setUp() {
        super.setUp()
        support = ScannerSupport(project)
        macScanner = MacScanner(support)
        val projectPath = Path.of(project.basePath!!).createDirectories()
        testRoot = Files.createTempDirectory(projectPath, "installation-scanner-")
    }

    fun testFindsDirectBundlesRegardlessOfBundleNameCase() {
        val namedBundle = createBundle(testRoot.resolve("Blender.app"))
        val versionBundle = createBundle(testRoot.resolve("5.2.0.APP"))

        val discovered = findBundles(listOf(testRoot))

        assertEquals(linkedSetOf(namedBundle, versionBundle), discovered.toCollection(linkedSetOf()))
    }

    fun testFindsOneNestedLevelAndHonorsMaximumDepth() {
        val nestedBundle = createBundle(testRoot.resolve("Blender").resolve("5.2.0.app"))
        createBundle(testRoot.resolve("Blender").resolve("Stable").resolve("5.1.0.app"))

        val discovered = findBundles(listOf(testRoot), maxSearchDepth = 1)

        assertEquals(listOf(nestedBundle), discovered)
    }

    fun testIgnoresBundlesWithoutAnInternalBlenderBinary() {
        testRoot.resolve("NotBlender.app").createDirectories()
        val validBundle = createBundle(testRoot.resolve("Custom Name.app"))

        assertEquals(listOf(validBundle), findBundles(listOf(testRoot)))
    }

    fun testOverlappingRootsProbeEachBundleOnce() {
        val container = testRoot.resolve("Blender").createDirectories()
        val bundle = createBundle(container.resolve("5.2.0.app"))
        var probeCount = 0

        val discovered =
            macScanner.findBlenderBundles(
                searchRoots = listOf(testRoot, container, testRoot.resolve(".")),
                maxSearchDepth = 1,
                isExecutable = {
                    probeCount += 1
                    Files.isRegularFile(it)
                },
            )

        assertEquals(listOf(bundle), discovered)
        assertEquals(1, probeCount)
    }

    fun testCancellationStopsBundleTraversal() {
        createBundle(testRoot.resolve("Blender.app"))

        try {
            macScanner.findBlenderBundles(
                searchRoots = listOf(testRoot),
                maxSearchDepth = 1,
                shouldCancel = { true },
                isExecutable = Files::isRegularFile,
            )
            fail("Expected discovery to be cancelled")
        } catch (_: CancellationException) {}
    }

    fun testFindsLinkedAndCellarHomebrewBinaries() {
        val linkedBlender = createBinary(testRoot.resolve("bin").resolve("blender"))
        val linkedRuntime = createBinary(testRoot.resolve("bin").resolve("blender-runtime"))
        val cellarBlender = createBinary(testRoot.resolve("Cellar").resolve("blender").resolve("5.2.0").resolve("bin").resolve("blender"))
        val cellarRuntime =
            createBinary(testRoot.resolve("Cellar").resolve("blender-runtime").resolve("4.5.0").resolve("bin").resolve("blender-runtime"))

        val discovered =
            support.findHomebrewBlenderBinaries(
                brewPrefixes = listOf(testRoot, testRoot.resolve(".")),
                isExecutable = Files::isRegularFile,
            )

        assertEquals(
            linkedSetOf(linkedBlender, cellarBlender, linkedRuntime, cellarRuntime),
            discovered.toCollection(linkedSetOf()),
        )
    }

    fun testHomebrewDiscoveryHonorsCancellationBeforeInspectingPaths() {
        var executableChecks = 0

        try {
            support.findHomebrewBlenderBinaries(
                brewPrefixes = listOf(testRoot),
                shouldCancel = { true },
                isExecutable = {
                    executableChecks += 1
                    Files.isRegularFile(it)
                },
            )
            fail("Expected discovery to be cancelled")
        } catch (_: CancellationException) {}

        assertEquals(0, executableChecks)
    }

    fun testExtractsSemanticVersionsFromSupportedBlenderOutputFormats() {
        assertEquals("4.2.1", formSemanticVersion("Blender 4.2.1"))
        assertEquals("4.2.0", formSemanticVersion("Blender 4.2"))
        assertEquals("4.2.1", formSemanticVersion("Blender version 4 build 2 revision 1"))
        assertEquals("", formSemanticVersion("Blender unknown"))
    }

    private fun findBundles(searchRoots: List<Path>, maxSearchDepth: Int = 1): List<Path> {
        return macScanner.findBlenderBundles(
            searchRoots = searchRoots,
            maxSearchDepth = maxSearchDepth,
            isExecutable = Files::isRegularFile,
        )
    }

    private fun createBundle(bundle: Path): Path {
        createBinary(bundle.resolve("Contents").resolve("MacOS").resolve("Blender"))
        return bundle.toAbsolutePath().normalize()
    }

    private fun createBinary(binary: Path): Path {
        binary.parent.createDirectories()
        binary.createFile()
        return binary.toAbsolutePath().normalize()
    }
}
