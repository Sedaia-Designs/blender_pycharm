package com.sakurasedaia.blenderextensions.python

object PythonVersions {

    private val BLENDER_PYTHON_ASSOCIATION = mapOf(
        "4.2" to "3.11.7",
        "4.3" to "3.11.9",
        "4.4" to "3.11.11",
        "4.5" to "3.11.11",
        "5.0" to "3.11.13",
        "5.1" to "3.13.9"
    )

    fun getPythonVersionForBlender(blenderVersion: String): String? {
        return BLENDER_PYTHON_ASSOCIATION[blenderVersion]
    }
}

