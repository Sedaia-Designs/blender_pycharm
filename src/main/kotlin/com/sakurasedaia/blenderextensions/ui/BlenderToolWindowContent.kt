package com.sakurasedaia.blenderextensions.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.BlenderService
import com.sakurasedaia.blenderextensions.blender.BlenderVersions
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.python.PythonService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class BlenderToolWindowContent(private val project: Project) {
    private val blenderService = BlenderService.getInstance(project)
    private val pythonService = PythonService.getInstance(project)
    private val downloader = BlenderDownloader.getInstance(project)

    private val versionComboBox = ComboBox<String>()
    private val downloadButton = JButton(LangManager.message("toolwindow.managed.button.download"))
    private val setupLinterButton = JButton(LangManager.message("toolwindow.managed.button.setup.linter"))
    private val clearSandboxButton = JButton(LangManager.message("toolwindow.sandbox.clear"))

    init {
        blenderService.scanInstallations()
    }

    fun getContent(): JComponent {
        val mainPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.insets = JBUI.insets(5)

        // --- Settings Link ---
        gbc.gridx = 0
        gbc.gridy = 0
        val settingsPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val openSettingsButton = JButton(com.intellij.icons.AllIcons.General.Settings)
        openSettingsButton.toolTipText = LangManager.message("toolwindow.open.settings")
        openSettingsButton.addActionListener {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, com.sakurasedaia.blenderextensions.settings.BlenderConfigurable::class.java)
        }
        settingsPanel.add(openSettingsButton)
        mainPanel.add(settingsPanel, gbc)

        // Sandbox Management
        gbc.gridy++
        val sandboxPanel = JPanel(BorderLayout())
        sandboxPanel.border = JBUI.Borders.compound(
            BorderFactory.createTitledBorder(LangManager.message("toolwindow.sandbox.management.label")),
            JBUI.Borders.empty(5)
        )
        
        clearSandboxButton.addActionListener {
            val result = Messages.showYesNoDialog(
                project,
                LangManager.message("toolwindow.sandbox.clear.warning"),
                LangManager.message("toolwindow.sandbox.clear"),
                LangManager.message("toolwindow.sandbox.clear.confirm"),
                LangManager.message("button.cancel"),
                Messages.getQuestionIcon()
            )
            if (result == Messages.YES) {
                blenderService.clearSandbox()
                Messages.showInfoMessage(
                    project,
                    LangManager.message("toolwindow.sandbox.clear.success"),
                    LangManager.message("toolwindow.sandbox.clear.success.title")
                )
            }
        }
        sandboxPanel.add(clearSandboxButton, BorderLayout.CENTER)
        mainPanel.add(sandboxPanel, gbc)

        // Blender Version Selector
        gbc.gridy++
        val versionPanel = JPanel(GridBagLayout())
        versionPanel.border = JBUI.Borders.compound(
            BorderFactory.createTitledBorder(LangManager.message("toolwindow.table.column.version")),
            JBUI.Borders.empty(5)
        )
        val vGbc = GridBagConstraints()
        vGbc.fill = GridBagConstraints.HORIZONTAL
        vGbc.weightx = 1.0
        vGbc.insets = JBUI.insets(2)

        refreshVersions()
        
        vGbc.gridx = 0
        vGbc.gridy = 0
        vGbc.gridwidth = 3
        versionPanel.add(versionComboBox, vGbc)

        vGbc.gridy++
        vGbc.gridwidth = 1
        vGbc.gridx = 0
        versionPanel.add(downloadButton, vGbc)
        vGbc.gridx = 1
        vGbc.gridwidth = 2
        versionPanel.add(setupLinterButton, vGbc)

        downloadButton.addActionListener { handleDownload() }
        setupLinterButton.addActionListener { handleSetupLinter() }
        versionComboBox.addActionListener { updateButtonStates() }

        updateButtonStates()

        mainPanel.add(versionPanel, gbc)

        // Add spacer to push everything to top
        gbc.gridy++
        gbc.weighty = 1.0
        mainPanel.add(JPanel(), gbc)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(mainPanel, BorderLayout.NORTH)
        return wrapper
    }

    private fun refreshVersions() {
        val selectable = BlenderVersions.getAllSelectableVersions()
        val (managedVersions, discoveredPaths) = selectable.partition {
            !it.contains("/") && !it.contains("\\")
        }
        val orderedSelectable = managedVersions.asReversed() + discoveredPaths
        val currentSelection = versionComboBox.selectedItem as? String
        
        versionComboBox.removeAllItems()
        orderedSelectable.forEach { versionComboBox.addItem(it) }
        
        if (currentSelection != null && orderedSelectable.contains(currentSelection)) {
            versionComboBox.selectedItem = currentSelection
        }
    }

    private fun updateButtonStates() {
        val selected = versionComboBox.selectedItem as? String ?: return
        
        val isPath = selected.contains("/") || selected.contains("\\")
        if (isPath) {
            downloadButton.isEnabled = false
            setupLinterButton.isEnabled = true
        } else {
            val isDownloaded = downloader.isDownloaded(selected)
            downloadButton.isEnabled = !isDownloaded
            setupLinterButton.isEnabled = isDownloaded
        }
    }

    private fun handleDownload() {
        val version = versionComboBox.selectedItem as? String ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, LangManager.message("action.download.blender.task", version)) {
            override fun run(indicator: ProgressIndicator) {
                downloader.getOrDownloadBlenderPath(version)
                ApplicationManager.getApplication().invokeLater {
                    updateButtonStates()
                }
            }
        })
    }

    private fun handleSetupLinter() {
        val selected = versionComboBox.selectedItem as? String ?: return
        pythonService.setupLinter(selected)
    }
}
