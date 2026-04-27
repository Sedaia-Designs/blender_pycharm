# Changelog

## [0.6.0] - 4-26-2026
### Added
- **uv-Powered Python Integration ([#4](https://codeberg.org/SakuraSedaia/blender_pycharm/issues/4))**: Implemented a mandatory `uv` integration for ultra-fast virtual environment management and linter installation.
  - Added automated `uv` installation for the user and support for specific Python versions per Blender release.
  - Deep integration with PyCharm's native `uv` metadata.
  - Enhanced `uv` venv creation with the `--seed` flag to ensure `pip`, `setuptools`, and `wheel` are always present, resolving packaging tool failures.
- **Project Traits Management**: Added new capabilities to the Blender tool window to generate essential project files for existing projects.
  - Supports generation of Junie Agent Guidelines, default Run Configurations, `.gitignore` templates, and GPL V3 LICENSE files.
- **Background Task Management**: Centralized all long-running operations (linter setup, downloads, reloads) into a new `BlenderTaskManager` to ensure a smooth, non-blocking IDE experience.
- **Execution Validation**: Implemented proactive detection of filesystem execution restrictions (e.g., `noexec` on Linux). The plugin now identifies restricted partitions and provides actionable troubleshooting guides.
- **User Permission Prompt**: Added explicit confirmation dialogs before the plugin performs invasive operations like Python SDK management or environment recreation.
  - Improved Python version tracking to use full semantic versions (e.g., 3.11.7) for better compatibility with specific Blender releases.
- **Enhanced Blender Discovery**: Improved `BlenderScanner` and `BlenderPathUtil` for more reliable detection of system-wide installations across all platforms.
- **Robust Downloader**: Overhauled `BlenderDownloader` and `ArchiveUtil` to improve extraction reliability and properly handle Unix execution permissions.
- **Internationalization (i18n)**: Audited and cleaned up the `LangManager` message bundles.
  - Removed redundant and unused keys and standardized terminology (using "Module" consistently for Blender components).
  - Synchronized all 11 supported language bundles.
  - Created unified `button.yes` and `button.no` keys and updated confirmation dialogs across the UI for better consistency.
- **Version Updates**: Updated supported Blender and Python version metadata to include the latest releases.

### Changed
- **UI Modernization**: Updated the Virtual Environment recreation prompt to display the project name instead of the absolute file path for a cleaner, more user-friendly interface.
  - Refactored the Tool Window, Settings, and Run Configuration editors using modern Kotlin DSL components for a cleaner, more responsive interface.
- **Repository & CI Migration**: Migrated the project templates and CI workflows from `.github` to `.forgejo` for native Codeberg compatibility.
  - Fixed the `release.yml` workflow to use the correct `inputs.version` syntax for `workflow_dispatch`.
- **Junie Agent Guidelines**: Renamed the agent configuration directory from `.agent` to `.junie` and expanded the documentation guidelines to require detailed changelog entries when updating existing features.
  - Improved the **AI Usage Declaration** in the `README.md`.
  - Consolidated the **Features** section into core summaries.
  - Updated the project description to reflect the current state (uv integration, Project Traits, and Codeberg migration).
- **Core Refactoring**: Enhanced the service layer (`BlenderService`, `BlenderLinker`, `BlenderCommunicationService`) and centralized version parsing/comparison logic for better maintainability.
- **Process Management**: Refactored Blender process launching to use `KillableProcessHandler`, improving responsiveness and termination handling.
- **Run Configuration Flow**: Improved path resolution and validation to ensure managed versions are correctly handled before launch.
  - Resolved Issue #3 by ensuring startup scripts use deterministic filenames to prevent scratch directory clutter.
- **Enhanced Build & Validate ([#5](https://codeberg.org/SakuraSedaia/blender_pycharm/issues/5), [#6](https://codeberg.org/SakuraSedaia/blender_pycharm/issues/6))**: Added dedicated inputs for source and output directories in Build and Validate run configurations, allowing for more flexible extension packaging.
- **Reactive Error Handling**: Refactored permission checks to be reactive, triggering detailed diagnostics only when a "Permission denied" error occurs.

### Fixed
- **Thread Safety & EDT Compliance**: Fixed an issue where "Access is allowed from Event Dispatch Thread (EDT) only" would occur during Python SDK initialization and linter setup by ensuring all model-modifying calls (like `PythonSdkUpdater.update`, SDK creation, and virtual environment management) are correctly synchronized on the EDT.
- **Codebase Modernization**: Audited the codebase and replaced several deprecated IntelliJ APIs with modern recommended implementations.
  - Replaced deprecated `SdkType.getAllTypes()` with the modern `SdkType.EP_NAME.extensionList` for SDK type discovery.
  - Replaced `Messages.showYesNoDialog` with `MessageDialogBuilder` for improved dialog management.
  - Refactored `BlenderNotification` to use the standard `NotificationGroupManager` retrieval pattern.
  - Modernized various UI components and SDK initialization logic to align with the latest IntelliJ Platform guidelines.
  - Replaced deprecated `Project.baseDir` with `Project.guessProjectDir()` for more reliable project root resolution.
  - Fixed build errors in `BlenderProjectService.kt` by adding missing `Project` import and standardizing path resolution.
  - Replaced deprecated `ProgressManager.runProcessWithProgressSynchronously` with modern `Task.Modal` pattern via `BlenderTaskManager`.
- **Enhanced Debugging**: Updated `BlenderLogger` to print debug messages directly to the console (`println`) when running the IDE, ensuring immediate visibility of logs during development without extra platform configuration.
- **Archive & Download Reliability**: Implemented robust extraction using temporary directories, atomic moves, file size verification, and improved top-level directory stripping to prevent corrupted installations.
- **Thread Safety & Synchronization**: Resolved race conditions and `ConcurrentModificationException` in shared caches. Added synchronization locks in `BlenderService` to ensure atomic process initialization.
- **Communication Server Leak**: Implemented a robust cleanup mechanism in `BlenderCommunicationService` to prevent resource leaks and ensure only one active client connection.
- **Linter & Setup Stability**: Ensured valid version string usage during linter setup and improved New Project Wizard validation for sandbox settings.
- **Command & Reload Safety**: Implemented proper shell-style quoting for project paths with spaces and robust JSON serialization for extension reload commands.
- **Cancellation Responsiveness**: Improved responsiveness by adding `checkCanceled()` calls throughout long-running processes, allowing users to abort operations.
- **Linux Execution (Error 13)**: Resolved issues where Blender failed to launch on Linux partitions with restrictive mount flags by providing clear diagnostic feedback.
- **Sandbox Extension Path**: Fixed a path mismatch in sandbox mode by correctly aligning the linker path with `BLENDER_USER_SCRIPTS`.
- **Process Execution**: Replaced inefficient busy-wait loops with a listener-based approach and improved resource cleanup.

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
