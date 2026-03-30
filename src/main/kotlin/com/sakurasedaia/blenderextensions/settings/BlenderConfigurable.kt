package com.sakurasedaia.blenderextensions.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.sakurasedaia.blenderextensions.LangManager
import com.sakurasedaia.blenderextensions.blender.*
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.notifications.BlenderNotification
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.ui.ManagedBlenderTable
import com.sakurasedaia.blenderextensions.ui.SystemBlenderTable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import javax.swing.*

class BlenderConfigurable(private val project: Project) : SearchableConfigurable, Configurable.NoScroll {
    private val service = BlenderService.getInstance(project)
    private val pythonService = PythonService.getInstance(project)
    private val commService = BlenderCommunicationService.getInstance(project)
    
    private var myAutoReloadCheckbox = JBCheckBox(LangManager.message("settings.auto.reload.checkbox"))
    private var myDownloadsDirField = TextFieldWithBrowseButton()
    private var myResetButton = JButton(LangManager.message("settings.reset.defaults"))

    private val managedTable = ManagedBlenderTable(project)
    private val systemTable = SystemBlenderTable(project)

    private val managedActionButtons = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    private val downloadUninstallButton = JButton()
    private val setupLinterButton = JButton(LangManager.message("toolwindow.managed.button.setup.linter"), BlenderIcons.Python)

    private val systemActionButtons = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    private val systemSetupLinterButton = JButton(LangManager.message("toolwindow.managed.button.setup.linter"), BlenderIcons.Python)
    private val systemRemoveButton = JButton("", BlenderIcons.Remove)

    private val managedProgressPanel = JPanel(BorderLayout(5, 2))
    private val managedProgressBar = JProgressBar(0, 100)
    private val managedStatusLabel = JBLabel().apply {
        font = font.deriveFont(11f)
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    private val systemProgressPanel = JPanel(BorderLayout(5, 2))
    private val systemProgressBar = JProgressBar(0, 100)
    private val systemStatusLabel = JBLabel().apply {
        font = font.deriveFont(11f)
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    private val cs = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timer: Timer? = null

    init {
        service.scanInstallations()
        myDownloadsDirField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(LangManager.message("settings.downloads.dir.label"))
        )
        myResetButton.addActionListener {
            resetToDefaults()
        }

        setupManagedButtons()
        setupSystemButtons()
        setupListeners()

        managedProgressPanel.add(managedProgressBar, BorderLayout.CENTER)
        managedProgressPanel.add(managedStatusLabel, BorderLayout.SOUTH)
        managedProgressPanel.isVisible = false

        systemProgressPanel.add(systemProgressBar, BorderLayout.CENTER)
        systemProgressPanel.add(systemStatusLabel, BorderLayout.SOUTH)
        systemProgressPanel.isVisible = false

        val downloader = BlenderDownloader.getInstance(project)
        cs.launch {
            downloader.downloadProgress.collectLatest { progress ->
                SwingUtilities.invokeLater {
                    updateProgress(progress)
                }
            }
        }
    }

    private fun startTimer() {
        if (timer == null) {
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
            timer?.start()
        }
    }

    private fun stopTimer() {
        timer?.stop()
        timer = null
    }

    private fun updateProgress(progress: BlenderDownloader.DownloadProgress) {
        if (!progress.isDownloading) {
            managedProgressPanel.isVisible = false
            systemProgressPanel.isVisible = false
            return
        }

        val inManaged = managedTable.containsVersion(progress.version)
        val progressBar = if (inManaged) managedProgressBar else systemProgressBar
        val statusLabel = if (inManaged) managedStatusLabel else systemStatusLabel
        val panel = if (inManaged) managedProgressPanel else systemProgressPanel

        progressBar.isIndeterminate = progress.progress <= 0
        progressBar.value = (progress.progress * 100).toInt()
        statusLabel.text = progress.statusText
        panel.isVisible = true
    }

    private fun setupListeners() {
        managedTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) updateManagedButtons()
        }
        systemTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) updateSystemButtons()
        }
    }

    private fun setupManagedButtons() {
        managedActionButtons.add(downloadUninstallButton)
        managedActionButtons.add(setupLinterButton)

        downloadUninstallButton.addActionListener {
            val version = managedTable.getSelectedVersion() ?: return@addActionListener
            val isDownloaded = managedTable.isSelectedVersionDownloaded()

            if (isDownloaded) {
                val confirm = Messages.showYesNoDialog(
                    project,
                    LangManager.message("toolwindow.managed.action.delete.description"),
                    LangManager.message("toolwindow.managed.action.delete.title", version),
                    Messages.getQuestionIcon()
                )
                if (confirm == Messages.YES) {
                    if (service.isRunning()) {
                        BlenderNotification(project).sendError(
                            LangManager.message("notification.delete.failed.title", version),
                            LangManager.message("notification.delete.failed.reason.blender.running")
                        )
                    } else {
                        BlenderDownloader.getInstance(project).deleteVersion(version)
                        managedTable.refresh()
                        updateManagedButtons()
                    }
                }
            } else {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, LangManager.message("action.download.blender.task", version)) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        service.getOrDownloadBlenderPath(version)
                        SwingUtilities.invokeLater {
                            managedTable.refresh()
                            updateManagedButtons()
                        }
                    }
                })
            }
        }

        setupLinterButton.addActionListener {
            val version = managedTable.getSelectedVersion() ?: return@addActionListener
            val path = service.getOrDownloadBlenderPath(version)
            if (path != null) {
                pythonService.setupLinter(path)
            }
        }

        updateManagedButtons()
    }

    private fun updateManagedButtons() {
        val version = managedTable.getSelectedVersion()
        if (version == null) {
            downloadUninstallButton.isEnabled = false
            setupLinterButton.isEnabled = false
            return
        }

        val isDownloaded = managedTable.isSelectedVersionDownloaded()
        downloadUninstallButton.isEnabled = true
        downloadUninstallButton.text = if (isDownloaded)
            LangManager.message("toolwindow.managed.button.uninstall")
        else
            LangManager.message("toolwindow.managed.button.download")

        setupLinterButton.isEnabled = isDownloaded
    }

    private fun setupSystemButtons() {
        systemActionButtons.add(systemSetupLinterButton)
        systemActionButtons.add(systemRemoveButton)

        systemSetupLinterButton.addActionListener {
            val inst = systemTable.getSelectedInstallation() ?: return@addActionListener
            pythonService.setupLinter(inst.path)
        }

        systemRemoveButton.addActionListener {
            val inst = systemTable.getSelectedInstallation() ?: return@addActionListener
            if (inst.isCustom && inst.originPath != null) {
                val confirm = Messages.showYesNoDialog(
                    project,
                    LangManager.message("toolwindow.system.table.action.delete.confirm.message"),
                    LangManager.message("toolwindow.table.action.remove"),
                    Messages.getQuestionIcon()
                )
                if (confirm == Messages.YES) {
                    BlenderSettings.getInstance(project).removeCustomBlenderPath(inst.originPath)
                    systemTable.refresh()
                }
            }
        }

        updateSystemButtons()
    }

    private fun updateSystemButtons() {
        val inst = systemTable.getSelectedInstallation()
        if (inst == null) {
            systemSetupLinterButton.isEnabled = false
            systemRemoveButton.isVisible = false
            return
        }

        systemSetupLinterButton.isEnabled = true
        systemRemoveButton.isVisible = inst.isCustom
    }

    private fun resetToDefaults() {
        val systemPath = com.intellij.openapi.application.PathManager.getSystemPath()
        myDownloadsDirField.text = java.nio.file.Path.of(systemPath, "blender_downloads").toString()
    }

    override fun getDisplayName(): String = LangManager.message("settings.display.name")

    override fun getId(): String = "com.sakurasedaia.blenderextensions.settings.BlenderConfigurable"

    override fun createComponent(): JComponent {
        val resetPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        resetPanel.add(myResetButton)

        val managedVersionsLabel = JBLabel(LangManager.message("toolwindow.managed.table.title")).apply {
            font = font.deriveFont(Font.BOLD)
        }
        val managedVersionsHeader = JPanel(BorderLayout()).apply {
            add(managedVersionsLabel, BorderLayout.WEST)
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
                    LangManager.message("toolwindow.sandbox.clear"),
                    LangManager.message("toolwindow.sandbox.clear.confirm"),
                    LangManager.message("button.cancel"),
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

        startTimer()

        return FormBuilder.createFormBuilder()
            .addComponent(myAutoReloadCheckbox)
            .addLabeledComponent(LangManager.message("settings.downloads.dir.label"), myDownloadsDirField)
            .addComponent(resetPanel)
            .addVerticalGap(10)
            .addComponent(managedVersionsHeader)
            .addComponent(JBScrollPane(managedTable).apply {
                preferredSize = Dimension(-1, 150)
            })
            .addComponent(managedActionButtons)
            .addComponent(managedProgressPanel)
            .addVerticalGap(10)
            .addComponent(systemVersionsHeader)
            .addComponent(JBScrollPane(systemTable).apply {
                preferredSize = Dimension(-1, 100)
            })
            .addComponent(systemActionButtons)
            .addComponent(systemProgressPanel)
            .addVerticalGap(10)
            .addComponent(sandboxLabel)
            .addComponent(clearSandboxButton)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun disposeUIResources() {
        stopTimer()
        cs.cancel()
    }

    override fun isModified(): Boolean {
        val settings = BlenderSettings.getInstance(project).state
        return myAutoReloadCheckbox.isSelected != settings.autoReload ||
                myDownloadsDirField.text != settings.downloadsPath
    }

    override fun apply() {
        val settings = BlenderSettings.getInstance(project).state
        val oldDownloadsPath = settings.downloadsPath
        val newDownloadsPath = myDownloadsDirField.text

        settings.autoReload = myAutoReloadCheckbox.isSelected

        if (oldDownloadsPath != newDownloadsPath) {
            com.sakurasedaia.blenderextensions.system.MigrationUtil.migrate(project, oldDownloadsPath, newDownloadsPath) { finalPath ->
                settings.downloadsPath = finalPath
                myDownloadsDirField.text = finalPath
            }
        }
    }

    override fun reset() {
        val settings = BlenderSettings.getInstance(project).state
        myAutoReloadCheckbox.isSelected = settings.autoReload
        myDownloadsDirField.text = settings.downloadsPath
    }
}
