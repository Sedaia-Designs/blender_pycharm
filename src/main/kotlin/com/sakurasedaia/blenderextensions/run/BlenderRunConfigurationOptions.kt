package com.sakurasedaia.blenderextensions.run

import com.intellij.execution.configurations.RunConfigurationOptions


class BlenderRunConfigurationOptions : RunConfigurationOptions() {
    private val blenderVersionProperty = string("5.0").provideDelegate(this, "blenderVersion")
    var blenderVersion: String?
        get() = blenderVersionProperty.getValue(this)
        set(value) = blenderVersionProperty.setValue(this, value)


    private val isSandboxedProperty = property(true).provideDelegate(this, "isSandboxed")
    var isSandboxed: Boolean
        get() = isSandboxedProperty.getValue(this)
        set(value) = isSandboxedProperty.setValue(this, value)
    
    private val importUserConfigProperty = property(false).provideDelegate(this, "importUserConfig")
    var importUserConfig: Boolean
        get() = importUserConfigProperty.getValue(this)
        set(value) = importUserConfigProperty.setValue(this, value)

    private val addonSymlinkNameProperty = string("").provideDelegate(this, "addonSymlinkName")
    var addonSymlinkName: String?
        get() = addonSymlinkNameProperty.getValue(this)
        set(value) = addonSymlinkNameProperty.setValue(this, value)

    private val addonSourceDirectoryProperty = string("").provideDelegate(this, "addonSourceDirectory")
    var addonSourceDirectory: String?
        get() = addonSourceDirectoryProperty.getValue(this)
        set(value) = addonSourceDirectoryProperty.setValue(this, value)

    private val additionalArgumentsProperty = string("").provideDelegate(this, "additionalArguments")
    var additionalArguments: String?
        get() = additionalArgumentsProperty.getValue(this)
        set(value) = additionalArgumentsProperty.setValue(this, value)

    private val blenderCommandProperty = string("").provideDelegate(this, "blenderCommand")
    var blenderCommand: String?
        get() = blenderCommandProperty.getValue(this)
        set(value) = blenderCommandProperty.setValue(this, value)

    private val isDebugModeProperty = property(false).provideDelegate(this, "isDebugMode")
    var isDebugMode: Boolean
        get() = isDebugModeProperty.getValue(this)
        set(value) = isDebugModeProperty.setValue(this, value)
}
