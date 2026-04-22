# Changelog

## [0.6.0] - 2026-04-19
### Added
- **Execution Validation**: Implemented proactive and reactive detection of filesystem execution restrictions (e.g., `noexec` on Linux). The plugin now identifies when a partition prevents binary execution and provides user-friendly error messages with direct links to troubleshooting guides.
- **UI Modernization**: Updated the Tool Window, Settings, and Run Configuration editors with modern Kotlin DSL components for a cleaner and more responsive user experience.
- **Enhanced Blender Discovery**: Improved the `BlenderScanner` and `BlenderPathUtil` to more reliably detect system-wide Blender installations across Linux and macOS, including better support for custom paths.
- **Robust Downloader**: Overhauled `BlenderDownloader` and `ArchiveUtil` to improve extraction reliability and properly handle Unix execution permissions post-download.
- **Internationalization (i18n)**: Enhanced multi-language support, specifically updating Spanish translations and externalizing complex diagnostic messages.
- **Version Updates**: Updated supported Blender and Python version metadata to include the latest releases.

### Changed
- **Process Management**: Refactored Blender process launching to use `KillableProcessHandler` for improved process management and more responsive termination.
- **UI Modernization**: Improved the Blender Tool Window layout and added descriptive text to the settings button.
- **Reactive Error Handling**: Refactored permission checks to be reactive, triggering detailed diagnostics only when a "Permission denied" error is encountered during process startup.
- **Refactored Service Layer**: Enhanced `BlenderService` and related components (`BlenderLinker`, `BlenderCommunicationService`) for better project lifecycle management and UI integration.
- **Run Configuration Flow**: Improved path resolution and validation within Blender Run Configurations to ensure managed versions are correctly handled before launch.

### Fixed
- **Cancellation Responsiveness**: Added `checkCanceled()` calls and improved exception handling to ensure the IDE remains responsive and the startup process can be aborted by the user.
- **Process State Race Condition**: Resolved a race condition in `BlenderService` where the process state could be incorrectly reported during startup.
- **Permission Denied (Error 13)**: Resolved issues where Blender would fail to launch on Linux partitions mounted with restrictive flags by providing clear diagnostic feedback.
- **Downloader Extraction**: Fixed various edge cases in the extraction logic that could lead to corrupted or incomplete Blender installations.

## [0.5.0] - 2026-03-29
### Added
- **Virtual Environment Guardrail**: Introduced a project-wide guardrail that automatically ensures all Python-related operations (linter setup, etc.) run within a dedicated virtual environment (`.venv`) at the project root. It creates one using the latest available system Python if it doesn't exist.
- **Linter Setup Improvements**:
  - Enhanced the "Setup Linter" process to explicitly use the virtual environment's `pip`. It now runs `ensurepip` to guarantee `pip` availability before installation.
  - Simplified the linter setup by ensuring only the Blender Major.Minor version is passed to the Linter from the UI, adding logic to automatically use the "Latest" option if the latest version of Blender is specified.

### Changed
- **Refactored UI**: Moved the Tables for managing and seeing Blender Versions to the User Preferences, and focused the Tool Window to only contain Sandbox Settings, Version Selection, and Linter Setup.
- **Refocused UI**: Simplified the Tool Window and Settings UI by removing the dedicated Python installations table and consolidating interpreter setup into a direct linter installation flow.
- **Internationalization (i18n)**: Migrated all hardcoded strings in `logger.log` and notifications to the Language Bundle. Synchronized all supported languages (`de`, `es`, `fr`, `it`, `ja`, `ko`, `nl`, `pl`, `pt`, `ru`, `zh`) with the updated English keys.
- **SDK Metadata Management**: Improved SDK creation to reliably identify virtual environments and correctly set the home path and version metadata.

### Fixed
- **EDT Conflict**: Fixed an issue where the plugin would attempt to run both the Telemetry and Debug Instance of Blender at the same time, causing a thread conflict.

### Removed
- **Automated Python Installation**: Removed the plugin's capability to download, install, and manage system-level Python interpreters.

## [0.4.0] - 2026-03-15
### Added
- **Integrated Linter Setup**: The "Setup Python Interpreter" action now automatically triggers the installation and configuration of the `fake-bpy-module` linter for the selected Blender version.
- **Junie Agent Guidelines**: Introduced a dedicated set of instructions and specialized skills for AI agents (`.agent/junie_instructions.md`) to ensure consistent behavior, standardized commit messages, and correct environment management.
- **Linter Progress Indicators**: Real-time progress for linter file installations is now visible in the Tool Window's progress panels, providing clear feedback during the `pip` installation process.
- **Managed/System Blender Actions**: Refactored the tool window to use dedicated button panels below both the Managed and System tables for easier management.
- **Setup Linter**: Connected the "Setup Linter" buttons in the Tool Window to the automatic linter installation and configuration logic, allowing users to manually trigger `fake-bpy-module` installation for any Blender version.
- **Python Interpreter Setup**: Added functionality to automatically configure the project's Python interpreter to use the one bundled with a selected Blender installation.
- **Linting Support**: Automatically installs `fake-bpy-module` via `pip` when a Blender version is downloaded.
- **Interpreter Path Configuration**: Programmatically configures the Python SDK's classpath to include the standard library, Blender modules, and the installed `fake-bpy-module` linting files.

### Changed
- **Blender Downloader**: Simplified the Blender extraction process and flattened the directory structure to reduce nesting, now using a dedicated `app` subfolder (e.g., `system/blender_downloads/app/<version>`). Improved version management by ensuring version directories are created only during extraction and added macOS-specific app renaming (e.g., `Blender 4.2.app`) for better identification.
- **Table Layout**: Separated Managed and System Blender installation tables into individual classes and adjusted column widths for better readability.
- **Run Configuration**: Removed the "Custom" Blender path selection from Run Configurations to focus on using managed and detected system installations.

### Fixed
- **SDK Management**: Resolved a `SymbolicIdAlreadyExistsException` and potential infinite loops when programmatically configuring Python interpreters and linting paths.
- **UI Improvements**: Moved the Blender installation path to a dedicated read-only field in the System table for easier access and added an "Interpreter" column to the tables.
- **Python Interpreter Setup**: Fixed a `Write access is allowed inside write-action only` error and an `Unknown Sdk type` error when configuring the Blender Python interpreter.
- **Code Quality**: Removed unused attributes and refined internal API for version management.
- **Fixed Softlock in NPW**: Addressed a softlock issue in the New Project Wizard.

## [0.3.0] - 2026-03-08
### Added
- **Blender Status Bar Widget**: New indicator in the IDE status bar showing connection status to Blender.
- **Support for Multiple Source Folders**: Projects can now designate and manage multiple folders as Blender source directories.
- **Automatic Python Interpreter Setup**: Streamlined environment configuration for new projects.
- **Offline Telemetry**: Added local-only telemetry for debugging and error reporting.
- **Internationalization**: Full i18n support for 11 languages (Spanish, German, French, Italian, Japanese, Korean, Dutch, Polish, Portuguese, Russian, and Chinese).
- **Unit & Integration Testing**: Added a comprehensive test suite, including headless integration tests for TCP heartbeat and reload logic.
- **Sandbox Management**: New tool window for clearing and managing Blender sandboxed environments.
- **Bidirectional Heartbeat**: Implemented a more robust TCP client with bidirectional heartbeat and automatic retry logic for connection stability.

### Changed
- **Localization Refactor**: Standardized all resource bundle keys and migrated from `BlenderBundle` to `LangManager` (extending `DynamicBundle`).
- **Improved Blender Downloader**: Refined extraction logic and updated the selectable version list to focus on LTS releases.
- **Path Resolution**: Centralized and improved cross-platform path handling using Kotlin NIO.2 (`java.nio.file.Path`) utilities.
- **Documentation Migration**: Moved comprehensive guides to a new Sphinx-based documentation site.
- **License Change**: Updated project license to officially use GNU GPL v3.
- **Configuration Discovery**: Switched to dynamic detection and copying of Blender configuration subdirectories (system vs. user) to handle different OS layouts.

### Fixed
- **macOS Compatibility**: Prevented installation of Blender 5.0+ on Intel-based Macs and integrated `tryWhich` for better executable detection.
- **Manifest Validation**: Switched extension Manifest IDs to `snake_case` to comply with Blender requirements.
- **Run Configuration Stability**: Fixed absolute path handling for sandboxed installations and corrected CLI argument syntax for preset configurations.
- **UI Stability**: Resolved crashes in the version management tool window and improved New Project Wizard validation.
- **Logging**: Added log rotation for better disk usage management and expanded debug output for connection handshakes.

## [0.2.0] - 2026-03-01
### Added
- **Blender Status Indicator**: Added a real-time status bar widget to monitor Blender connection states (Connected, Disconnected, Not Running).
- **Internationalization**: Comprehensive i18n support for all user-facing UI, logs, and console outputs across 11 languages (Spanish, German, French, Italian, Japanese, Korean, Dutch, Polish, Portuguese, Russian, and Chinese).
- **Blender Development Project**: New specialized project type for Blender extension development.
- **Improved Scanner**: Enhanced macOS and Linux Blender detection using the `which` command, and improved custom path labeling.
- **Custom Versions**: Support for manual specification of Blender executable paths and versioning.
- **Source Management**: Option to mark project folders as Blender source directories for better organization.
- **Cross-Platform Compatibility**: Refined path handling for Windows, macOS, and Linux.
- **Documentation**: Simplified internal documentation to English-only to ensure maintainability. Moved comprehensive and localized guides to the [external documentation site](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/index.html). Localized wiki links are available in [WIKI_LOCALIZED.md](docs/WIKI_LOCALIZED.md).
- **Unit Testing**: Initial suite of unit tests for core plugin functionality.
- **Sandbox Control**: New setting to toggle sandboxing for Blender instances within the New Project Wizard.

### Changed
- **Branding**: Renamed the plugin to **Blender Development** and updated all icons to comply with JetBrains Icon guidelines. Added standardized scaling and positioning for Blender logo icons.
- **Improved**: Added folder icons for directories marked as Blender source folders in the project view.
- **Environment Setup**: Automated the detection and replication of system Blender configuration subdirectories to ensure a consistent sandboxed environment.
- **Diagnostics**: Improved logging with per-day rotation, more detailed configuration, and specific error reporting for extraction/mounting failures.
- **Run Configurations**: Updated templates for testing, building, and validation with a dynamic UI.
  - Removed redundant `--app-template pycharm` arguments when executing `build` and `validate` commands.
  - Enhanced logic for detecting extension-specific commands.
  - Standardized internal `src` path handling using Kotlin NIO.2 utilities for better OS reliability.
- **Licensing**: Transitioned to GNU GPL v3 and moved license text to a standalone template.

### Fixed
- **Management UI**: Reworked the Blender version and sandbox management tool window for better stability.
- **Manifest Formatting**: Switched Manifest IDs from kebab-case to snake_case to comply with Blender's validation requirements.
- **CLI Arguments**: Corrected the extension command syntax in run configurations, fixing a pluralization error.
- **Stability**: Fixed crashes in the version management tool window and resolved validation issues in the New Project Wizard.
- **Path Resolution**: Fixed the `FATAL_ERROR: Missing local "src"` by utilizing absolute paths for the `--source-dir` argument.
- **Process Management**: Configured the `GeneralCommandLine` working directory to ensure correct resolution of relative paths.
- **Extension Logic**: Fixed a bug where `--app-template` was incorrectly applied to CLI-based extension operations.


## [0.1.0] - 2026-02-20
- Initial release of Blender Development for PyCharm in alpha
