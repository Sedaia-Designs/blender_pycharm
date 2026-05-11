/**
 * This Module converts the NewProjectWizard API to the legacy DirectoryProjectGenerator
 * so it will render properly within Pycharm
 */
package com.sakurasedaia.blenderdevelopment.wizard.legacy

import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator
import com.sakurasedaia.blenderdevelopment.wizard.BlenderNewProjectWizardData
import com.sakurasedaia.blenderdevelopment.wizard.BlenderPythonProjectWizard

@Deprecated("This Module is slated for removal once PyCharm migrates to the newer ${"NewProjectWizard"} API.")
class BlenderProjectDirectoryGenerator :
    NewProjectWizardDirectoryGeneratorAdapter<BlenderNewProjectWizardData>(BlenderPythonProjectWizard()),
    CustomStepProjectGenerator<BlenderNewProjectWizardData> {
    
    private fun validate(): ValidationResult {
        return with(panel.component.validateAll()) {
            if (all { it.okEnabled }) ValidationResult.OK
            else find { !it.okEnabled }?.let { ValidationResult(it.message) } ?: ValidationResult("")
        }
    }
    
    override fun createStep(
        projectGenerator: DirectoryProjectGenerator<BlenderNewProjectWizardData>?,
        callback: AbstractNewProjectStep.AbstractCallback<BlenderNewProjectWizardData>?,
    ): AbstractActionWithPanel = object : NewProjectWizardProjectSettingsStep<BlenderNewProjectWizardData>(this) {
        override fun registerValidators() {
            setErrorText(validate().errorMessage)
            panel.step.propertyGraph.afterPropagation {
                setErrorText(validate().errorMessage)
            }
            Disposer.register(this) { }
        }
    }
}