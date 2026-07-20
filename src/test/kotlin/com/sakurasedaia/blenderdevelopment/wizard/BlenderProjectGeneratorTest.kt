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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.wizard

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.PythonSdkType
import com.sakurasedaia.blenderdevelopment.state.ProjectConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.java.JavaSourceRootType

/** Tests Blender scaffolding against the module and directory supplied by PyCharm. */
class BlenderProjectGeneratorTest : BasePlatformTestCase() {
    /** Runs suspendable generation from a worker thread so EDT write dispatch can complete. */
    override fun runInDispatchThread(): Boolean = false

    /** Verifies that generation writes the complete scaffold into the supplied module and directory. */
    fun testGenerationUsesSuppliedModuleAndCreatesProjectMetadata() {
        val baseDir = myFixture.tempDirFixture.findOrCreateDir("generated-project")
        addContentRoot(module, baseDir)

        val result = generate(module, baseDir)

        assertTrue(result is Result.Success)
        assertNotNull(baseDir.findFileByRelativePath("src/__init__.py"))
        assertNotNull(baseDir.findFileByRelativePath("src/blender_manifest.toml"))
        assertNotNull(baseDir.findChild("pyproject.toml"))
        assertNotNull(baseDir.findChild("LICENSE"))
        assertEquals(
            listOf(baseDir.findChild("src")),
            ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE).toList(),
        )
        assertEquals("sample_project", ProjectConfig.getInstance(project).getAddonSymlinkName())
        assertEquals("src/", ProjectConfig.getInstance(project).getSourceFolder())
    }

    /** Verifies that a missing content root is returned as a PyResult failure instead of being swallowed. */
    fun testMissingModuleContentRootReturnsFailure() {
        val baseDir = myFixture.tempDirFixture.findOrCreateDir("unowned-project")
        removeContentRoots(module)

        val result = generate(module, baseDir)

        assertTrue(result is Result.Failure)
        assertTrue(result.errorOrNull.toString().contains(baseDir.path))
        assertNotNull(baseDir.findChild("src"))
        assertNull(baseDir.findChild("pyproject.toml"))
    }

    /**
     * Removes content roots to exercise the generator's module-boundary failure path.
     *
     * @param generatedModule module to detach from its content roots.
     */
    private fun removeContentRoots(generatedModule: Module) {
        runWriteActionAndWait {
            val model = ModuleRootManager.getInstance(generatedModule).modifiableModel
            model.contentEntries.forEach(model::removeContentEntry)
            model.commit()
        }
    }

    /**
     * Adds the generated project directory as a module content root.
     *
     * @param generatedModule module receiving the content root.
     * @param baseDir generated project directory.
     */
    private fun addContentRoot(generatedModule: Module, baseDir: VirtualFile) {
        runWriteActionAndWait {
            val model = ModuleRootManager.getInstance(generatedModule).modifiableModel
            model.addContentEntry(baseDir)
            model.commit()
        }
    }

    /**
     * Runs generation with a minimal SDK and representative Blender manifest.
     *
     * @param generatedModule target module.
     * @param baseDir target project directory.
     * @return generation result.
     */
    private fun generate(generatedModule: Module, baseDir: VirtualFile): Result<Unit, *> =
        runBlocking(Dispatchers.Default) {
            BlenderProjectGenerator(sampleManifest(baseDir)).generateNewProject(generatedModule, baseDir, testSdk())
        }

    /**
     * Creates an unregistered Python SDK for the generator contract.
     *
     * @return test Python SDK.
     */
    private fun testSdk(): Sdk = ProjectJdkTable.getInstance().createSdk("Blender scaffolding test SDK", PythonSdkType.getInstance())

    /**
     * Creates representative generator input.
     *
     * @param baseDir generated project directory.
     * @return manifest used by the generator.
     */
    private fun sampleManifest(baseDir: VirtualFile): BlenderExtensionManifest = BlenderExtensionManifest(
        name = "Sample Project",
        path = baseDir.path,
        description = "Generator integration test",
        extensionVersion = "1.0.0",
        isGitInitialized = false,
        blenderVersion = "4.5",
        addExampleCode = true,
        author = "Sakura",
        projectType = BlenderProjectGenerator.PROJECT_TYPE_EXTENSION,
        extensionId = "sample_project",
        projectLicense = "SPDX:GPL-3.0-or-later",
        minBlenderVersion = "4.5",
        maxBlenderVersion = "",
        website = "",
        tags = emptyList(),
        filesPermission = "",
        networkPermission = "",
        clipboardPermission = "",
        cameraPermission = "",
        microphonePermission = "",
    )
}
