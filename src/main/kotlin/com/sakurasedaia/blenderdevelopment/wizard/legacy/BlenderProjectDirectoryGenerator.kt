// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
 *
 * ---
 * Portions of this file are derived from xmake-idea
 * Copyright 2022 xmake-io (https://github.com/xmake-io/xmake-idea)
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * http://www.apache.org/licenses/LICENSE-2.0
 */


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

@Deprecated("This Module is slated for removal once PyCharm properly migrates to the newer ${"NewProjectWizard"} API.")
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