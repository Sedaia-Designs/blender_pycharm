# Blender Python Development for PyCharm

[![Kotlin](https://img.shields.io/badge/Kotlin-JVM%2021-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-pre--release-orange)](CHANGELOG.md)

Blender Python Development is a pre-release PyCharm plugin for creating, configuring, running, and debugging Blender add-ons and extensions. It combines PyCharm's native Python environment workflow with Blender-specific project templates, project settings, installation discovery, version-matched API stubs, and a bundled Blender runtime.

The current plugin targets PyCharm rather than the wider IntelliJ Platform product family. Support for other JetBrains IDEs is not implemented.

## Current capabilities

### Project creation

- Adds a **Blender Project** type to PyCharm's New Project wizard.
- Uses PyCharm's native environment selector for project virtual environments, optional uv environments, and existing interpreters.
- Creates a `src` source root, `pyproject.toml`, GPL license, add-on entry point, and—when creating an extension—`blender_manifest.toml`.
- Supports extension metadata, compatibility ranges, tags, permission reasons, documentation URLs, and optional example code.
- Can install the configured version-specific `fake-bpy-module` package into the selected project interpreter and record it in the `dev` dependency group.

### Project configuration

The **Project Blender Manager** tool window provides project-scoped controls for:

- Blender executable or application bundle
- Target Blender version
- Add-on module/symlink name and source folder
- Blender command-line arguments and log level
- Reload-on-save and just-my-code preferences
- Extension repository name
- Additional script directories
- Project environment variables
- Version-matched Blender API stub replacement

Application settings provide global environment variables, a custom installation scan root, and plugin log location. Some persisted download and code-completion path settings are placeholders for workflows that are not implemented yet.

### Blender discovery

The plugin can scan common installation locations on Windows, macOS, and Linux, probe detected binaries with `--version`, and cache the results for project selection. It discovers existing installations only; it does not currently download, install, update, or sandbox Blender builds.

### Run and debug

The plugin registers a **Blender** run configuration:

- **Run** starts Blender with the configured arguments and a lightweight script that refreshes and attempts to enable the configured add-on or extension.
- **Debug** extracts the bundled runtime, links and loads configured add-on roots, starts a local runtime command server and `debugpy`, then asks PyCharm to attach the Python debugger.
- Debug sessions can reload the configured add-on on file save.
- Active runtime sessions expose **Run Script**, **Reload Add-on**, and **Stop Blender** actions through the Tools menu; Run Script is also available from the Python editor context menu.
- Generated bootstrap scripts and runtime session state are cleaned up when the Blender process terminates, with stale-script cleanup on later IDE startup.

## Compatibility

| Component | Current target |
| --- | --- |
| IDE | PyCharm 2026.1 or newer (`sinceBuild = 261`) |
| Plugin JVM | Java 21 |
| Kotlin target | JVM 21 |
| Blender workflow | Blender 4.2+ add-ons and extensions |
| Selectable Blender presets | 4.2, 4.5, and 5.2 |
| Host discovery | Windows, macOS, and Linux |

The selectable version registry recommends the Python version bundled with each Blender line and resolves the corresponding linting-stub package. Blender 5.2 currently uses the explicit `fake-bpy-module-latest` mapping.

uv is optional. When uv support is available in PyCharm, it can be selected through PyCharm's native project environment controls. This plugin does not require uv and does not install uv automatically.

## Trying the plugin from source

Requirements:

- JDK 21
- Network access for the first Gradle/IntelliJ Platform dependency resolution
- PyCharm 2026.1-compatible development environment

Clone the repository, then run:

```bash
./gradlew compileKotlin --no-daemon
./gradlew runIde
```

`runIde` opens a sandbox PyCharm instance with the plugin installed. Inside the sandbox:

1. Create a Blender project or open an existing one.
2. Open **Project Blender Manager** and select or enter a Blender launch target.
3. Confirm the source folder and add-on/extension module name.
4. Add a **Blender** run configuration through **Run > Edit Configurations**.
5. Use Run for lightweight launch/refresh behavior or Debug for debugger attachment and runtime commands.

On the first Debug launch, Blender may need network access while the bundled runtime installs missing Python dependencies into Blender's user script modules.

## Building and validation

```bash
./gradlew compileKotlin --no-daemon
./gradlew test --no-daemon
./gradlew buildPlugin
```

The build packages `src/main/python` as the `include/blender_pycharm` package in `blender-runtime.zip` and embeds it in the plugin resources. The runtime is extracted into the IDE settings area and refreshed when the plugin version or archive hash changes.

## Current status and limitations

This repository is under active development and is not production-hardened. The current source audit records known correctness, security, and maintenance issues, including project-template defects, installation-discovery edge cases, and unauthenticated localhost runtime command channels. Review the [July 2026 codebase audit](docs/Wiki/internal/codebase-audit-2026-07.html) before relying on the plugin for untrusted or production workflows.

Other current limitations:

- Normal Run does not provide the full runtime command/debug handshake used by Debug.
- Blender installation management is discovery-only.
- A graphical `blender_manifest.toml` editor and custom synchronized project model are not implemented.
- User-visible text is primarily English; a full translation set is not available.
- Live Blender integration is not covered by the JVM test suite.

Issues and feature requests can be reported in the [Codeberg issue tracker](https://codeberg.org/SakuraSedaia/blender_pycharm/issues).

## Documentation

- [Project documentation](docs/Wiki/project/index.html)
- [Contributor guide](docs/CONTRIBUTING.md)
- [Internal technical documentation](docs/Wiki/internal/index.html)
- [Changelog](CHANGELOG.md)

## Roadmap

Near-term work is focused on:

- Resolving the high-priority findings in the current codebase audit
- Hardening and testing Run/Debug lifecycle behavior
- Improving installation discovery and adding managed Blender installations
- Adding a Blender manifest editor and synchronized project configuration
- Expanding localization coverage

Roadmap items are directional and do not represent release commitments.

## Credits and licensing

This project is licensed under the [GNU General Public License v3.0 or later](LICENSE).

The Blender runtime under `src/main/python` is substantially derived from Jacques Lucke's [Blender Development extension for Visual Studio Code](https://github.com/JacquesLucke/blender_vscode) and adapted for this plugin's PyCharm run/debug lifecycle, debugger attachment, runtime commands, and project configuration. The upstream runtime is MIT-licensed; see [NOTICE](NOTICE) for the complete attribution and license text.

The Blender logo and name are trademarks of the Blender Foundation. This project is not affiliated with or endorsed by the Blender Foundation.

Acknowledgements:

- **JetBrains** for the IntelliJ Platform SDK and PyCharm Python APIs.
- **Jacques Lucke** for the original Blender Development runtime and workflows.
- **Blender Foundation** for Blender and its Python extension APIs.
- **Blender and PyCharm communities** for documentation, testing, and feedback.
- **AI collaborators** including Gemini, OpenAI Codex, and Junie (JetBrains AI).

## AI use disclosure

AI tools are used as development assistants for portions of implementation, repetitive maintenance, documentation synchronization, code review, auditing, and learning the Kotlin and IntelliJ Platform APIs. Changes remain subject to repository review and validation requirements.
