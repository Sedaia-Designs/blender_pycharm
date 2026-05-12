# Blender Development for PyCharm
[![Python](https://img.shields.io/badge/python-3670A0?style=for-the-badge&logo=python&logoColor=ffdd54)](https://www.python.org/)
[![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)

## About the project

The Blender Development for Pycharm Plugin is a comprehensive tool suite aimed at bringing Pycharm's full development suite to Blender Python developers, with integration for other Intellij Platform IDE's (Such as CLion for Blender Source development) being in consideration for the future.

The Plugin is heavily inspired by and even uses some Python source code from [Jacques Lucke's Blender Development](https://github.com/JacquesLucke/blender_vscode) extension for [Visual Studio Code](https://code.visualstudio.com/).

## Credits & Licensing

### License
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for the full text.

### Attributions & Third-Party Code
* **xmake-idea:** This plugin incorporates bridge components derived from the [xmake-idea](https://github.com/xmake-io/xmake-idea) project (Apache License 2.0). See the [NOTICE](NOTICE) file for full copyright details.
* **Blender Logo & Name:** The Blender logo and the name "Blender" are registered trademarks of the **Blender Foundation**. This project is not affiliated with or endorsed by the Blender Foundation. The Blender logo is used here for community identification purposes.

### Acknowledgements
* **JetBrains:** For the IntelliJ Platform SDK.
* **AI Collaborators:** Developed with technical assistance from Gemini and Junie (JetBrains AI).
* **Blender Foundation:** For the incredible open-source and highly extensible 3D suite that makes this plugin necessary.
* **Community:** Thanks to the Blender and PyCharm communities for their ongoing support and feedback.

### AI Use Disclaimer
This Project is developed in part using AI tooling, specifically Jetbrain's Junie Agent (And Associated Models) and Google's Gemini Model
- Generate small portions of the codebase
- Automate certain tasks such as:
  - Syncing Repository Documentation with the externally hosted [Wiki](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/contributing/index.html)
  - Automate highly repetitive tasks
- Audit the codebase for bugs, vulnerabilities, and other issues.
- Assist Sakura in learning Kotlin and the Intellij Platform SDK

## Plans

This is a complete rewrite of the core plugin, making use of modern Intellij standards as well as improving some functionalities. Here is a list of items I have planned to integrate as well as bring over from the old, poorly written extension

- [x] More robust data class for managing Blender Version metadata
- [x] Blender Project Generator (Using the NewProjectWizard API isntead of DirectoryProjectGenerator)
- [ ] UV Integration for automated Python Virtual Environment Setup with Blender Extensions
  - [ ] `fake-bpy-module` Package integration, automatically setting up the appropriate linter for the plugin.
  - [ ] Automated UV installer for users who don't have UV installed
- [ ] Custom `blproject.toml` Configuration File for Blender Extensions, with Gradle-like syncing with the Blender Manifest.
- [ ] Blender Manifest GUI based editor
- [ ] Debugging Protocol for Blender, allowing users to run Blender via the IDE with a Debugger attached, which will enable users to have "Hot-Reloading" of their code
- [ ] Run Blender from the IDE in a normal capacity, allowing users to test functions in a controlled environment.
- [ ] Blender Installation management directly from within the IDE, allowing for sandboxed installations that are separate from a User's main install
- [ ] Full range of i18n Translations.
