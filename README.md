# Blender Development for PyCharm

[![Kotlin](https://img.shields.io/badge/Kotlin-JVM%2021-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-pre--release-orange)](CHANGELOG.md)

Blender Development is a pre-release PyCharm plugin for creating, configuring, running, and debugging Blender add-ons and
extensions. It provides Blender project templates, installation discovery and management, project-scoped launch settings,
version-matched API stubs, and Blender run/debug integration.

The plugin currently targets PyCharm 2026.1 or newer and Blender 4.2 or newer.

## Key capabilities

- Create add-on and extension projects through PyCharm's native Python environment workflow.
- Discover existing Blender installations or install and remove host-compatible versions from plugin Settings.
- Configure the active Blender installation, source folder, add-on symlink name, launch arguments, environment variables,
  and debugger preferences in **Project Blender Manager**.
- Install version-matched Blender API stubs during project creation.
- Run Blender from PyCharm or attach the debugger for reload-on-save, script execution, add-on reload, and stop commands.

The plugin is undergoing it's final beta stages to finally reach release status, now updated to version 1.0.0-beta.1, the project is in it's final, pre-release stages. Feel free to track what changes have been made in [CHANGELOG.md](/CHANGELOG.md)

## Documentation

The [Blender Development documentation](https://docs.sakura-sedaia.com/blender-development/) is the canonical source for:

- [Installing the plugin](https://docs.sakura-sedaia.com/blender-development/getting-started/)
- [Creating a Blender project](https://docs.sakura-sedaia.com/blender-development/guides/create-project/)
- [Running and debugging in Blender](https://docs.sakura-sedaia.com/blender-development/guides/run-and-debug/)
- [Configuration](https://docs.sakura-sedaia.com/blender-development/reference/configuration/)
- [Troubleshooting](https://docs.sakura-sedaia.com/blender-development/guides/troubleshooting/)
- [Contributing and building from source](https://docs.sakura-sedaia.com/blender-development/development/contributing/)

Release changes are recorded in the [changelog](CHANGELOG.md). Bugs and feature requests can be submitted through the [GitLab issue tracker](https://gitlab.com/sedaia-designs/blender_pycharm/-/issues).

## License

Blender Development is licensed under the [GNU General Public License v3.0 or later](LICENSE).

The bundled Blender runtime is substantially derived from Jacques Lucke's [Blender Development extension for Visual Studio Code](https://github.com/JacquesLucke/blender_vscode) and adapted for this plugin. See [NOTICE](NOTICE) for attribution and license details.

Blender is a trademark of the Blender Foundation. This project is not affiliated with or endorsed by the Blender Foundation.
