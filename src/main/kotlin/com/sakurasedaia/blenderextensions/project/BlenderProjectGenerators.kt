package com.sakurasedaia.blenderextensions.project

import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.LangManager
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.UIUtil
import javax.swing.event.DocumentEvent
import javax.swing.SwingUtilities
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.sakurasedaia.blenderextensions.blender.*
import com.sakurasedaia.blenderextensions.system.*
import com.sakurasedaia.blenderextensions.run.*
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.settings.BlenderSettings
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPanel
import java.lang.ref.WeakReference
import kotlin.io.path.writeText


internal fun formatToId(name: String, allowCapitals: Boolean = false): String {
    val replaced = name.replace(Regex("\\s+"), "_")
    return if (allowCapitals) {
        replaced.replace(Regex("[^a-zA-Z0-9_]"), "")
    } else {
        replaced.lowercase().replace(Regex("[^a-z0-9_]"), "")
    }
}

class BlenderAddonProjectGenerator : DirectoryProjectGenerator<BlenderAddonProjectSettings> {
    private var myPeerReference = WeakReference<BlenderAddonProjectPeer>(null)

    override fun getName(): String = LangManager.message("project.template.name")
    override fun getLogo(): Icon = BlenderIcons.Blender

    override fun createPeer(): ProjectGeneratorPeer<BlenderAddonProjectSettings> {
        val peer = BlenderAddonProjectPeer()
        myPeerReference = WeakReference(peer)
        return peer
    }

    override fun validate(baseDirPath: String): ValidationResult {
        myPeerReference.get()?.updateLocation(baseDirPath)
        return ValidationResult.OK
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: BlenderAddonProjectSettings, module: Module) {
        val projectPath = Path.of(baseDir.path)
        val projectName = settings.projectName?.takeIf { it.isNotBlank() } ?: project.name
        val authorName = settings.addonMaintainer ?: System.getProperty("user.name") ?: "Author"
        val addonId = settings.addonId?.takeIf { it.isNotBlank() } ?: formatToId(projectName)
        val selectedVersion = settings.blenderVersion ?: "5.0"

        val blenderVersionMin = settings.blenderVersionMin ?: if (BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == selectedVersion }) {
            val patch = BlenderVersions.SUPPORTED_VERSIONS.find { it.majorMinor == selectedVersion }?.fallbackPatch ?: "0"
            "$selectedVersion.$patch"
        } else {
            // It might be a path. Try to get version from it.
            val detected = BlenderFinder.tryGetVersion(selectedVersion)
            if (detected != LangManager.message("blender.version.unknown")) {
                detected + ".0" // BlenderScanner returns X.Y, we need X.Y.Z
            } else {
                "4.2.0" // Default fallback
            }
        }

        val srcDir = projectPath.resolve("src")
        Files.createDirectories(srcDir)
        BlenderSettings.getInstance(project).addSourceFolder(srcDir.toAbsolutePath().toString().replace("\\", "/"))

        val permissionsMap = mutableMapOf<String, String>()
        if (settings.permissionNetwork && !settings.permissionNetworkReason.isNullOrBlank()) permissionsMap["network"] = settings.permissionNetworkReason!!
        if (settings.permissionFiles && !settings.permissionFilesReason.isNullOrBlank()) permissionsMap["files"] = settings.permissionFilesReason!!
        if (settings.permissionClipboard && !settings.permissionClipboardReason.isNullOrBlank()) permissionsMap["clipboard"] = settings.permissionClipboardReason!!
        if (settings.permissionCamera && !settings.permissionCameraReason.isNullOrBlank()) permissionsMap["camera"] = settings.permissionCameraReason!!
        if (settings.permissionMicrophone && !settings.permissionMicrophoneReason.isNullOrBlank()) permissionsMap["microphone"] = settings.permissionMicrophoneReason!!

        val manifestSettings = BlenderManifestSettings(
            id = addonId,
            name = projectName,
            tagline = settings.addonTagline ?: "A Blender extension",
            maintainer = authorName,
            website = settings.addonWebsite,
            tags = settings.addonTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            blenderVersionMin = blenderVersionMin,
            blenderVersionMax = settings.blenderVersionMax,
            platforms = settings.addonPlatforms?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            permissions = if (permissionsMap.isNotEmpty()) permissionsMap else null,
            buildPathsExcludePattern = settings.buildPathsExcludePattern?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        )
        srcDir.resolve("blender_manifest.toml").writeText(
            BlenderProjectTemplateGenerator.generateManifest(manifestSettings)
        )
        projectPath.resolve("LICENSE").writeText(BlenderProjectTemplateGenerator.generateLicense())
        projectPath.resolve("README.md").writeText(BlenderProjectTemplateGenerator.generateReadme(
            name = manifestSettings.name,
            author = manifestSettings.maintainer,
            tagline = manifestSettings.tagline
        ))
        projectPath.resolve(".gitignore").writeText(BlenderProjectTemplateGenerator.generateGitignore())

        if (settings.agentGuidelines) {
            val agentDir = projectPath.resolve(".agent")
            val skillsDir = agentDir.resolve("skills")
            Files.createDirectories(skillsDir)

            agentDir.resolve("guidelines.md").writeText(
                BlenderProjectTemplateGenerator.generateAgentGuidelines()
            )
            agentDir.resolve("project.md").writeText(
                BlenderProjectTemplateGenerator.generateAgentProject(projectName)
            )
            agentDir.resolve("context.md").writeText(
                BlenderProjectTemplateGenerator.generateAgentContext()
            )

            val skills = listOf("blender_extension_dev", "python_practices", "git_management", "ai_workflow")
            for (skill in skills) {
                skillsDir.resolve("$skill.md").writeText(
                    BlenderProjectTemplateGenerator.generateAgentSkill(skill)
                )
            }
        }

        if (settings.enableAutoLoad) {
            srcDir.resolve("__init__.py").writeText(
                BlenderProjectTemplateGenerator.generateAutoLoadInit(projectName, authorName)
            )
            srcDir.resolve("auto_load.py").writeText(
                BlenderProjectTemplateGenerator.getAutoLoadContent()
            )
        } else {
            srcDir.resolve("__init__.py").writeText(
                BlenderProjectTemplateGenerator.generateSimpleInit(projectName, authorName)
            )
        }

        // Automatically create Blender Run Configurations: Start Blender, Build, and Validate
        val runManager = RunManager.getInstance(project)
        val configType = ConfigurationTypeUtil.findConfigurationType(BlenderRunConfigurationType::class.java)
        
        // 1. Start Blender
        val startBlenderFactory = configType.configurationFactories.find { it is BlenderStartBlenderConfigurationFactory }
        if (startBlenderFactory != null) {
            val runSettings = runManager.createConfiguration("Start Blender", startBlenderFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            val options = runConfig.getOptions()
            options.blenderVersion = selectedVersion
            options.isSandboxed = true
            options.addonSourceDirectory = srcDir.toAbsolutePath().toString()
            options.addonSymlinkName = addonId
            runManager.addConfiguration(runSettings)
            runManager.selectedConfiguration = runSettings
        }

        // 2. Build
        val buildFactory = configType.configurationFactories.find { it is BlenderBuildConfigurationFactory }
        if (buildFactory != null) {
            val runSettings = runManager.createConfiguration("Build", buildFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            runConfig.getOptions().blenderVersion = selectedVersion
            runManager.addConfiguration(runSettings)
        }

        // 3. Validate
        val validateFactory = configType.configurationFactories.find { it is BlenderValidateConfigurationFactory }
        if (validateFactory != null) {
            val runSettings = runManager.createConfiguration("Validate", validateFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            runConfig.getOptions().blenderVersion = selectedVersion
            runManager.addConfiguration(runSettings)
        }

        if (settings.createGitRepo) {
            try {
                com.intellij.execution.configurations.GeneralCommandLine("git", "init")
                    .withWorkDirectory(projectPath.toFile())
                    .createProcess()
                    .waitFor()
            } catch (_: Exception) {}
        }

        // 4. Automatic Python Interpreter Setup
        try {
            val blenderPath = if (BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == selectedVersion }) {
                BlenderDownloader.getInstance(project).getOrDownloadBlenderPath(selectedVersion)
            } else {
                // It's likely a custom path if not in SUPPORTED_VERSIONS
                selectedVersion
            }
            
            if (blenderPath != null && Files.exists(Path.of(blenderPath))) {
                BlenderLogger.getInstance(project).log("Initiating Python SDK setup for Blender path: $blenderPath")
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    PythonService.getInstance(project).setupPythonInterpreter(blenderPath)
                }
            } else {
                BlenderLogger.getInstance(project).log("Python SDK setup skipped: Blender path not found ($blenderPath)")
            }
        } catch (e: Exception) {
            BlenderLogger.getInstance(project).error("Failed to initiate Python SDK setup during project generation", e)
        }
    }
}

data class BlenderAddonProjectSettings(
    var enableAutoLoad: Boolean = false,
    var projectName: String? = null,
    var addonId: String? = null,
    var addonTagline: String? = LangManager.message("project.template.default.tagline"),
    var addonMaintainer: String? = System.getProperty("user.name") ?: "Author",
    var addonWebsite: String? = null,
    var addonTags: String? = null,
    var blenderVersionMin: String? = "4.2.0",
    var blenderVersionMax: String? = null,
    var blenderVersion: String? = "5.0",
    var addonPlatforms: String? = null,
    var permissionNetwork: Boolean = false,
    var permissionNetworkReason: String? = null,
    var permissionFiles: Boolean = false,
    var permissionFilesReason: String? = null,
    var permissionClipboard: Boolean = false,
    var permissionClipboardReason: String? = null,
    var permissionCamera: Boolean = false,
    var permissionCameraReason: String? = null,
    var permissionMicrophone: Boolean = false,
    var permissionMicrophoneReason: String? = null,
    var buildPathsExcludePattern: String? = null,
    var createGitRepo: Boolean = false,
    val agentGuidelines: Boolean,
    val sandbox: Boolean = true
    
)

internal class BlenderAddonProjectPeer : ProjectGeneratorPeer<BlenderAddonProjectSettings> {
    private var projectLocation: String? = null
    private var isUpdating = false
    private var addonIdIsManual = false
    @Suppress("DEPRECATION")
    private val stateListeners = mutableListOf<com.intellij.platform.WebProjectGenerator.SettingsStateListener>()

    private fun fireStateChanged() {
        if (!isUpdating) {
            stateListeners.forEach { it.stateChanged(true) }
        }
    }

    fun updateLocation(path: String) {
        if (projectLocation == path) return
        projectLocation = path

        if (!isUpdating) {
            val nameFromPath = try { Path.of(path).fileName?.toString() } catch (_: Exception) { null }
            if (!nameFromPath.isNullOrEmpty() && projectNameField.text != nameFromPath) {
                isUpdating = true
                try {
                    projectNameField.text = nameFromPath
                    if (!addonIdIsManual) {
                        addonIdField.text = formatToId(nameFromPath, allowCapitals = false)
                    }
                } finally {
                    isUpdating = false
                }
                fireStateChanged()
            }
        }
    }

    private val autoLoadCheckbox = JBCheckBox("Add automatic module/class registration script", false)
    private val includeAgentGuidelines = JBCheckBox("Append pre-made agent guidelines", true)
    private val createGitRepoCheckbox = JBCheckBox("Create Git repository", false)
    private val sandboxEnvironment = JBCheckBox("Enable sandbox environment", true)
    internal val projectNameField = JBTextField()
    internal val addonIdField = JBTextField()
    internal val addonTaglineField = JBTextField("A Blender extension")
    internal val addonMaintainerField = JBTextField(System.getProperty("user.name") ?: "Author")
    internal val addonWebsiteField = JBTextField()
    internal val addonTagsField = JBTextField()
    internal val blenderVersionComboBox = com.intellij.openapi.ui.ComboBox<String>()
    internal val blenderDownloadButton = javax.swing.JButton(LangManager.message("run.configuration.button.download"), BlenderIcons.Install)
    internal val blenderVersionMinField = JBTextField("5.0.0")
    internal val blenderVersionMaxField = JBTextField()
    internal val addonPlatformsField = JBTextField()

    internal val permissionNetworkCheckbox = JBCheckBox("Network access", false)
    internal val permissionNetworkReasonField = JBTextField()
    internal val permissionFilesCheckbox = JBCheckBox("Filesystem access", false)
    internal val permissionFilesReasonField = JBTextField()
    internal val permissionClipboardCheckbox = JBCheckBox("Clipboard access", false)
    internal val permissionClipboardReasonField = JBTextField()
    internal val permissionCameraCheckbox = JBCheckBox("Camera access", false)
    internal val permissionCameraReasonField = JBTextField()
    internal val permissionMicrophoneCheckbox = JBCheckBox("Microphone access", false)
    internal val permissionMicrophoneReasonField = JBTextField()

    internal val buildPathsExcludePatternField = JBTextField()

    private val panel: JPanel

    init {
        val allFields = listOf(
            projectNameField,
            addonIdField,
            addonTaglineField,
            addonMaintainerField,
            addonWebsiteField,
            addonTagsField,
            blenderVersionMinField,
            blenderVersionMaxField,
            addonPlatformsField,
            buildPathsExcludePatternField,
            permissionNetworkReasonField,
            permissionFilesReasonField,
            permissionClipboardReasonField,
            permissionCameraReasonField,
            permissionMicrophoneReasonField
        )

        allFields.forEach { field ->
            field.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent?) {
                    fireStateChanged()
                }
            })
        }

        projectNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (isUpdating) return
                val original = projectNameField.text
                val formatted = formatToId(original, allowCapitals = true)

                if (original != formatted) {
                    isUpdating = true
                    SwingUtilities.invokeLater {
                        try {
                            val caret = projectNameField.caretPosition
                            projectNameField.text = formatted
                            try {
                                projectNameField.caretPosition = Math.min(caret, formatted.length)
                            } catch (_: Exception) {}
                            updateLocationFromProjectName()
                            if (!addonIdIsManual) {
                                addonIdField.text = formatToId(formatted, allowCapitals = false)
                            }
                        } finally {
                            isUpdating = false
                        }
                        fireStateChanged()
                    }
                } else {
                    isUpdating = true
                    try {
                        updateLocationFromProjectName()
                        if (!addonIdIsManual) {
                            addonIdField.text = formatToId(formatted, allowCapitals = false)
                        }
                    } finally {
                        isUpdating = false
                    }
                    fireStateChanged()
                }
            }
        })

        addonIdField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (isUpdating) return
                
                val original = addonIdField.text
                val formatted = formatToId(original, allowCapitals = false)

                if (original != formatted) {
                    isUpdating = true
                    SwingUtilities.invokeLater {
                        try {
                            val caret = addonIdField.caretPosition
                            addonIdField.text = formatted
                            try {
                                addonIdField.caretPosition = Math.min(caret, formatted.length)
                            } catch (_: Exception) {}
                            addonIdIsManual = formatted.isNotBlank()
                        } finally {
                            isUpdating = false
                        }
                        fireStateChanged()
                    }
                } else {
                    addonIdIsManual = original.isNotBlank()
                    fireStateChanged()
                }
            }
        })

        // Setup blender version combo box
        val downloader = BlenderDownloader.getInstance(com.intellij.openapi.project.ProjectManager.getInstance().defaultProject)
        val versions = BlenderVersions.getAllSelectableVersions()
        blenderVersionComboBox.model = javax.swing.DefaultComboBoxModel(versions.toTypedArray())
        blenderVersionComboBox.selectedItem = "5.0"
        
        fun updateMinVersion() {
            val selected = blenderVersionComboBox.selectedItem as? String ?: return
            val supported = BlenderVersions.SUPPORTED_VERSIONS.find { it.majorMinor == selected }
            val minVer = if (supported != null) {
                "${supported.majorMinor}.${supported.fallbackPatch}"
            } else {
                val detected = BlenderFinder.tryGetVersion(selected)
                if (detected != LangManager.message("blender.version.unknown")) {
                    detected + ".0"
                } else {
                    "4.2.0"
                }
            }
            blenderVersionMinField.text = minVer
        }
        updateMinVersion()

        blenderVersionComboBox.addActionListener { 
            updateMinVersion()
            fireStateChanged()
            updateDownloadButtonVisibility()
        }

        blenderDownloadButton.addActionListener {
            val selected = blenderVersionComboBox.selectedItem as? String ?: return@addActionListener
            if (!downloader.isDownloaded(selected)) {
                com.intellij.openapi.progress.ProgressManager.getInstance().run(object : com.intellij.openapi.progress.Task.Backgroundable(null, LangManager.message("action.download.blender.task", selected)) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        downloader.getOrDownloadBlenderPath(selected)
                        SwingUtilities.invokeLater {
                            updateDownloadButtonVisibility()
                        }
                    }
                })
            }
        }
        updateDownloadButtonVisibility()

        // Reason fields should be disabled if checkbox is not selected
        permissionNetworkReasonField.isEnabled = false
        permissionNetworkCheckbox.addActionListener {
            permissionNetworkReasonField.isEnabled = permissionNetworkCheckbox.isSelected
            fireStateChanged()
        }
        permissionFilesReasonField.isEnabled = false
        permissionFilesCheckbox.addActionListener {
            permissionFilesReasonField.isEnabled = permissionFilesCheckbox.isSelected
            fireStateChanged()
        }
        permissionClipboardReasonField.isEnabled = false
        permissionClipboardCheckbox.addActionListener {
            permissionClipboardReasonField.isEnabled = permissionClipboardCheckbox.isSelected
            fireStateChanged()
        }
        permissionCameraReasonField.isEnabled = false
        permissionCameraCheckbox.addActionListener {
            permissionCameraReasonField.isEnabled = permissionCameraCheckbox.isSelected
            fireStateChanged()
        }
        permissionMicrophoneReasonField.isEnabled = false
        permissionMicrophoneCheckbox.addActionListener {
            permissionMicrophoneReasonField.isEnabled = permissionMicrophoneCheckbox.isSelected
            fireStateChanged()
        }

        autoLoadCheckbox.addActionListener { fireStateChanged() }
        includeAgentGuidelines.addActionListener { fireStateChanged() }
        createGitRepoCheckbox.addActionListener { fireStateChanged() }

        // Add listeners to other fields to trigger validation
        listOf(
            addonTaglineField,
            addonMaintainerField,
            addonWebsiteField,
            addonTagsField,
            blenderVersionMinField,
            blenderVersionMaxField,
            addonPlatformsField,
            buildPathsExcludePatternField
        ).forEach { field ->
            field.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = fireStateChanged()
            })
        }

        sandboxEnvironment.addActionListener { fireStateChanged() }

        // Enforce 64-char max for permission reasons
        fun enforceMax64(tf: JBTextField) {
            tf.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    val t = tf.text
                    if (t.length > 64) {
                        val caret = tf.caretPosition
                        tf.text = t.substring(0, 64)
                        try { tf.caretPosition = minOf(caret, 64) } catch (_: Exception) {}
                    }
                    fireStateChanged()
                }
            })
        }
        listOf(
            permissionNetworkReasonField,
            permissionFilesReasonField,
            permissionClipboardReasonField,
            permissionCameraReasonField,
            permissionMicrophoneReasonField
        ).forEach { enforceMax64(it) }

        val builder = FormBuilder.createFormBuilder()
            .addComponent(autoLoadCheckbox)
            .addComponent(includeAgentGuidelines)
            .addComponent(createGitRepoCheckbox)
            .addComponent(sandboxEnvironment)
            .addSeparator()
            .addLabeledComponent("Project name:", projectNameField)
            .addLabeledComponent("Addon ID:", addonIdField)
            .addLabeledComponent("Tagline:", addonTaglineField)
            .addLabeledComponent("Maintainer:", addonMaintainerField)
            .addLabeledComponent("Website (Optional):", addonWebsiteField)
            .addLabeledComponent("Tags (comma separated, Optional):", addonTagsField)
            
        val versionPanel = JPanel(java.awt.BorderLayout())
        versionPanel.add(blenderVersionComboBox, java.awt.BorderLayout.CENTER)
        versionPanel.add(blenderDownloadButton, java.awt.BorderLayout.EAST)
        
        builder.addLabeledComponent("Blender version:", versionPanel)
        builder.addLabeledComponent("Min blender version:", blenderVersionMinField)
        builder.addLabeledComponent("Max blender version (optional):", blenderVersionMaxField)

        panel = builder.addLabeledComponent("Platforms (comma separated, optional):", addonPlatformsField)
            .addSeparator()
            .addLabeledComponent("Permissions (optional):", JPanel()) // Placeholder for header
            .addComponent(permissionNetworkCheckbox)
            .addLabeledComponent("  Reason (required if checked, max 64 chars):", permissionNetworkReasonField)
            .addComponent(permissionFilesCheckbox)
            .addLabeledComponent("  Reason (required if checked, max 64 chars):", permissionFilesReasonField)
            .addComponent(permissionClipboardCheckbox)
            .addLabeledComponent("  Reason (required if checked, max 64 chars):", permissionClipboardReasonField)
            .addComponent(permissionCameraCheckbox)
            .addLabeledComponent("  Reason (required if checked, max 64 chars):", permissionCameraReasonField)
            .addComponent(permissionMicrophoneCheckbox)
            .addLabeledComponent("  Reason (required if checked, max 64 chars):", permissionMicrophoneReasonField)
            .addSeparator()
            .addLabeledComponent("Build exclude patterns (optional):", buildPathsExcludePatternField)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Tooltips/Hints
        addonIdField.toolTipText = "Kebab-case, alphanumeric, 3-32 characters"
        addonPlatformsField.toolTipText = "windows-x64, macos-arm64, linux-x64, windows-arm64, macos-x64"
        blenderVersionComboBox.toolTipText = "Select the Blender version to use for development"
        blenderVersionMinField.toolTipText = "Minimum Blender version required by the extension (x.y.z)"
    }

    private fun updateDownloadButtonVisibility() {
        val selected = blenderVersionComboBox.selectedItem as? String
        val downloader = BlenderDownloader.getInstance(com.intellij.openapi.project.ProjectManager.getInstance().defaultProject)
        blenderDownloadButton.isVisible = selected != null && BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == selected } && !downloader.isDownloaded(selected)
    }

    private fun updateLocationFromProjectName() {
        val name = projectNameField.text.trim()
        if (name.isEmpty() || projectLocation == null) return

        val path = try { Path.of(projectLocation!!) } catch (_: Exception) { return }
        val parent = path.parent ?: return
        val newPath = parent.resolve(name).toAbsolutePath().toString()

        if (newPath != projectLocation) {
            findLocationField()?.let {
                if (it.text != newPath) it.text = newPath
            }
        }
    }

    private fun findLocationField(): TextFieldWithBrowseButton? {
        var current: java.awt.Component? = panel
        while (current != null) {
            val parent = current.parent
            if (parent is javax.swing.JComponent) {
                val fields = UIUtil.findComponentsOfType(parent, TextFieldWithBrowseButton::class.java)
                for (field in fields) {
                    if (!SwingUtilities.isDescendingFrom(field, panel)) {
                        return field
                    }
                }
            }
            current = parent
        }
        return null
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getComponent(): javax.swing.JComponent = panel

    @Suppress("OVERRIDE_DEPRECATION")
    override fun buildUI(settingsStep: com.intellij.ide.util.projectWizard.SettingsStep) {
        settingsStep.addSettingsComponent(component)
    }

    override fun getSettings(): BlenderAddonProjectSettings = BlenderAddonProjectSettings(
        enableAutoLoad = autoLoadCheckbox.isSelected,
        agentGuidelines = includeAgentGuidelines.isSelected,
        projectName = projectNameField.text?.trim(),
        addonId = addonIdField.text?.trim(),
        addonTagline = addonTaglineField.text?.trim(),
        addonMaintainer = addonMaintainerField.text?.trim(),
        addonWebsite = addonWebsiteField.text?.trim(),
        addonTags = addonTagsField.text?.trim(),
        blenderVersion = blenderVersionComboBox.selectedItem as? String,
        blenderVersionMin = blenderVersionMinField.text?.trim(),
        blenderVersionMax = blenderVersionMaxField.text?.trim(),
        addonPlatforms = addonPlatformsField.text?.trim(),
        permissionNetwork = permissionNetworkCheckbox.isSelected,
        permissionNetworkReason = permissionNetworkReasonField.text?.trim(),
        permissionFiles = permissionFilesCheckbox.isSelected,
        permissionFilesReason = permissionFilesReasonField.text?.trim(),
        permissionClipboard = permissionClipboardCheckbox.isSelected,
        permissionClipboardReason = permissionClipboardReasonField.text?.trim(),
        permissionCamera = permissionCameraCheckbox.isSelected,
        permissionCameraReason = permissionCameraReasonField.text?.trim(),
        permissionMicrophone = permissionMicrophoneCheckbox.isSelected,
        permissionMicrophoneReason = permissionMicrophoneReasonField.text?.trim(),
        buildPathsExcludePattern = buildPathsExcludePatternField.text?.trim(),
        createGitRepo = createGitRepoCheckbox.isSelected,
        sandbox = sandboxEnvironment.isSelected
    )

    override fun validate(): ValidationInfo? {
        // 1. Project Name
        val projectName = projectNameField.text?.trim().orEmpty()
        if (projectName.isEmpty()) {
            return ValidationInfo("Project name cannot be empty.", projectNameField)
        }

        // 2. Addon ID
        val id = addonIdField.text?.trim().orEmpty()
        if (id.isEmpty()) {
            return ValidationInfo("Addon ID cannot be empty.", addonIdField)
        }
        val idRegex = Regex("^[a-z0-9_]{3,32}$")
        if (!idRegex.matches(id)) {
            if (id.length < 3) return ValidationInfo("Addon ID is too short (min 3 characters).", addonIdField)
            if (id.length > 32) return ValidationInfo("Addon ID is too long (max 32 characters).", addonIdField)
            return ValidationInfo("Addon ID must be snake-case (lowercase letters, underscores only).", addonIdField)
        }

        // 3. Blender Version
        val selectedVersion = blenderVersionComboBox.selectedItem as? String
        if (selectedVersion.isNullOrBlank()) {
            return ValidationInfo("Please select a Blender version.", blenderVersionComboBox)
        }

        val semver = Regex("^\\d+\\.\\d+\\.\\d+")

        val minVer = blenderVersionMinField.text?.trim().orEmpty()
        if (minVer.isEmpty()) {
            return ValidationInfo("Minimum Blender version cannot be empty.", blenderVersionMinField)
        }
        if (!semver.matches(minVer)) {
            return ValidationInfo("Minimum Blender version must be in format x.y.z (e.g., 5.0.0).", blenderVersionMinField)
        }

        val maxVer = blenderVersionMaxField.text?.trim().orEmpty()
        if (maxVer.isNotEmpty() && !semver.matches(maxVer)) {
            return ValidationInfo("Maximum Blender version must be in format x.y.z (e.g., 5.0.0).", blenderVersionMaxField)
        }

        // 4. Website URL
        val website = addonWebsiteField.text?.trim().orEmpty()
        if (website.isNotEmpty()) {
            val urlRegex = Regex("^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")
            if (!urlRegex.matches(website)) {
                return ValidationInfo("Please enter a valid URL (e.g., https://example.com).", addonWebsiteField)
            }
        }

        // 5. Permissions
        fun checkReason(checkbox: JBCheckBox, field: JBTextField, label: String): ValidationInfo? {
            if (checkbox.isSelected) {
                val reason = field.text?.trim().orEmpty()
                if (reason.isEmpty()) return ValidationInfo("Permission '$label' requires a reason.", field)
                if (reason.length > 64) return ValidationInfo("Reason for '$label' must be 64 characters or fewer.", field)
                if (reason.endsWith('.')) return ValidationInfo("Reason for '$label' should not end with a period (.).", field)
            }
            return null
        }
        checkReason(permissionNetworkCheckbox, permissionNetworkReasonField, "Network")?.let { return it }
        checkReason(permissionFilesCheckbox, permissionFilesReasonField, "Files")?.let { return it }
        checkReason(permissionClipboardCheckbox, permissionClipboardReasonField, "Clipboard")?.let { return it }
        checkReason(permissionCameraCheckbox, permissionCameraReasonField, "Camera")?.let { return it }
        checkReason(permissionMicrophoneCheckbox, permissionMicrophoneReasonField, "Microphone")?.let { return it }

        // 6. Platforms
        val allowedPlatforms = setOf("windows-x64", "macos-arm64", "linux-x64", "windows-arm64", "macos-x64", "linux-arm64")
        val platforms = addonPlatformsField.text?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        for (platform in platforms) {
            if (platform !in allowedPlatforms) {
                return ValidationInfo("Unsupported platform: $platform. Allowed: windows-x64, macos-arm64, linux-x64, etc.", addonPlatformsField)
            }
        }

        return null
    }

    override fun isBackgroundJobRunning(): Boolean = false

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun addSettingsStateListener(listener: com.intellij.platform.WebProjectGenerator.SettingsStateListener) {
        stateListeners.add(listener)
    }
}
