package com.sakurasedaia.blenderdevelopment.model

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
    val statusText: String? = null,
    val version: String? = null,
    val type: ProgressType = ProgressType.NONE
) {
    companion object {
        val None = DownloadProgress()
    }
}
