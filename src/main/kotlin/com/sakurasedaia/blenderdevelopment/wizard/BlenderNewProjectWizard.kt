/*
 * Copyright (C) 2026  [Your Name or Organization]
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
    override val name: String = MessageBundle.message("ui.project.wizard.template.title")
    override val id: String = "BlenderPythonProjectWizard"
    override val icon: Icon = BlenderIcons.BlenderColor
    
    override fun createStep(context: WizardContext): NewProjectWizardStep {
        return RootNewProjectWizardStep(context)
            .nextStep(::newProjectWizardBaseStepWithoutGap)
            .nextStep(::GitNewProjectWizardStep)
            .nextStep(::BlenderNewProjectWizardStep)
    }
}