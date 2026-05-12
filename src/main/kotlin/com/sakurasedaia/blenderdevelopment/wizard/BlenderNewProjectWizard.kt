/**
 * Entry point level of the entire Project Wizard. This file defines the entry itself into the New Project Wizard for Intellij-based applications.
 * */
package com.sakurasedaia.blenderdevelopment.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.newProjectWizardBaseStepWithoutGap
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.sakurasedaia.blenderdevelopment.common.MessageBundle
import com.sakurasedaia.blenderdevelopment.icons.BlenderIcons
import javax.swing.Icon

class BlenderPythonProjectWizard : GeneratorNewProjectWizard {
    override val name: String = MessageBundle.message("project.wizard.template.title")
    override val id: String = "BlenderPythonProjectWizard"
    override val icon: Icon = BlenderIcons.BlenderColor
    
    override fun createStep(context: WizardContext): NewProjectWizardStep {
        return RootNewProjectWizardStep(context)
            .nextStep(::newProjectWizardBaseStepWithoutGap)
            .nextStep(::GitNewProjectWizardStep)
            .nextStep(::BlenderNewProjectWizardStep)
    }
}