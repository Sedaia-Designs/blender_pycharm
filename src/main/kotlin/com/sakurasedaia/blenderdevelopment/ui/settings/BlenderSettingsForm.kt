package com.sakurasedaia.blenderdevelopment.ui.settings

internal data class BlenderSettingsForm(
  val blenderInstallPath: String,
  val codeCompletionPath: String,
  val logPath: String,
  val downloadPath: String,
  val clearDownloadsAfterInstall: Boolean,
  val minimumBlenderVersion: String,
  val globalEnvironmentVariables: Map<String, String>,
)
