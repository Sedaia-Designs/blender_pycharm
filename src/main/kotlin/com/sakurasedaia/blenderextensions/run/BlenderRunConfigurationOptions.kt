package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.configurations.RunConfigurationOptions

/**
 * Stores the persistent settings for a Blender run configuration.
 */
class BlenderRunConfigurationOptions : RunConfigurationOptions() {
    /**
     * The Blender version to use (e.g., "4.1", "5.0") or a path to the Blender executable.
     */
    private val blenderVersionProperty = string("5.0").provideDelegate(this, "blenderVersion")
    var blenderVersion: String?
        get() = blenderVersionProperty.getValue(this)
        set(value) = blenderVersionProperty.setValue(this, value)

    /**
     * Whether to run Blender in sandbox mode (isolated environment).
     */
    private val isSandboxedProperty = property(true).provideDelegate(this, "isSandboxed")
    var isSandboxed: Boolean
        get() = isSandboxedProperty.getValue(this)
        set(value) = isSandboxedProperty.setValue(this, value)

    /**
     * Whether to import the user's existing Blender configuration into the sandbox.
     */
    private val importUserConfigProperty = property(false).provideDelegate(this, "importUserConfig")
    var importUserConfig: Boolean
        get() = importUserConfigProperty.getValue(this)
        set(value) = importUserConfigProperty.setValue(this, value)

    /**
     * The name of the symlink created for the addon inside Blender's scripts directory.
     */
    private val addonSymlinkNameProperty = string("").provideDelegate(this, "addonSymlinkName")
    var addonSymlinkName: String?
        get() = addonSymlinkNameProperty.getValue(this)
        set(value) = addonSymlinkNameProperty.setValue(this, value)

    /**
     * The source directory of the extension being developed.
     */
    private val addonSourceDirectoryProperty = string("").provideDelegate(this, "addonSourceDirectory")
    var addonSourceDirectory: String?
        get() = addonSourceDirectoryProperty.getValue(this)
        set(value) = addonSourceDirectoryProperty.setValue(this, value)

    /**
     * Additional CLI arguments passed to Blender when it starts.
     */
    private val additionalArgumentsProperty = string("").provideDelegate(this, "additionalArguments")
    var additionalArguments: String?
        get() = additionalArgumentsProperty.getValue(this)
        set(value) = additionalArgumentsProperty.setValue(this, value)

    /**
     * A specific Blender command to run (e.g., "extension build").
     */
    private val blenderCommandProperty = string("").provideDelegate(this, "blenderCommand")
    var blenderCommand: String?
        get() = blenderCommandProperty.getValue(this)
        set(value) = blenderCommandProperty.setValue(this, value)
}
