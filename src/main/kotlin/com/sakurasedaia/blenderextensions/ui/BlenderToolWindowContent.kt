package com.sakurasedaia.blenderextensions.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.*
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import javax.swing.*

class BlenderToolWindowContent(private val project: Project) {
    private val service = BlenderService.getInstance(project)
    private val pythonService = PythonService.getInstance(project)
    private val commService = BlenderCommunicationService.getInstance(project)
    private val managedTable = ManagedBlenderTable(project)
    private val systemTable = SystemBlenderTable(project)
    private val systemPathField = JBTextField().apply {
        isEditable = false
    }
    private val managedActionButtons = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    private val downloadUninstallButton = JButton()
    private val setupInterpreterButton = JButton(LangManager.message("toolwindow.setup.interpreter"), BlenderIcons.Python)
    private val setupLinterButton = JButton(LangManager.message("toolwindow.managed.button.setup.linter"), BlenderIcons.Add)
    
    private val systemActionButtons = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    private val systemSetupInterpreterButton = JButton(LangManager.message("toolwindow.setup.interpreter"), BlenderIcons.Python)
    private val systemSetupLinterButton = JButton(LangManager.message("toolwindow.managed.button.setup.linter"), BlenderIcons.Add)
    private val systemRemoveButton = JButton("", BlenderIcons.Remove)

    private val managedProgressPanel = JPanel(VerticalLayout(2))
    private val managedProgressBar = JProgressBar(0, 100)
    private val managedStatusLabel = JBLabel().apply {
        font = font.deriveFont(11f)
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    private val systemProgressPanel = JPanel(VerticalLayout(2))
    private val systemProgressBar = JProgressBar(0, 100)
    private val systemStatusLabel = JBLabel().apply {
        font = font.deriveFont(11f)
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    private val cs = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val timer: Timer

    init {
        setupManagedButtons()
        setupSystemButtons()
        setupListeners()

        managedProgressPanel.add(managedProgressBar)
        managedProgressPanel.add(managedStatusLabel)
        managedProgressPanel.isVisible = false

        systemProgressPanel.add(systemProgressBar)
        systemProgressPanel.add(systemStatusLabel)
        systemProgressPanel.isVisible = false

        val downloader = BlenderDownloader.getInstance(project)
        cs.launch {
            downloader.downloadProgress.collectLatest { progress ->
                SwingUtilities.invokeLater {
                    updateProgress(progress)
                }
            }
        }

        timer = Timer(5000) {
            if (!project.isDisposed) {
                managedTable.refresh()
                systemTable.refresh()
                updateManagedButtons()
                updateSystemButtons()
            } else {
                (it.source as Timer).stop()
            }
        }
        timer.start()
    }

    private fun updateProgress(progress: BlenderDownloader.DownloadProgress) {
        if (!progress.isDownloading) {
            managedProgressPanel.isVisible = false
            systemProgressPanel.isVisible = false
            return
        }

        val isManaged = progress.type == BlenderDownloader.ProgressType.DOWNLOAD
        val isLinter = progress.type == BlenderDownloader.ProgressType.LINTER
        
        // Find if this version is in managed table or system table
        val inManaged = managedTable.containsVersion(progress.version)
        
        val progressBar = if (inManaged) managedProgressBar else systemProgressBar
        val statusLabel = if (inManaged) managedStatusLabel else systemStatusLabel
        val panel = if (inManaged) managedProgressPanel else systemProgressPanel

        panel.isVisible = true
        statusLabel.text = progress.statusText
        
        if (progress.progress < 0) {
            progressBar.isIndeterminate = true
        } else {
            progressBar.isIndeterminate = false
            progressBar.value = (progress.progress * 100).toInt()
        }
        
        if (inManaged) {
            systemProgressPanel.isVisible = false
        } else {
            managedProgressPanel.isVisible = false
        }
    }

    private fun setupListeners() {
        managedTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateManagedButtons()
            }
        }

        systemTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val inst = systemTable.getSelectedInstallation()
                systemPathField.text = inst?.path ?: ""
                updateSystemButtons()
            }
        }
    }

    private fun setupManagedButtons() {
        downloadUninstallButton.addActionListener {
            val version = managedTable.getSelectedVersion() ?: return@addActionListener
            val downloader = BlenderDownloader.getInstance(project)
            if (downloader.isDownloaded(version)) {
                val confirm = Messages.showYesNoDialog(
                    project,
                    LangManager.message("toolwindow.managed.action.delete.description"),
                    LangManager.message("toolwindow.managed.action.delete.title", version),
                    Messages.getQuestionIcon()
                )
                if (confirm == Messages.YES) {
                    if (!commService.isConnected()) {
                        downloader.deleteVersion(version)
                        managedTable.refresh()
                        updateManagedButtons()
                    } else {
                        BlenderNotification(project).sendError(
                            title = LangManager.message("notification.delete.failed.title", "Blender $version"),
                            content = LangManager.message("notification.delete.failed.reason.blender.running")
                        )
                    }
                }
            } else {
                ProgressManager.getInstance().run(
                    object : Task.Backgroundable(project, LangManager.message("action.download.blender.task", version)) {
                        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                            downloader.getOrDownloadBlenderPath(version)
                            SwingUtilities.invokeLater {
                                managedTable.refresh()
                                updateManagedButtons()
                            }
                        }
                    }
                )
            }
        }

        setupInterpreterButton.apply {
            toolTipText = LangManager.message("toolwindow.setup.interpreter.tooltip")
            addActionListener {
                val version = managedTable.getSelectedVersion() ?: return@addActionListener
                val path = BlenderDownloader.getInstance(project).getOrDownloadBlenderPath(version)
                if (path != null) {
                    pythonService.setupPythonInterpreter(path)
                }
            }
        }

        setupLinterButton.apply {
            toolTipText = LangManager.message("toolwindow.managed.button.setup.linter.tooltip")
            addActionListener {
                val version = managedTable.getSelectedVersion() ?: return@addActionListener
                val path = BlenderDownloader.getInstance(project).getOrDownloadBlenderPath(version)
                if (path != null) {
                    service.setupLinter(path)
                }
            }
        }

        managedActionButtons.add(downloadUninstallButton)
        managedActionButtons.add(setupInterpreterButton)
        managedActionButtons.add(setupLinterButton)

        updateManagedButtons()
    }

    private fun updateManagedButtons() {
        val version = managedTable.getSelectedVersion()
        if (version == null) {
            downloadUninstallButton.isEnabled = false
            downloadUninstallButton.text = LangManager.message("toolwindow.managed.button.download")
            downloadUninstallButton.icon = BlenderIcons.Install
            setupInterpreterButton.isEnabled = false
            setupLinterButton.isEnabled = false
            return
        }

        val downloaded = BlenderDownloader.getInstance(project).isDownloaded(version)
        downloadUninstallButton.isEnabled = true
        if (downloaded) {
            downloadUninstallButton.text = LangManager.message("toolwindow.managed.button.uninstall")
            downloadUninstallButton.icon = BlenderIcons.Remove
            setupInterpreterButton.isEnabled = true
            setupLinterButton.isEnabled = true
        } else {
            downloadUninstallButton.text = LangManager.message("toolwindow.managed.button.download")
            downloadUninstallButton.icon = BlenderIcons.Install
            setupInterpreterButton.isEnabled = false
            setupLinterButton.isEnabled = false
        }
    }

    private fun setupSystemButtons() {
        systemSetupInterpreterButton.apply {
            toolTipText = LangManager.message("toolwindow.setup.interpreter.tooltip")
            addActionListener {
                val inst = systemTable.getSelectedInstallation() ?: return@addActionListener
                pythonService.setupPythonInterpreter(inst.path)
            }
        }

        systemSetupLinterButton.apply {
            toolTipText = LangManager.message("toolwindow.managed.button.setup.linter.tooltip")
            addActionListener {
                val inst = systemTable.getSelectedInstallation() ?: return@addActionListener
                service.setupLinter(inst.path)
            }
        }

        systemRemoveButton.apply {
            toolTipText = LangManager.message("toolwindow.table.action.remove")
            addActionListener {
                val inst = systemTable.getSelectedInstallation() ?: return@addActionListener
                if (inst.isCustom && inst.originPath != null) {
                    val confirm = Messages.showYesNoDialog(
                        project,
                        LangManager.message("toolwindow.system.table.action.delete.confirm.message"),
                        LangManager.message("toolwindow.system.table.action.delete.confirm.button"),
                        Messages.getQuestionIcon()
                    )
                    if (confirm == Messages.YES) {
                        BlenderSettings.getInstance(project).removeCustomBlenderPath(inst.originPath)
                        systemTable.refresh()
                        updateSystemButtons()
                    }
                }
            }
        }

        systemActionButtons.add(systemSetupInterpreterButton)
        systemActionButtons.add(systemSetupLinterButton)
        systemActionButtons.add(systemRemoveButton)

        updateSystemButtons()
    }

    private fun updateSystemButtons() {
        val inst = systemTable.getSelectedInstallation()
        if (inst == null) {
            systemSetupInterpreterButton.isEnabled = false
            systemSetupLinterButton.isEnabled = false
            systemRemoveButton.isVisible = false
            return
        }

        systemSetupInterpreterButton.isEnabled = true
        systemSetupLinterButton.isEnabled = true
        systemRemoveButton.isVisible = inst.isCustom
    }

    fun getContent(): JComponent {
        val managedVersionsLabel = JBLabel(LangManager.message("toolwindow.managed.table.title")).apply {
            font = font.deriveFont(Font.BOLD)
        }

        val refreshButton = JButton("", BlenderIcons.Refresh).apply {
            toolTipText = LangManager.message("toolwindow.refresh.tooltip")
            addActionListener {
                managedTable.refresh()
                systemTable.refresh()
            }
        }
        
        val managedVersionsHeader = JPanel(BorderLayout()).apply {
            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
            leftPanel.add(managedVersionsLabel)
            add(leftPanel, BorderLayout.WEST)
            add(refreshButton, BorderLayout.EAST)
        }

        val systemVersionsLabel = JBLabel(LangManager.message("toolwindow.system.table.title")).apply {
            font = font.deriveFont(Font.BOLD)
        }

        val addCustomButton = JButton("", BlenderIcons.Add).apply {
            toolTipText = LangManager.message("toolwindow.system.table.add.custom.tooltip")
            addActionListener {
                val descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
                    .withTitle(LangManager.message("toolwindow.system.modal.select.title"))
                    .withDescription(LangManager.message("toolwindow.system.modal.select.description"))
                
                val file = FileChooser.chooseFile(descriptor, project, null)
                if (file != null) {
                    BlenderSettings.getInstance(project).addCustomBlenderPath(file.path)
                    systemTable.refresh()
                }
            }
        }

        val systemVersionsHeader = JPanel(BorderLayout()).apply {
            add(systemVersionsLabel, BorderLayout.WEST)
            add(addCustomButton, BorderLayout.EAST)
        }

        val sandboxLabel = JBLabel(LangManager.message("toolwindow.sandbox.management.label")).apply {
            font = font.deriveFont(Font.BOLD)
        }
        
        val clearSandboxButton = JButton(LangManager.message("toolwindow.sandbox.clear"), BlenderIcons.Remove).apply {
            addActionListener {
                val confirm = Messages.showYesNoDialog(
                    project,
                    LangManager.message("toolwindow.sandbox.clear.warning"),
                    LangManager.message("toolwindow.sandbox.clear.confirm"),
                    Messages.getQuestionIcon()
                )
                if (confirm == Messages.YES) {
                    if (!commService.isConnected()) {
                        service.clearSandbox()
                        Messages.showInfoMessage(
                            project,
                            LangManager.message("toolwindow.sandbox.clear.success"),
                            LangManager.message("toolwindow.sandbox.clear.success.title")
                        )
                    }
                }
            }
        }
        
        // Use GridBagLayout for vertical expandability of the table lists
        val mainPanel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            fill = GridBagConstraints.BOTH
            gridx = 0
            weightx = 1.0
        }

        // Managed Header
        c.gridy = 0
        c.weighty = 0.0
        c.insets = JBUI.insets(5)
        mainPanel.add(managedVersionsHeader, c)

        // Managed Table (Shortened)
        c.gridy = 1
        c.weighty = 0.0
        c.insets = JBUI.insets(0, 5, 5, 5)
        mainPanel.add(JBScrollPane(managedTable), c)

        // Managed Actions
        c.gridy = 2
        c.weighty = 0.0
        c.insets = JBUI.insets(0, 5, 5, 5)
        mainPanel.add(JBScrollPane(managedActionButtons).apply {
            border = null
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }, c)

        // Managed Progress
        c.gridy = 3
        c.weighty = 0.0
        c.insets = JBUI.insets(0, 10, 5, 10)
        mainPanel.add(managedProgressPanel, c)

        // System Label
        c.gridy = 4
        c.weighty = 0.0
        c.insets = JBUI.insets(15, 5, 5, 5)
        mainPanel.add(systemVersionsHeader, c)

        // System Table (Shortened)
        c.gridy = 5
        c.weighty = 0.0
        c.insets = JBUI.insets(0, 5, 5, 5)
        mainPanel.add(JBScrollPane(systemTable), c)

        // System Actions
        c.gridy = 6
        c.weighty = 0.0
        c.insets = JBUI.insets(0, 5, 5, 5)
        mainPanel.add(JBScrollPane(systemActionButtons).apply {
            border = null
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }, c)

        // System Progress
        c.gridy = 7
        c.weighty = 0.0
        c.insets = JBUI.insets(0, 10, 5, 10)
        mainPanel.add(systemProgressPanel, c)

        // System Path Field
        c.gridy = 8
        c.weighty = 0.0
        c.insets = JBUI.insets(5, 5, 5, 5)
        mainPanel.add(systemPathField, c)

        // Sandbox Section
        c.gridy = 9
        c.weighty = 0.0
        c.insets = JBUI.insets(20, 5, 5, 5)
        mainPanel.add(sandboxLabel, c)

        c.gridy = 10
        c.insets = JBUI.insets(0, 5, 5, 5)
        mainPanel.add(clearSandboxButton, c)

        // Wrap in a BorderLayout panel to respect expansion
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(mainPanel).apply {
            border = null
        }, BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
        
        return panel
    }
}
