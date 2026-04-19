package com.sakurasedaia.blenderextensions.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.blender.services.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.services.BlenderService
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.python.PythonService
import javax.swing.*
import com.sakurasedaia.blenderextensions.ui.settings.BlenderConfigurable
import com.sakurasedaia.blenderextensions.icons.BlenderIcons

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
        refreshVersions()
        updateButtonStates()

        return panel {
            row {
                button("") {
                    com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, BlenderConfigurable::class.java)
                }.applyToComponent {
                    icon = com.intellij.icons.AllIcons.General.Settings
                    toolTipText = LangManager.message("toolwindow.open.settings")
                }.align(AlignX.RIGHT)
            }

            group(LangManager.message("toolwindow.sandbox.management.label")) {
                row {
                    cell(clearSandboxButton).applyToComponent {
                        addActionListener {
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
                    }.align(com.intellij.ui.dsl.builder.AlignX.FILL)
                }
            }

            group(LangManager.message("toolwindow.table.column.version")) {
                row {
                    cell(versionComboBox).align(com.intellij.ui.dsl.builder.AlignX.FILL).applyToComponent {
                        addActionListener { updateButtonStates() }
                    }
                }
                row {
                    cell(downloadButton).applyToComponent {
                        addActionListener { handleDownload() }
                    }
                    cell(setupLinterButton).applyToComponent {
                        addActionListener { handleSetupLinter() }
                    }
                }
            }
        }
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
