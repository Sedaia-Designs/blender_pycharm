package com.sakurasedaia.blenderdevelopment.logging

enum class ErrorTypes(val message: String) {
    UNSUPPORTED_OS("[BL-001]: User's OS is not a compatible type, Supported Operating Systems: Windows, MacOS, and Linux (Or alternate Linux Kernel Fork)"),
    INVALID_DAP_CONFIG("[BL-002]: INVALID_DAP_CONFIG is not set to debugpy, could not start process"),
    ARCHIVE_FORMAT_UNSUPPORTED("[BL-003]: Archive Format not supported"),
    NOT_BLENDER_BUNDLE("[BL-004]: Bundle provided is not a Portable Blender bundle"),
    UNSUPPORTED_ARCH("[BL-005]: Unsupported CPU Architecture"),
    ;
    override fun toString(): String = message
}