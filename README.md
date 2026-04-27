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

- **Integrated Blender Management**: A dedicated system for discovering, downloading, and managing multiple Blender versions (LTS 4.2+ and newer) with automatic linter configuration and sandbox isolation.
- **Real-Time Extension Development**: Robust auto-reload capabilities powered by bidirectional TCP communication, allowing for instant feedback and live updates directly within Blender as you save your code.
- **Automated Project Lifecycle**: Streamlined project creation via an integrated wizard that handles multi-source directory management, manifest configuration, and automated Python virtual environment setup using **uv**.
- **Project Maintenance & Traits**: Built-in tools for managing existing projects, including the generation of agent guidelines, run configurations, `.gitignore` templates, and GPL licenses for existing projects.
- **Platform-Aware Validation**: Smart detection of system-specific requirements, such as filesystem execution permissions (e.g., `noexec` on Linux), with guided resolution steps.
- **Global Ready**: Comprehensive internationalization support for 11 languages, ensuring a native experience for developers worldwide.

## Installation

### Prerequisites

This plugin requires **uv** for Python virtual environment management and package integration. If **uv** is not found on your system, the plugin will offer to install it for you automatically.

You can also manually obtain it from the [official uv installation guide](https://docs.astral.sh/uv/getting-started/installation/).

### Option 1: Install Prebuilt Binary (Recommended)

1. Download the latest plugin ZIP file from the [Codeberg Releases](https://codeberg.org/SakuraSedaia/blender_pycharm/releases) page.
2. In PyCharm, go to **Settings** > **Plugins**.
3. Click ⚙️ > **Install Plugin from Disk...**.
4. Select the downloaded ZIP and restart PyCharm.

### Option 2: Build from Source

1. Clone the repository: `git clone --depth 1 https://codeberg.org/SakuraSedaia/blender_pycharm.git`
2. Run build:
   - **Windows**: `.\gradlew.bat buildPlugin`
   - **macOS/Linux**: `./gradlew buildPlugin`
3. Install the ZIP from `build/distributions/` using the steps in Option 1.

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for the full license text.

## AI Usage Declaration

This project is developed in collaboration with **Junie**, an AI agent by JetBrains integrated into IntelliJ IDEA. AI assistance is utilized for:

- **Code Generation & Architecture**: Implementing core logic, refactoring, and documentation.
- **Quality Assurance**: Assisting with code reviews, optimization, and bug fixing.
- **Internationalization**: Localizing the plugin into 11+ languages.
- **Workflow Automation**: Managing repetitive tasks, git commits, and documentation updates.

**Human Oversight**: All AI-generated contributions are strictly reviewed, tested, and approved by **Sakura Sedaia** to ensure project integrity and security.

---

For legal notices and acknowledgments, please see [NOTICE.md](docs/NOTICE.md).
