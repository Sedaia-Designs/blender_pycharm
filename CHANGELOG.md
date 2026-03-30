# Changelog

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
