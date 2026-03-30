# Blender Development for PyCharm

Blender Development for PyCharm is a comprehensive plugin that streamlines the creation and debugging of Blender extensions by enabling seamless launch and real-time reloading directly from the IDE. It features a dedicated management system for multiple Blender versions (LTS 4.2+, 5.0, & 5.1) and offers robust auto-reload capabilities powered by bidirectional TCP communication. With an integrated project wizard and multi-language support, it provides a powerful environment for developers to manage complex, multi-source projects with ease.

## Documentation
- [Installation Guide](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/getting-started/installation.html)
- [Operating Instructions](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/usage/index.html)
- [Architecture Overview](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/core-concepts/architecture.html)
- [Contributing](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/contributing/index.html)
- [Localized Wiki Links](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/index.html)
- [Notices and Acknowledgments](docs/NOTICE.md)
- [Full Documentation Wiki](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/index.html)

## Features

- **Blender Status Bar Widget**: Real-time indicator for Blender connection status and auto-reload status.
- **Multiple Source Folders**: Projects can now designate and manage multiple folders as Blender source directories.
- **Auto-Reload**: Real-time extension updates on file save with robust bidirectional TCP communication and heartbeat logic.
- **Project Template**: Integrated New Project Wizard for Blender extensions with automatic Python interpreter setup, VENV creation, and manifest configuration.
- **Blender Management**: Dedicated tool window for multi-version downloads (LTS 4.2+, 5.0, & 5.1), automatic `fake-bpy-module` linter installation, and sandbox management.
- **Python Integration**: VENV-based interpreter configuration that matches the Python version bundled with Blender, with automatic site-packages and linter path mapping. It includes a Virtual Environment Guardrail to ensure all Python operations run in a dedicated `.venv`.
- **UI Improvements**: Optimized Tool Window layout with scrollable tables and dedicated action buttons for easier Blender version management.
- **Offline Telemetry**: Local-only telemetry support to aid in debugging and stability monitoring.
- **Internationalization**: Full i18n support for 11 languages (Spanish, German, French, Italian, Japanese, Korean, Dutch, Polish, Portuguese, Russian, and Chinese).

## Installation

### Option 1: Install Prebuilt Binary (Recommended)

1. Download the latest plugin ZIP file from the [GitHub Releases](https://github.com/Sakura-Sedaia/BlenderExtensions/releases) page.
2. In PyCharm, go to **Settings** > **Plugins**.
3. Click ⚙️ > **Install Plugin from Disk...**.
4. Select the downloaded ZIP and restart PyCharm.

### Option 2: Build from Source

1. Clone the repository: `git clone --depth 1 https://github.com/Sakura-Sedaia/BlenderExtensions.git`
2. Run build:
   - **Windows**: `.\gradlew.bat buildPlugin`
   - **macOS/Linux**: `./gradlew buildPlugin`
3. Install the ZIP from `build/distributions/` using the steps in Option 1.

## Future Plans

- **Enhanced UI**: More granular control over symlink management and multi-extension projects.
- **Deeper Integration**: Better support for Blender's internal asset browser and library management.

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for the full license text.

---

For legal notices and acknowledgments, please see [NOTICE.md](docs/NOTICE.md).
