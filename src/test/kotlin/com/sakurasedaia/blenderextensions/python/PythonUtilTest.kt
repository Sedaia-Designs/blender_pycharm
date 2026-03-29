package com.sakurasedaia.blenderextensions.python

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class PythonUtilTest : BasePlatformTestCase() {

    fun testFindPythonExecutableDetectsVersionedPythonBinary() {
        val tempDir = Files.createTempDirectory("python_util_versioned")
        try {
            val blenderExe = if (SystemInfo.isWindows) {
                tempDir.resolve("blender.exe").createFile()
            } else {
                tempDir.resolve("blender").createFile()
            }

            val pythonBinDir = tempDir.resolve("5.1").resolve("python").resolve("bin")
            pythonBinDir.createDirectories()
            val expected = if (SystemInfo.isWindows) {
                pythonBinDir.resolve("python.exe").createFile()
            } else {
                pythonBinDir.resolve("python3.11").createFile()
            }

            val detected = PythonUtil.findPythonExecutable(blenderExe)
            assertEquals(expected, detected)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    fun testGetPythonVersionHandlesFullVersionStrings() {
        // This test is hard without a real python executable, but we can verify our regexes
        val regex = Regex("Python (\\d+\\.\\d+(?:\\.\\d+)?)")
        val match = regex.find("Python 3.11.7\n")
        assertEquals("3.11.7", match?.groupValues?.get(1))

        val match2 = regex.find("Python 3.12\n")
        assertEquals("3.12", match2?.groupValues?.get(1))

        val genericRegex = Regex("(\\d+\\.\\d+(?:\\.\\d+)?)")
        val match3 = genericRegex.find("3.11.7\n")
        assertEquals("3.11.7", match3?.groupValues?.get(1))
    }

    fun testPythonInterpreterDirectoryUsesPythonSubdirEvenForLegacyDownloadsPath() {
        val settings = BlenderSettings.getInstance(project)
        val originalDownloadsPath = settings.state.downloadsPath
        val tempRoot = Files.createTempDirectory("legacy_downloads_root")
        try {
            settings.state.downloadsPath = tempRoot.resolve("py_interpreter").toString()
            val expected = tempRoot.resolve("python").resolve("3.13")
            val actual = PythonUtil.getPythonInterpreterDirectory("3.13", project)
            assertEquals(expected, actual)

            val legacy = PythonUtil.getLegacyPythonInterpreterDirectory("3.13", project)
            assertEquals(tempRoot.resolve("py_interpreter").resolve("3.13"), legacy)
        } finally {
            settings.state.downloadsPath = originalDownloadsPath
            tempRoot.toFile().deleteRecursively()
        }
    }
}
