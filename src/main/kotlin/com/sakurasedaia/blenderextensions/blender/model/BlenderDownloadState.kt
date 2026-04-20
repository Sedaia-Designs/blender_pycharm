package com.sakurasedaia.blenderextensions.blender.model

enum class ProgressType {
    NONE,
    DOWNLOAD,
    EXTRACT,
    LINTER,
    SANDBOX
}

data class DownloadProgress(
    val isDownloading: Boolean = false,
    val progress: Double = 0.0,
    val statusText: String = "",
    val version: String = "",
    val type: ProgressType = ProgressType.NONE
) {
    companion object {
        val None = DownloadProgress()
    }
}
