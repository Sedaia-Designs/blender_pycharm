package com.sakurasedaia.blenderextensions.project

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import com.sakurasedaia.blenderextensions.blender.model.BlenderVersions
import com.sakurasedaia.blenderextensions.blender.services.BlenderDownloader
import com.sakurasedaia.blenderextensions.common.utils.BlenderTaskManager
import com.sakurasedaia.blenderextensions.common.utils.LangManager
import com.sakurasedaia.blenderextensions.common.utils.PathUtils
import com.sakurasedaia.blenderextensions.icons.BlenderIcons
import com.sakurasedaia.blenderextensions.run.*
import com.sakurasedaia.blenderextensions.ui.settings.BlenderSettings
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import kotlin.io.path.writeText

/**
 * Project generator that produces the same overall layout as
 * [BlenderAddonProjectGenerator] (LICENSE, README, .gitignore, optional
 * agent guidelines, run configurations) but `src/` only contains a single
 * `__init__.py` rendered from the consolidated addon file template.
 *
 * Replaces the legacy "Blank Addon" and "ExampleCode Addon" file templates
 * with a single project generator and an "Add Example Code" toggle.
 */
class BlenderAddonFileProjectGenerator : DirectoryProjectGenerator<BlenderAddonFileProjectSettings> {
    private var myPeerReference = WeakReference<BlenderAddonFileProjectPeer>(null)

    override fun getName(): String = LangManager.message("project.template.addon.file.name")
    override fun getLogo(): Icon = BlenderIcons.Blender

    override fun createPeer(): ProjectGeneratorPeer<BlenderAddonFileProjectSettings> {
        val peer = BlenderAddonFileProjectPeer()
        myPeerReference = WeakReference(peer)
        return peer
    }

    override fun validate(baseDirPath: String): ValidationResult {
        myPeerReference.get()?.updateLocation(baseDirPath)
        return ValidationResult.OK
    }

    @Suppress("DEPRECATION")
    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: BlenderAddonFileProjectSettings,
        module: Module
    ) {
        val projectPath = Path.of(baseDir.path)
        val projectName = settings.projectName?.takeIf { it.isNotBlank() } ?: project.name
        val authorName = settings.author ?: System.getProperty("user.name") ?: "Author"
        val selectedVersion = settings.blenderVersion ?: "5.0"

        // No `src/` directory for the single-file generator: the addon file lives at
        // the project root and the project root itself is registered as the source folder.
        BlenderSettings.getInstance(project).addSourceFolder(
            projectPath.toAbsolutePath().toString().replace("\\", "/")
        )

        val rawFilename = settings.filename?.takeIf { it.isNotBlank() }
            ?: "${formatToId(projectName)}.py"
        val addonFilename = if (rawFilename.endsWith(".py")) rawFilename else "$rawFilename.py"

        // Render the single consolidated addon Python file from the template.
        projectPath.resolve(addonFilename).writeText(
            BlenderProjectTemplateGenerator.generateAddonInit(
                name = projectName,
                author = authorName,
                version = settings.version ?: "(0, 0, 1)",
                blender = settings.blender ?: "(4, 2, 0)",
                location = settings.location ?: "",
                description = settings.description ?: "",
                warning = settings.warning ?: "",
                docUrl = settings.docUrl ?: "",
                category = settings.category ?: "Generic",
                addExampleCode = settings.addExampleCode
            )
        )

        // Project-level scaffolding (mirrors BlenderAddonProjectGenerator).
        projectPath.resolve(PathUtils.LICENSE_NAME).writeText(BlenderProjectTemplateGenerator.generateLicense())
        projectPath.resolve(PathUtils.README_NAME).writeText(
            BlenderProjectTemplateGenerator.generateReadme(
                name = projectName,
                author = authorName,
                tagline = LangManager.message("project.template.default.tagline")
            )
        )
        projectPath.resolve(PathUtils.GITIGNORE_NAME).writeText(BlenderProjectTemplateGenerator.generateGitignore())

        if (settings.agentGuidelines) {
            val agentDir = PathUtils.getAgentDir(project)
            val skillsDir = PathUtils.getSkillsDir(project)
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

        // Create the same set of run configurations as the full extension generator.
        val runManager = RunManager.getInstance(project)
        val configType = ConfigurationTypeUtil.findConfigurationType(BlenderRunConfigurationType::class.java)

        val startBlenderFactory = configType.configurationFactories.find { it is BlenderStartBlenderConfigurationFactory }
        if (startBlenderFactory != null) {
            val runSettings = runManager.createConfiguration("Start Blender", startBlenderFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            val options = runConfig.options
            options.blenderVersion = selectedVersion
            options.isSandboxed = settings.sandbox
            options.addonSourceDirectory = projectPath.toAbsolutePath().toString()
            options.addonSymlinkName = formatToId(projectName)
            runManager.addConfiguration(runSettings)
            runManager.selectedConfiguration = runSettings
        }

        val buildFactory = configType.configurationFactories.find { it is BlenderBuildConfigurationFactory }
        if (buildFactory != null) {
            val runSettings = runManager.createConfiguration("Build", buildFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            runConfig.options.blenderVersion = selectedVersion
            runManager.addConfiguration(runSettings)
        }

        val validateFactory = configType.configurationFactories.find { it is BlenderValidateConfigurationFactory }
        if (validateFactory != null) {
            val runSettings = runManager.createConfiguration("Validate", validateFactory)
            val runConfig = runSettings.configuration as BlenderRunConfiguration
            runConfig.options.blenderVersion = selectedVersion
            runManager.addConfiguration(runSettings)
        }

        if (settings.createGitRepo) {
            try {
                com.intellij.execution.configurations.GeneralCommandLine("git", "init")
                    .withWorkDirectory(projectPath.toFile())
                    .createProcess()
                    .waitFor()
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Settings backing [BlenderAddonFileProjectGenerator]. The fields directly
 * reflect the variables exposed by the original `Blank Addon.py.ft` and
 * `ExampleCode Addon.py.ft` file templates: NAME, USER, VERSION, BLENDER,
 * LOCATION, DESCRIPTION, WARNING, DOC URL and category.
 */
data class BlenderAddonFileProjectSettings(
    var projectName: String? = null,
    var filename: String? = null,
    var author: String? = System.getProperty("user.name") ?: "Author",
    var version: String? = "(0, 0, 1)",
    var blender: String? = "(4, 2, 0)",
    var location: String? = "",
    var description: String? = "",
    var warning: String? = "",
    var docUrl: String? = "",
    var category: String? = "Generic",
    var addExampleCode: Boolean = false,
    var blenderVersion: String? = "5.0",
    var agentGuidelines: Boolean = true,
    var createGitRepo: Boolean = false,
    var sandbox: Boolean = true
)

internal class BlenderAddonFileProjectPeer : ProjectGeneratorPeer<BlenderAddonFileProjectSettings> {
    private var projectLocation: String? = null
    private var isUpdating = false

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
            val nameFromPath = try {
                Path.of(path).fileName?.toString()
            } catch (_: Exception) {
                null
            }
            if (!nameFromPath.isNullOrEmpty() && projectNameField.text != nameFromPath) {
                isUpdating = true
                try {
                    projectNameField.text = nameFromPath
                } finally {
                    isUpdating = false
                }
                fireStateChanged()
            }
        }
    }

    private val addExampleCodeCheckbox = JBCheckBox(LangManager.message("project.template.addon.file.add.example"), false)
    private val includeAgentGuidelines = JBCheckBox(LangManager.message("project.generator.checkbox.agent.guidelines"), true)
    private val createGitRepoCheckbox = JBCheckBox(LangManager.message("project.generator.checkbox.git.repo"), false)
    private val sandboxEnvironment = JBCheckBox(LangManager.message("project.generator.checkbox.sandbox"), true)

    internal val projectNameField = JBTextField()
    internal val filenameField = JBTextField()
    private var filenameEditedByUser = false
    internal val authorField = JBTextField(System.getProperty("user.name") ?: "Author")
    internal val versionField = JBTextField("(0, 0, 1)")
    internal val blenderField = JBTextField("(4, 2, 0)")
    internal val locationField = JBTextField()
    internal val descriptionField = JBTextField()
    internal val warningField = JBTextField()
    internal val docUrlField = JBTextField()
    internal val categoryField = JBTextField("Generic")

    internal val blenderVersionComboBox = ComboBox<String>()
    internal val blenderDownloadButton = javax.swing.JButton(
        LangManager.message("run.configuration.button.download"),
        BlenderIcons.Install
    )

    private val panel: JPanel

    init {
        // Wire change listeners on all text fields.
        val allFields = listOf(
            projectNameField, filenameField, authorField, versionField, blenderField,
            locationField, descriptionField, warningField, docUrlField, categoryField
        )
        allFields.forEach { field ->
            field.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = fireStateChanged()
            })
            field.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent?) {
                    fireStateChanged()
                }
            })
        }

        projectNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (isUpdating) return
                isUpdating = true
                try {
                    SwingUtilities.invokeLater {
                        try {
                            updateLocationFromProjectName()
                            updateFilenameFromProjectName()
                        } finally {
                            isUpdating = false
                        }
                        fireStateChanged()
                    }
                } catch (_: Exception) {
                    isUpdating = false
                }
            }
        })

        filenameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!isUpdating) filenameEditedByUser = true
            }
        })

        addExampleCodeCheckbox.addActionListener { fireStateChanged() }
        includeAgentGuidelines.addActionListener { fireStateChanged() }
        createGitRepoCheckbox.addActionListener { fireStateChanged() }
        sandboxEnvironment.addActionListener { fireStateChanged() }

        // Blender version combo box.
        val downloader = BlenderDownloader.getInstance(
            com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
        )
        val versions = BlenderVersions.getAllSelectableVersions()
        blenderVersionComboBox.model = javax.swing.DefaultComboBoxModel(versions.toTypedArray())
        blenderVersionComboBox.selectedItem = "5.0"
        blenderVersionComboBox.addActionListener {
            fireStateChanged()
            updateDownloadButtonVisibility()
        }
        blenderDownloadButton.addActionListener {
            val selected = blenderVersionComboBox.selectedItem as? String ?: return@addActionListener
            if (!downloader.isDownloaded(selected)) {
                BlenderTaskManager.getInstance().run(
                    null,
                    LangManager.message("action.download.blender.task", selected)
                ) {
                    downloader.getOrDownloadBlenderPath(selected)
                    SwingUtilities.invokeLater { updateDownloadButtonVisibility() }
                }
            }
        }
        updateDownloadButtonVisibility()

        panel = panel {
            row { cell(addExampleCodeCheckbox) }
            row { cell(includeAgentGuidelines) }
            row { cell(createGitRepoCheckbox) }
            row { cell(sandboxEnvironment) }
            separator()
            row(LangManager.message("project.generator.addon.file.row.project.name")) { cell(projectNameField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.filename")) { cell(filenameField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.author")) { cell(authorField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.version")) { cell(versionField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.blender")) { cell(blenderField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.location")) { cell(locationField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.description")) { cell(descriptionField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.warning")) { cell(warningField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.docurl")) { cell(docUrlField).align(AlignX.FILL) }
            row(LangManager.message("project.generator.addon.file.row.category")) { cell(categoryField).align(AlignX.FILL) }
            separator()
            row(LangManager.message("project.generator.row.blender.version")) {
                cell(blenderVersionComboBox).align(AlignX.FILL).resizableColumn()
                cell(blenderDownloadButton)
            }
        }

        projectNameField.toolTipText = LangManager.message("project.generator.addon.file.tooltip.project.name")
        versionField.toolTipText = LangManager.message("project.generator.addon.file.tooltip.version")
        blenderField.toolTipText = LangManager.message("project.generator.addon.file.tooltip.blender")
        docUrlField.toolTipText = LangManager.message("project.generator.addon.file.tooltip.docurl")
    }

    private fun updateDownloadButtonVisibility() {
        val selected = blenderVersionComboBox.selectedItem as? String
        val downloader = BlenderDownloader.getInstance(
            com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
        )
        blenderDownloadButton.isVisible = selected != null &&
                BlenderVersions.SUPPORTED_VERSIONS.any { it.majorMinor == selected } &&
                !downloader.isDownloaded(selected)
    }

    private fun updateFilenameFromProjectName() {
        if (filenameEditedByUser) return
        val name = projectNameField.text.trim()
        if (name.isEmpty()) return
        val derived = formatToId(name)
        if (derived.isEmpty()) return
        val newName = "$derived.py"
        if (filenameField.text != newName) {
            filenameField.text = newName
            filenameEditedByUser = false
        }
    }

    private fun updateLocationFromProjectName() {
        val name = projectNameField.text.trim()
        if (name.isEmpty() || projectLocation == null) return

        val safeDirectoryName = formatToId(name, allowCapitals = true, allowSpaces = false, trim = true)
        if (safeDirectoryName.isEmpty()) return

        val path = try {
            Path.of(projectLocation ?: return)
        } catch (_: Exception) {
            return
        }
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

    override fun getSettings(): BlenderAddonFileProjectSettings = BlenderAddonFileProjectSettings(
        projectName = projectNameField.text?.trim(),
        filename = filenameField.text?.trim(),
        author = authorField.text?.trim(),
        version = versionField.text?.trim(),
        blender = blenderField.text?.trim(),
        location = locationField.text?.trim(),
        description = descriptionField.text?.trim(),
        warning = warningField.text?.trim(),
        docUrl = docUrlField.text?.trim(),
        category = categoryField.text?.trim(),
        addExampleCode = addExampleCodeCheckbox.isSelected,
        blenderVersion = blenderVersionComboBox.selectedItem as? String,
        agentGuidelines = includeAgentGuidelines.isSelected,
        createGitRepo = createGitRepoCheckbox.isSelected,
        sandbox = sandboxEnvironment.isSelected
    )

    override fun validate(): ValidationInfo? {
        val projectName = projectNameField.text?.trim().orEmpty()
        if (projectName.isEmpty()) {
            return ValidationInfo(LangManager.message("project.generator.error.project.name.empty"), projectNameField)
        }

        val filename = filenameField.text?.trim().orEmpty()
        if (filename.isEmpty()) {
            return ValidationInfo(LangManager.message("project.generator.addon.file.error.filename.empty"), filenameField)
        }
        val filenameNoExt = if (filename.endsWith(".py")) filename.removeSuffix(".py") else filename
        if (filenameNoExt.isEmpty() || filenameNoExt.any { !(it.isLetterOrDigit() || it == '_') } || filenameNoExt[0].isDigit()) {
            return ValidationInfo(LangManager.message("project.generator.addon.file.error.filename.invalid"), filenameField)
        }

        val version = versionField.text?.trim().orEmpty()
        if (version.isEmpty() || !version.startsWith("(") || !version.endsWith(")")) {
            return ValidationInfo(LangManager.message("project.generator.addon.file.error.version.format"), versionField)
        }

        val blender = blenderField.text?.trim().orEmpty()
        if (blender.isEmpty() || !blender.startsWith("(") || !blender.endsWith(")")) {
            return ValidationInfo(LangManager.message("project.generator.addon.file.error.blender.format"), blenderField)
        }

        val selected = blenderVersionComboBox.selectedItem as? String
        if (selected.isNullOrBlank()) {
            return ValidationInfo(LangManager.message("project.generator.error.blender.version.select"), blenderVersionComboBox)
        }
        return null
    }

    override fun isBackgroundJobRunning(): Boolean = false

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun addSettingsStateListener(listener: com.intellij.platform.WebProjectGenerator.SettingsStateListener) {
        stateListeners.add(listener)
    }
}
