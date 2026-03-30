# Changelog

## [0.5.0] - 2026-03-29
### Added
- **Integrated Linter Management**: Replaced the automatic Python interpreter configuration with a streamlined "Setup Linter" action that installs `fake-bpy-module` and adds its paths to the existing project SDK.
- **Blender-to-Python Version Mapping**: Added a dedicated "Python" column to the Managed Blender table, displaying the specific Python version (e.g., Python 3.11.7) associated with each Blender installation.

### Removed
- **Automated Python Installation**: Removed the plugin's capability to download, install, and manage system-level Python interpreters.
- **Virtual Environment Management**: Deleted the `PythonInterpreterService` and all logic related to automated `.venv` creation and management.

### Changed
- **Refocused UI**: Simplified the Tool Window and Settings UI by removing the dedicated Python installations table and consolidated interpreter setup into a direct linter installation flow.
- **Project Generators**: Removed automated Python SDK configuration from the Blender extension project creation process.

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
