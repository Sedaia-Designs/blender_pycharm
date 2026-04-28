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
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.common.BlenderProjectPaths
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.UIUtil
import javax.swing.event.DocumentEvent
import javax.swing.SwingUtilities
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.sakurasedaia.blenderextensions.blender.model.*
import com.sakurasedaia.blenderextensions.blender.services.*
import com.sakurasedaia.blenderextensions.blender.utils.*
import com.sakurasedaia.blenderextensions.common.utils.*
import com.sakurasedaia.blenderextensions.run.*
import com.sakurasedaia.blenderextensions.python.PythonService
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPanel
import java.lang.ref.WeakReference
import kotlin.io.path.writeText
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.blender.services.BlenderDownloader
import com.sakurasedaia.blenderextensions.blender.services.BlenderFinder
import com.sakurasedaia.blenderextensions.blender.services.BlenderScanner


internal fun formatToId(name: String, allowCapitals: Boolean = false, allowSpaces: Boolean = false, trim: Boolean = true): String {
    val sb = StringBuilder()
    var lastWasSpace = false
    for (char in name) {
        if (char.isWhitespace()) {
            if (allowSpaces) {
                sb.append(' ')
            } else if (!lastWasSpace && sb.isNotEmpty()) {
                sb.append('_')
                lastWasSpace = true
            }
        } else {
            val processedChar = if (allowCapitals) char else char.lowercaseChar()
            if (processedChar.isLetterOrDigit() || processedChar == '_') {
                sb.append(processedChar)
                lastWasSpace = false
            }
        }
    }
    val result = sb.toString()
    return if (trim) {
        if (allowSpaces) result.trim() else result.trim('_')
    } else {
        result
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

    @Suppress("DEPRECATION")
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

        val srcDir = BlenderProjectPaths.getSrcDir(project)
        Files.createDirectories(srcDir)
        BlenderSettings.getInstance(project).addSourceFolder(srcDir.toAbsolutePath().toString().replace("\\", "/"))

        val permissionsMap = mutableMapOf<String, String>()
        if (settings.permissionNetwork) settings.permissionNetworkReason?.let { permissionsMap["network"] = it }
        if (settings.permissionFiles) settings.permissionFilesReason?.let { permissionsMap["files"] = it }
        if (settings.permissionClipboard) settings.permissionClipboardReason?.let { permissionsMap["clipboard"] = it }
        if (settings.permissionCamera) settings.permissionCameraReason?.let { permissionsMap["camera"] = it }
        if (settings.permissionMicrophone) settings.permissionMicrophoneReason?.let { permissionsMap["microphone"] = it }

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
        srcDir.resolve(BlenderProjectPaths.MANIFEST_NAME).writeText(
            BlenderProjectTemplateGenerator.generateManifest(manifestSettings)
        )
        projectPath.resolve(BlenderProjectPaths.LICENSE_NAME).writeText(BlenderProjectTemplateGenerator.generateLicense())
        projectPath.resolve(BlenderProjectPaths.README_NAME).writeText(BlenderProjectTemplateGenerator.generateReadme(
            name = manifestSettings.name,
            author = manifestSettings.maintainer,
            tagline = manifestSettings.tagline
        ))
        projectPath.resolve(BlenderProjectPaths.GITIGNORE_NAME).writeText(BlenderProjectTemplateGenerator.generateGitignore())

        if (settings.agentGuidelines) {
            val agentDir = BlenderProjectPaths.getAgentDir(project)
            val skillsDir = BlenderProjectPaths.getSkillsDir(project)
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
            srcDir.resolve(BlenderProjectPaths.INIT_PY_NAME).writeText(
                BlenderProjectTemplateGenerator.generateAutoLoadInit(projectName, authorName)
            )
            srcDir.resolve(BlenderProjectPaths.AUTO_LOAD_PY_NAME).writeText(
                BlenderProjectTemplateGenerator.getAutoLoadContent()
            )
        } else {
            srcDir.resolve(BlenderProjectPaths.INIT_PY_NAME).writeText(
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
            options.isSandboxed = settings.sandbox
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
                val formatted = formatToId(original, allowCapitals = true, allowSpaces = true, trim = false)

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
                                addonIdField.text = formatToId(formatted.trim(), allowCapitals = false)
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
                            addonIdField.text = formatToId(formatted.trim(), allowCapitals = false)
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
                BlenderTaskManager.getInstance().run(null, LangManager.message("action.download.blender.task", selected)) {
                    downloader.getOrDownloadBlenderPath(selected)
                    SwingUtilities.invokeLater {
                        updateDownloadButtonVisibility()
                    }
                }
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

        panel = panel {
            row {
                cell(autoLoadCheckbox)
            }
            row {
                cell(includeAgentGuidelines)
            }
            row {
                cell(createGitRepoCheckbox)
            }
            row {
                cell(sandboxEnvironment)
            }
            separator()
            row("Project name:") {
                cell(projectNameField).align(AlignX.FILL)
            }
            row("Addon ID:") {
                cell(addonIdField).align(AlignX.FILL)
            }
            row("Tagline:") {
                cell(addonTaglineField).align(AlignX.FILL)
            }
            row("Maintainer:") {
                cell(addonMaintainerField).align(AlignX.FILL)
            }
            row("Website (Optional):") {
                cell(addonWebsiteField).align(AlignX.FILL)
            }
            row("Tags (comma separated, Optional):") {
                cell(addonTagsField).align(AlignX.FILL)
            }

            row("Blender version:") {
                cell(blenderVersionComboBox).align(AlignX.FILL).resizableColumn()
                cell(blenderDownloadButton)
            }
            row("Min blender version:") {
                cell(blenderVersionMinField).align(AlignX.FILL)
            }
            row("Max blender version (optional):") {
                cell(blenderVersionMaxField).align(AlignX.FILL)
            }
            row("Platforms (comma separated, optional):") {
                cell(addonPlatformsField).align(AlignX.FILL)
            }

            separator()
            group("Permissions (optional)") {
                row {
                    cell(permissionNetworkCheckbox)
                }
                row("  Reason (required if checked, max 64 chars):") {
                    cell(permissionNetworkReasonField).align(AlignX.FILL)
                }
                row {
                    cell(permissionFilesCheckbox)
                }
                row("  Reason (required if checked, max 64 chars):") {
                    cell(permissionFilesReasonField).align(AlignX.FILL)
                }
                row {
                    cell(permissionClipboardCheckbox)
                }
                row("  Reason (required if checked, max 64 chars):") {
                    cell(permissionClipboardReasonField).align(AlignX.FILL)
                }
                row {
                    cell(permissionCameraCheckbox)
                }
                row("  Reason (required if checked, max 64 chars):") {
                    cell(permissionCameraReasonField).align(AlignX.FILL)
                }
                row {
                    cell(permissionMicrophoneCheckbox)
                }
                row("  Reason (required if checked, max 64 chars):") {
                    cell(permissionMicrophoneReasonField).align(AlignX.FILL)
                }
            }
            separator()
            row("Build exclude patterns (optional):") {
                cell(buildPathsExcludePatternField).align(AlignX.FILL)
            }
        }

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

        // Replace spaces with underscores for the directory name
        val safeDirectoryName = formatToId(name, allowCapitals = true, allowSpaces = false, trim = true)
        if (safeDirectoryName.isEmpty()) return

        val path = try { Path.of(projectLocation ?: return) } catch (_: Exception) { return }
        val parent = path.parent ?: return
        val newPath = parent.resolve(safeDirectoryName).toAbsolutePath().toString()

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
        if (projectName.length !in 3..64) {
            return ValidationInfo("Project name must be between 3 and 64 characters.", projectNameField)
        }

        // 2. Addon ID
        val id = addonIdField.text?.trim().orEmpty()
        if (id.isEmpty()) {
            return ValidationInfo("Addon ID cannot be empty.", addonIdField)
        }
        if (id.length !in 3..32 || !id.all { it.isLowerCase() || it.isDigit() || it == '_' }) {
            if (id.length < 3) return ValidationInfo("Addon ID is too short (min 3 characters).", addonIdField)
            if (id.length > 32) return ValidationInfo("Addon ID is too long (max 32 characters).", addonIdField)
            return ValidationInfo("Addon ID must be snake-case (lowercase letters, underscores only).", addonIdField)
        }

        // 3. Blender Version
        val selectedVersion = blenderVersionComboBox.selectedItem as? String
        if (selectedVersion.isNullOrBlank()) {
            return ValidationInfo("Please select a Blender version.", blenderVersionComboBox)
        }

        val minVer = blenderVersionMinField.text?.trim().orEmpty()
        if (minVer.isEmpty()) {
            return ValidationInfo("Minimum Blender version cannot be empty.", blenderVersionMinField)
        }
        val minVerParts = minVer.split(".")
        if (minVerParts.size < 3 || minVerParts.take(3).any { p -> p.isEmpty() || !p.all { it.isDigit() } }) {
            return ValidationInfo("Minimum Blender version must be in format x.y.z (e.g., 5.0.0).", blenderVersionMinField)
        }

        val maxVer = blenderVersionMaxField.text?.trim().orEmpty()
        if (maxVer.isNotEmpty()) {
            val maxVerParts = maxVer.split(".")
            if (maxVerParts.size < 3 || maxVerParts.take(3).any { p -> p.isEmpty() || !p.all { it.isDigit() } }) {
                return ValidationInfo("Maximum Blender version must be in format x.y.z (e.g., 5.0.0).", blenderVersionMaxField)
            }
        }

        // 4. Website URL
        val website = addonWebsiteField.text?.trim().orEmpty()
        if (website.isNotEmpty()) {
            if (!website.contains(".") || website.contains(" ") || website.length < 4) {
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
