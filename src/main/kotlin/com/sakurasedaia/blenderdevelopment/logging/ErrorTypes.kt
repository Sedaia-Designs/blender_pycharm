package com.sakurasedaia.blenderdevelopment.logging

enum class ErrorTypes(val message: String) {
    UnknownOS("[BL-001]: User's OS is not a compatible type, Supported Operating Systems: Windows, MacOS, and Linux (Or alternate Linux Kernel Fork)"),
    InvalidDapConfiguration("[BL-002]: InvalidDapConfiguration is not set to debugpy, could not start process"),
    ;
    override fun toString(): String = message
}