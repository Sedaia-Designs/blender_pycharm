package com.sakurasedaia.blenderextensions.blender.utils

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.sakurasedaia.blenderextensions.blender.model.DownloadProgress
import com.sakurasedaia.blenderextensions.blender.model.ProgressType
import com.sakurasedaia.blenderextensions.blender.services.BlenderDownloader

/**
 * A wrapper for [ProgressIndicator] that also updates [BlenderDownloader]'s progress flow.
 */
class BlenderProgressHandler(
    private val downloader: BlenderDownloader,
    private val version: String,
    private var statusText: String,
    private val type: ProgressType,
    private val delegate: ProgressIndicator
) : ProgressIndicator by delegate {

    init {
        delegate.text = statusText
        update()
    }

    override fun setFraction(fraction: Double) {
        delegate.fraction = fraction
        update()
    }

    override fun setIndeterminate(indeterminate: Boolean) {
        delegate.isIndeterminate = indeterminate
        update()
    }

    override fun setText(text: String?) {
        delegate.text = text
        text?.let { statusText = it }
        update()
    }

    private fun update() {
        val fraction = if (delegate.isIndeterminate) -1.0 else delegate.fraction
        downloader.updateProgress(DownloadProgress(true, fraction, statusText, version, type))
    }
}

fun ProgressIndicator?.toBlenderHandler(
    downloader: BlenderDownloader,
    version: String,
    statusText: String,
    type: ProgressType = ProgressType.DOWNLOAD
): BlenderProgressHandler {
    return BlenderProgressHandler(downloader, version, statusText, type, this ?: EmptyProgressIndicator())
}
