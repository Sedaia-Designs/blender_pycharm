package com.sakurasedaia.blenderextensions.project

import com.sakurasedaia.blenderextensions.common.utils.PathUtils

data class BlenderManifestSettings(
    val id: String,
    val name: String,
    val tagline: String,
    val maintainer: String,
    val website: String? = null,
    val tags: List<String>? = null,
    val blenderVersionMin: String = "4.2.0",
    val blenderVersionMax: String? = null,
    val platforms: List<String>? = null,
    val permissions: Map<String, String>? = null,
    val buildPathsExcludePattern: List<String>? = null
)

class BlenderProjectTemplateGenerator {
    companion object {
        fun generateManifest(settings: BlenderManifestSettings): String {
            val sb = StringBuilder()
            sb.append("schema_version = \"1.0.0\"\n\n")
            sb.append("# Example of manifest file for a Blender extension\n")
            sb.append("# Change the values according to your extension\n")
            sb.append("id = \"${settings.id}\"\n")
            sb.append("version = \"1.0.0\"\n")
            sb.append("name = \"${settings.name}\"\n")
            sb.append("tagline = \"${settings.tagline}\"\n")
            sb.append("maintainer = \"${settings.maintainer}\"\n")
            sb.append("# Supported types: \"add-on\", \"theme\"\n")
            sb.append("type = \"add-on\"\n\n")

            if (!settings.website.isNullOrBlank()) {
                sb.append("# Optional: link to documentation, support, source files, etc\n")
                sb.append("website = \"${settings.website}\"\n\n")
            } else {
                sb.append("# # Optional: link to documentation, support, source files, etc\n")
                sb.append("# website = \"https://extensions.blender.org/add-ons/my-example-package/\"\n\n")
            }

            if (!settings.tags.isNullOrEmpty()) {
                sb.append("# Optional: tag list defined by Blender and server, see:\n")
                sb.append("# https://docs.blender.org/manual/en/dev/advanced/extensions/tags.html\n")
                sb.append("tags = [${settings.tags.joinToString(", ") { "\"$it\"" }}]\n\n")
            } else {
                sb.append("# # Optional: tag list defined by Blender and server, see:\n")
                sb.append("# # https://docs.blender.org/manual/en/dev/advanced/extensions/tags.html\n")
                sb.append("# tags = [\"Animation\", \"Sequencer\"]\n\n")
            }

            sb.append("blender_version_min = \"${settings.blenderVersionMin}\"\n")
            if (!settings.blenderVersionMax.isNullOrBlank()) {
                sb.append("# Optional: Blender version that the extension does not support, earlier versions are supported.\n")
                sb.append("# This can be omitted and defined later on the extensions platform if an issue is found.\n")
                sb.append("blender_version_max = \"${settings.blenderVersionMax}\"\n\n")
            } else {
                sb.append("# # Optional: Blender version that the extension does not support, earlier versions are supported.\n")
                sb.append("# # This can be omitted and defined later on the extensions platform if an issue is found.\n")
                sb.append("# blender_version_max = \"5.1.0\"\n\n")
            }

            sb.append("# License conforming to https://spdx.org/licenses/ (use \"SPDX: prefix)\n")
            sb.append("# https://docs.blender.org/manual/en/dev/advanced/extensions/licenses.html\n")
            sb.append("license = [\n")
            sb.append("  \"SPDX:GPL-3.0-or-later\",\n")
            sb.append("]\n")
            sb.append("# # Optional: required by some licenses.\n")
            sb.append("# copyright = [\n")
            sb.append("#   \"2002-2024 Developer Name\",\n")
            sb.append("#   \"1998 Company Name\",\n")
            sb.append("# ]\n\n")

            if (!settings.platforms.isNullOrEmpty()) {
                sb.append("# Optional: list of supported platforms. If omitted, the extension will be available in all operating systems.\n")
                sb.append("platforms = [${settings.platforms.joinToString(", ") { "\"$it\"" }}]\n\n")
            } else {
                sb.append("# # Optional: list of supported platforms. If omitted, the extension will be available in all operating systems.\n")
                sb.append("# platforms = [\"windows-x64\", \"macos-arm64\", \"linux-x64\"]\n")
                sb.append("# # Other supported platforms: \"windows-arm64\", \"macos-x64\"\n\n")
            }

            if (!settings.permissions.isNullOrEmpty()) {
                sb.append("# Optional: add-ons can list which resources they will require:\n")
                sb.append("# * files (for access of any filesystem operations)\n")
                sb.append("# * network (for internet access)\n")
                sb.append("# * clipboard (to read and/or write the system clipboard)\n")
                sb.append("# * camera (to capture photos and videos)\n")
                sb.append("# * microphone (to capture audio)\n")
                sb.append("#\n")
                sb.append("# If using network, remember to also check `bpy.app.online_access`\n")
                sb.append("# https://docs.blender.org/manual/en/dev/advanced/extensions/addons.html#internet-access\n")
                sb.append("#\n")
                sb.append("# For each permission it is important to also specify the reason why it is required.\n")
                sb.append("# Keep this a single short sentence without a period (.) at the end.\n")
                sb.append("# For longer explanations use the documentation or detail page.\n\n")
                sb.append("[permissions]\n")
                settings.permissions.forEach { (key, reason) ->
                    sb.append("$key = \"$reason\"\n")
                }
                sb.append("\n")
            } else {
                sb.append("# # Optional: add-ons can list which resources they will require:\n")
                sb.append("# # * files (for access of any filesystem operations)\n")
                sb.append("# # * network (for internet access)\n")
                sb.append("# # * clipboard (to read and/or write the system clipboard)\n")
                sb.append("# # * camera (to capture photos and videos)\n")
                sb.append("# # * microphone (to capture audio)\n")
                sb.append("# #\n")
                sb.append("# # If using network, remember to also check `bpy.app.online_access`\n")
                sb.append("# # https://docs.blender.org/manual/en/dev/advanced/extensions/addons.html#internet-access\n")
                sb.append("# #\n")
                sb.append("# # For each permission it is important to also specify the reason why it is required.\n")
                sb.append("# # Keep this a single short sentence without a period (.) at the end.\n")
                sb.append("# # For longer explanations use the documentation or detail page.\n")
                sb.append("#\n")
                sb.append("# [permissions]\n")
                sb.append("# network = \"Need to sync motion-capture data to server\"\n")
                sb.append("# files = \"Import/export FBX from/to disk\"\n")
                sb.append("# clipboard = \"Copy and paste bone transforms\"\n\n")
            }

            if (!settings.buildPathsExcludePattern.isNullOrEmpty()) {
                sb.append("# Optional: advanced build settings.\n")
                sb.append("# https://docs.blender.org/manual/en/dev/advanced/extensions/command_line_arguments.html#command-line-args-extension-build\n")
                sb.append("[build]\n")
                sb.append("# These are the default build excluded patterns.\n")
                sb.append("# You only need to edit them if you want different options.\n")
                sb.append("paths_exclude_pattern = [${settings.buildPathsExcludePattern.joinToString(", ") { "\"$it\"" }}]\n")
            } else {
                sb.append("# # Optional: advanced build settings.\n")
                sb.append("# # https://docs.blender.org/manual/en/dev/advanced/extensions/command_line_arguments.html#command-line-args-extension-build\n")
                sb.append("# [build]\n")
                sb.append("# # These are the default build excluded patterns.\n")
                sb.append("# # You only need to edit them if you want different options.\n")
                sb.append("# paths_exclude_pattern = [\n")
                sb.append("#   \"__pycache__/\",\n")
                sb.append("#   \"/.git/\",\n")
                sb.append("#   \"/*.zip\",\n")
                sb.append("# ]\n")
            }

            return sb.toString()
        }

        /**
         * Generates an addon `__init__.py` modeled after the legacy
         * "Blank Addon" / "ExampleCode Addon" file templates. A single
         * "Add Example Code" toggle (`addExampleCode`) switches between
         * the empty-body variant and the example-code variant.
         */
        fun generateAddonInit(
            name: String,
            author: String,
            version: String,
            blender: String,
            location: String,
            description: String,
            warning: String,
            docUrl: String,
            category: String,
            addExampleCode: Boolean
        ): String {
            val header = """
                bl_info = {
                    "name": "$name",
                    "author": "$author",
                    "version": $version,
                    "blender": $blender,
                    "location": "$location",
                    "description": "$description",
                    "warning": "$warning",
                    "doc_url": "$docUrl",
                    "category": "$category",
                }

            """.trimIndent()

            val body = if (addExampleCode) {
                """

                import bpy
                import bpy.types as T
                import bpy.props as P
                import bpy.ops as O
                import bpy.utils as U

                class SIMPLE_OT_operator(T.Operator):
                    ""${'"'}Simple Operator Tooltip""${'"'}
                    bl_idname = "object.simple_operator"
                    bl_label = "Simple Operator"
                    bl_options = {'REGISTER', 'UNDO'}

                    def execute(self, context):
                        self.report({'INFO'}, "Hello $author!")
                        return {'FINISHED'}

                def menu_func(self, context):
                    self.layout.operator(SIMPLE_OT_operator.bl_idname, text=SIMPLE_OT_operator.bl_label)

                # Register and unregister classes

                classes = (
                    SIMPLE_OT_operator,
                )

                def register():
                    for cls in classes:
                        U.register_class(cls)

                    bpy.types.VIEW3D_MT_object.append(menu_func)

                def unregister():
                    for cls in reversed(classes):
                        U.unregister_class(cls)

                    bpy.types.VIEW3D_MT_object.remove(menu_func)

                if __name__ == "__main__":
                    register()
                """.trimIndent()
            } else {
                """

                import bpy

                def register():
                    pass

                def unregister():
                    pass

                if __name__ == "__main__":
                    register()
                """.trimIndent()
            }

            return header + "\n" + body + "\n"
        }

        fun generateSimpleInit(name: String, author: String): String {
            return """
                # This program is free software; you can redistribute it and/or modify
                # it under the terms of the GNU General Public License as published by
                # the Free Software Foundation; either version 3 of the License, or
                # (at your option) any later version.
                
                bl_info = {
                    "name": "$name",
                    "author": "$author",
                    "description": "",
                    "blender": (4, 2, 0),
                    "version": (0, 0, 1),
                    "location": "",
                    "warning": "",
                    "category": "Generic",
                }

                def register():
                    pass

                def unregister():
                    pass
            """.trimIndent()
        }

        fun generateAutoLoadInit(name: String, author: String): String {
            return """
                # This program is free software; you can redistribute it and/or modify
                # it under the terms of the GNU General Public License as published by
                # the Free Software Foundation; either version 3 of the License, or
                # (at your option) any later version.
                
                bl_info = {
                    "name": "$name",
                    "author": "$author",
                    "description": "",
                    "blender": (4, 2, 0),
                    "version": (0, 0, 1),
                    "location": "",
                    "warning": "",
                    "category": "Generic",
                }

                from . import auto_load

                auto_load.init()

                def register():
                    auto_load.register()

                def unregister():
                    auto_load.unregister()
            """.trimIndent()
        }

        fun generateReadme(name: String, @Suppress("UNUSED_PARAMETER") author: String, tagline: String): String {
            return """
                # $name

                $tagline

                ## Setup Instructions

                To get the best development experience, follow these steps to configure your Python environment using PyCharm's built-in tools:

                1.  **Configure Python Interpreter**:
                    - Go to **File > Settings > Project** (or **PyCharm > Settings** on macOS) and select **Python Interpreter**.
                    - Click **Add Interpreter** and select **Add Local Interpreter...**.
                    - Choose **Virtualenv Environment**.
                    - Ensure **New environment** is selected. The **Location** should default to a `${PathUtils.VENV_NAME}` folder in your project root.
                    - Select a **Base interpreter** (Python 3.11 is recommended for Blender 4.2+ and 5.0).
                    - Click **OK**. PyCharm will create the virtual environment and configure it for your project.

                2.  **Install Linting Stubs**:
                    - In the same **Python Interpreter** settings page, click the **+** icon (Install) to open the Available Packages dialog.
                    - Search for `fake-bpy-module` and click **Install Package**.
                    - Once installed, you will have full code completion and type hinting for the `bpy` module.

                ## Development
                
                The Template comes pre-configured with three Run Configurations:
                - **Validate Extension**: Checks your extension for errors and compliance with Blender's requirements.
                - **Build Extension**: Packages your extension into a `.zip` file ready for distribution.
                - **Run Extension**: Runs your extension in Blender (Sandboxed by default), and auto-reloads the extension when a file is saved

                1.  Open **Run > Edit Configurations...**.
                2.  Select the **Start Blender** configuration.
                3.  Click **Run** to download and start Blender with your extension automatically linked and enabled.

                ## Building and Validating

                You can use the provided Run Configurations to validate and build your extension:
                - **Validate Extension**: Checks your extension for errors and compliance with Blender's requirements.
                - **Build Extension**: Packages your extension into a `.zip` file ready for distribution.
                - **Run Extension**: Runs your extension in Blender (Sandboxed by default), and auto-reloads the extension when a file is saved

                ## Useful Resources

                - [Blender Python API Documentation](https://docs.blender.org/api/current/)
                - [Blender User Manual](https://docs.blender.org/manual/en/latest/)
                - [Blender Extension Platform](https://extensions.blender.org/)
                
                Extension Template Generated using [Blender Development Tools by Sakura Sedaia](https://codeberg.org/SakuraSedaia/blender_pycharm)
            """.trimIndent()
        }

        fun generateLicense(): String {
            val resource = BlenderProjectTemplateGenerator::class.java.getResourceAsStream("/templates/LICENSE")
            return resource?.bufferedReader()?.use { it.readText() } ?: ""
        }

        fun generateGitignore(): String {
            val resource = BlenderProjectTemplateGenerator::class.java.getResourceAsStream("/templates/gitignore")
            return resource?.bufferedReader()?.use { it.readText() } ?: ""
        }
        
        fun getAutoLoadContent(): String {
            val resource = BlenderProjectTemplateGenerator::class.java.getResourceAsStream("/templates/auto_load.py")
            return resource?.bufferedReader()?.use { it.readText() } ?: ""
        }

        fun generateAgentGuidelines(): String {
            val resource = BlenderProjectTemplateGenerator::class.java.getResourceAsStream("/templates/agent-guidelines.md")
            return resource?.bufferedReader()?.use { it.readText() } ?: ""
        }

        fun generateAgentSkill(skillName: String): String {
            val resource = BlenderProjectTemplateGenerator::class.java.getResourceAsStream("/templates/skills/$skillName.md")
            return resource?.bufferedReader()?.use { it.readText() } ?: ""
        }

        fun generateAgentProject(name: String): String {
            return """
                # Project Map: $name

                ## Goals
                - Develop a high-quality Blender Extension.
                - Maintain compliance with Blender's extension guidelines.

                ## Architecture
                - **${PathUtils.SRC_NAME}/**: Extension source code.
                - **${PathUtils.MANIFEST_NAME}**: Extension metadata and permissions.

                ## Task Status
                - [x] Project Initialization
                - [ ] Implement Core Functionality
                - [ ] Validate Extension
                - [ ] Build & Package
            """.trimIndent()
        }

        fun generateAgentContext(): String {
            return """
                # Project Context

                ## Language Styles
                ### Python
                - PEP 8 compliance.
                - Standard Blender API (bpy) usage.
                - Use `self.report()` for UI feedback in operators.

                ## Tooling
                - Blender Extension CLI for validation and building.
            """.trimIndent()
        }
    }
}
