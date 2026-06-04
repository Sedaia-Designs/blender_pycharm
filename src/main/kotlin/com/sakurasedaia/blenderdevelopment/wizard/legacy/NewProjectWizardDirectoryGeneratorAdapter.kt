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

package com.sakurasedaia.blenderdevelopment.wizard.legacy

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardStepPanel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A base adapter class to turn a [GeneratorNewProjectWizard] into a
 * [com.intellij.platform.DirectoryProjectGenerator] and register as an extension point.
 *
 * @see NewProjectWizardProjectSettingsStep
 */
@Deprecated("This Module is slated for removal once PyCharm migrates to the newer ${"NewProjectWizard"} API.")
open class NewProjectWizardDirectoryGeneratorAdapter<T : Any>(val wizard: GeneratorNewProjectWizard) :
    DirectoryProjectGeneratorBase<T>() {
    internal lateinit var panel: NewProjectWizardStepPanel

    /**
     * Returns the display name shown in legacy project generator lists.
     *
     * @return wizard display name.
     */
    
    
    override fun getName(): String = wizard.name
    /**
     * Returns the icon shown in legacy project generator lists.
     *
     * @return wizard icon.
     */
    override fun getLogo(): Icon = wizard.icon

    
    /**
     * Delegates project setup to the wrapped New Project Wizard step.
     *
     * @param project target project.
     * @param baseDir project base directory.
     * @param settings generator settings payload.
     * @param module module created for the project.
     * @return `Unit`.
     */
    override fun generateProject(project: Project, baseDir: VirtualFile, settings: T, module: Module) {
        panel.step.setupProject(project)
    }

    
    /**
     * Creates a bridge peer that renders the New Wizard step inside legacy UI.
     *
     * @return generator peer that hosts wizard content.
     */
    override fun createPeer(): ProjectGeneratorPeer<T> {
        val context = WizardContext(null) {}
        return object : GeneratorPeerImpl<T>() {
            override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
                panel = NewProjectWizardStepPanel(wizard.createStep(context))
                return panel.component
            }
        }
    }
}

/**
 * A wizard-enabled project settings step that you should use for your [projectGenerator] in your
 * [AbstractNewProjectStep.Customization.createProjectSpecificSettingsStep] to provide the project wizard UI and actions.
 */
@Deprecated("This Module is slated for removal once PyCharm migrates to the newer ${"NewProjectWizard"} API.")
open class NewProjectWizardProjectSettingsStep<T : Any>(private val projectGenerator: NewProjectWizardDirectoryGeneratorAdapter<T>) :
    ProjectSettingsStepBase<T>(projectGenerator, null) {

    init {
        myCallback = AbstractNewProjectStep.AbstractCallback()
    }

    /**
     * Builds the panel containing the bridged wizard UI.
     *
     * @return populated legacy content panel.
     */
    override fun createAndFillContentPanel(): JPanel =
        JPanel(VerticalFlowLayout()).apply {
            add(peer.getComponent(TextFieldWithBrowseButton()) {})
        }

    /**
     * Validators are managed by the wrapped wizard step.
     *
     * @return `Unit`.
     */
    override fun registerValidators() {}

    /**
     * Returns the project location from wizard context.
     *
     * @return project location path.
     */
    override fun getProjectLocation(): String =
        projectGenerator.panel.step.context.projectFileDirectory

    /**
     * Applies the wizard panel state before delegating to base action handling.
     *
     * @return action button for this settings step.
     */
    override fun getActionButton(): JButton =
        super.getActionButton().apply {
            addActionListener {
                projectGenerator.panel.apply()
            }
        }
}
