package com.sakurasedaia.blenderextensions.project

import com.intellij.execution.RunManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderextensions.run.BlenderRunConfiguration
import com.sakurasedaia.blenderextensions.run.BlenderRunConfigurationType
import org.junit.Test
import java.nio.file.Files

class BlenderProjectGeneratorTest : BasePlatformTestCase() {

    @Test
    fun testGenerateProjectRespectsSandboxSetting() {
        val extensionPoint = com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP
        val blenderRunConfigType = BlenderRunConfigurationType()
        extensionPoint.point.registerExtension(blenderRunConfigType, testRootDisposable)
        
        val generator = BlenderAddonProjectGenerator()
        val tempDir = Files.createTempDirectory("testProject")
        val baseDir = com.intellij.openapi.vfs.VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDir)!!
        
        @Suppress("DEPRECATION")
        try {
            // Scenario 1: Sandbox enabled (default)
            val settingsWithSandbox = BlenderAddonProjectSettings(
                projectName = "testProject",
                addonId = "test_addon",
                sandbox = true,
                agentGuidelines = false
            )
            
            generator.generateProject(project, baseDir, settingsWithSandbox, module)
            
            val runManager = RunManager.getInstance(project)
            val startBlenderConfig = runManager.findConfigurationByName("Start Blender")
            assertNotNull("Run configuration 'Start Blender' should be created", startBlenderConfig)
            val runConfig = startBlenderConfig?.configuration as BlenderRunConfiguration
            assertTrue("isSandboxed should be true when settings.sandbox is true", runConfig.getOptions().isSandboxed)
            
            // Scenario 2: Sandbox disabled
            val settingsWithoutSandbox = BlenderAddonProjectSettings(
                projectName = "testProjectNoSandbox",
                addonId = "test_addon_no_sandbox",
                sandbox = false,
                agentGuidelines = false
            )
            
            generator.generateProject(project, baseDir, settingsWithoutSandbox, module)
            
            val allConfigs = runManager.allConfigurationsList.filterIsInstance<BlenderRunConfiguration>()
            val latestStartBlender = allConfigs.last { it.name == "Start Blender" }
            
            assertFalse("isSandboxed should be false when settings.sandbox is false", latestStartBlender.getOptions().isSandboxed)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
