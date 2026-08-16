# Blender Development for PyCharm

[![Kotlin](https://img.shields.io/badge/Kotlin-JVM%2021-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-1.0.0--beta.2-orange)](CHANGELOG.md)

Blender Development is a beta PyCharm plugin for creating, configuring, running, debugging, building, and validating Blender
add-ons and extensions. It provides project templates, Blender installation discovery and management, project-scoped launch
settings, version-matched API stubs, and dedicated Blender run/debug configurations.

The plugin currently targets PyCharm 2026.1 or newer and Blender 4.2 or newer.

## Key capabilities

- Create add-on and extension projects through PyCharm's native Python environment workflow.
- Discover existing Blender installations or install and remove host-compatible versions from plugin Settings.
- Manage Blender discovery, compatible versions, installation locations, and system diagnostics from the unified
  **Blender Versions** settings section.
- Configure the active Blender installation, source folder, add-on symlink name, launch arguments, environment variables,
  and debugger preferences from **Project Blender Manager**.
- Install version-matched Blender API stubs during project creation through the selected Python interpreter.
- Run or debug Blender with reload-on-save, script execution, add-on reload, and stop commands.
- Run Blender extension commands with guided completion for built-in commands and options.
- Build or validate an extension using project-relative or absolute source and output paths.

## Current status

The current development version is **1.0.0-beta.3**. The core project, installation-management, and run/debug workflows are
implemented, and development is focused on stabilization and release hardening before 1.0. Recent beta work added dedicated
run configurations for Blender commands and extension build/validation, improved PyCharm 2026.1 compatibility, and made
managed installation state and scanning more reliable.

The beta is not yet production-hardened. Current limitations include:

- Managed Blender installations cannot yet be updated in place.
- Blender API stub integration and Python-version update workflows are not complete.
- Run and Debug integration still requires stabilization.
- Managed Blender archives are not verified against published checksums.
- Live Blender integration and platform-specific archive extraction are not fully covered by automated tests.

See [CHANGELOG.md](CHANGELOG.md) for the complete release history and current beta changes.

## Documentation

The [Blender Development documentation](https://docs.blender-development.sakura-sedaia.tech/) is the canonical source for:

- [Installing the plugin](https://docs.blender-development.sakura-sedaia.tech/getting-started/)
- [Creating a Blender project](https://docs.blender-development.sakura-sedaia.tech/guides/create-project/)
- [Running and debugging in Blender](https://docs.blender-development.sakura-sedaia.tech/guides/run-and-debug/)
- [Configuration](https://docs.blender-development.sakura-sedaia.tech/reference/configuration/)
- [Troubleshooting](https://docs.blender-development.sakura-sedaia.tech/guides/troubleshooting/)
- [Contributing and building from source](https://docs.blender-development.sakura-sedaia.tech/development/contributing/)

Release changes are recorded in the [changelog](CHANGELOG.md). Bugs and feature requests can be submitted through the [GitLab issue tracker](https://gitlab.com/sedaia-designs/blender_pycharm/-/issues).

## Local GitLab CI validation

The release preparation and Marketplace-input checks can run in a Docker container that matches the GitLab Linux/AMD64
environment. Docker Desktop with Docker Compose is required.

```shell
bash scripts/local-gitlab-ci.sh prepare_release
bash scripts/local-gitlab-ci.sh marketplace_check
```

Use `all` to run both checks. The harness never publishes, reads signing credentials, or includes `.env/` in its Docker build
context. Set `LOCAL_CI_PLATFORM` only when intentionally testing a different container architecture.

## License

Blender Development is licensed under the [GNU General Public License v3.0 or later](LICENSE).

The bundled Blender runtime is substantially derived from Jacques Lucke's [Blender Development extension for Visual Studio Code](https://github.com/JacquesLucke/blender_vscode) and adapted for this plugin. See [NOTICE](NOTICE) for attribution and license details.

Blender is a trademark of the Blender Foundation. This project is not affiliated with or endorsed by the Blender Foundation.
