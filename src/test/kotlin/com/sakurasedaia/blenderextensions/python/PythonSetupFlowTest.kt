package com.sakurasedaia.blenderextensions.python

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

class PythonSetupFlowTest : BasePlatformTestCase() {

    fun testSetupInterpreterFailsWhenBundledPythonMissing() {
        val tempDir = Files.createTempDirectory("py_setup_missing_bundled")
        try {
            val blenderExe = createBlenderExecutable(tempDir)
            val configured = PythonInterpreterService.getInstance(project).setupPythonInterpreter(blenderExe.toString())
            assertFalse("Interpreter setup should fail when Blender bundled Python is missing", configured)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    fun testSetupInterpreterRequiresMatchingSystemPython() {
        if (SystemInfo.isWindows) return

        val tempDir = Files.createTempDirectory("py_setup_requires_system")
        try {
            val blenderExe = createBlenderExecutable(tempDir)
            val bundledPython = tempDir.resolve("4.2").resolve("python").resolve("bin").resolve("python3")
            bundledPython.parent.createDirectories()

            // Fake Blender-bundled python used only for version detection.
            Files.writeString(
                bundledPython,
                "#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then echo \"Python 9.9.9\"; else echo \"Python 9.9.9\"; fi\n"
            )
            bundledPython.toFile().setExecutable(true)

            val configured = PythonInterpreterService.getInstance(project).setupPythonInterpreter(blenderExe.toString())
            assertFalse("Interpreter setup should fail if no matching system Python is available", configured)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    fun testInstallFakeBpyModuleDoesNotFallbackToBundledPythonOrLintDir() {
        val linterService = PythonLinterService.getInstance(project)
        val isolatedVersion = "9.9-nofallback"
        val lintDir = PythonUtil.getLintDirectory(isolatedVersion, project)
        if (lintDir.exists()) {
            lintDir.toFile().deleteRecursively()
        }

        val installed = linterService.installFakeBpyModule(isolatedVersion)
        assertFalse("Linter install should fail without a configured .venv python", installed)
    }

    fun testGetLinterSdkRootsUsesProjectVenvSitePackages() {
        val projectBase = project.basePath?.let { Path.of(it) } ?: return
        val sitePackagesRoot = if (SystemInfo.isWindows) {
            projectBase.resolve(".venv").resolve("Lib").resolve("site-packages")
        } else {
            projectBase.resolve(".venv").resolve("lib").resolve("python3.11").resolve("site-packages")
        }
        val createdByTest = !sitePackagesRoot.exists()
        sitePackagesRoot.createDirectories()

        try {
            val fakeBlenderPath = "/tmp/blender_downloads/app/4.2/blender"
            val roots = PythonLinterService.getInstance(project).getLinterSDKRoots(fakeBlenderPath)
            assertTrue("Expected .venv site-packages to be included in linter SDK roots", roots.contains(sitePackagesRoot))
        } finally {
            if (createdByTest) {
                sitePackagesRoot.toFile().deleteRecursively()
            }
        }
    }

    private fun createBlenderExecutable(root: Path): Path {
        return if (SystemInfo.isWindows) {
            root.resolve("blender.exe").createFile()
        } else {
            root.resolve("blender").createFile()
        }
    }
}
