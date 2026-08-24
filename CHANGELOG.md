<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Blender Development for PyCharm Changelog

## [Unreleased]

### Added

- Added optional `blender-workspace.toml` configuration for sharing portable project settings. Project Blender Manager can
  create the file and automatically keeps it synchronized while machine-specific paths and environment values remain local.

## [1.0.0-beta.3] - 2026-08-16

Beta 3 completes the V1 runtime-authentication gate and strengthens release readiness across managed installations, IDE
compatibility, and contributor validation. Runtime commands, setup messages, and early failure reports now share an
authenticated protocol with bounded, validated request handling.

Managed installations now honor post-install archive cleanup, Plugin Verifier covers both declared PyCharm targets, and the
project's Python requirement matches Blender 4.2's bundled runtime. Repeatable Kotlin formatting and expanded release
validation make the remaining V1 risks explicit and reproducible.

### Added

- Added persisted ownership metadata that distinguishes user-managed Blender installations from installations managed by the
  plugin, while treating installations saved by earlier versions as user-managed.
- Added Plugin Verifier coverage for the declared PyCharm 2026.1 and 2026.2 targets.
- Added a V1 release-readiness audit covering release blockers, security risks, stability issues, and validation results.
- Added repeatable Kotlin and Gradle Kotlin formatting through Spotless and ktfmt, with a two-space block indent, 140-column
  limit, and `spotlessCheck` validation task.

### Changed

- Standardized plugin diagnostics around typed error factories while keeping developer logging separate from user-facing
  notifications.
- Moved Ruff configuration to [.ruff.toml](.ruff.toml) with explicit Python-version, formatting, linting, and vendored-file
  exclusions.
- Restricted generated Marketplace change notes to the current release section.
- Standardized [MessageBundle.properties](src/main/resources/messages/MessageBundle.properties) keys across notifications, run
  configurations, and UI labels.

### Fixed

- Fixed **Perform post-install cleanup?** so successful managed Blender installations remove their downloaded archives when
  enabled while preserving archives after failed installations or when cleanup is disabled.
- Fixed generated starter Python scripts so both minimal and example-code projects contain a correctly indented registration
  call.
- Fixed plugin compatibility metadata to depend on the bundled `PythonCore` plugin directly, allowing Plugin Verifier to
  resolve the Python APIs used by the plugin on every declared target.
- Isolated JVM platform tests from unrelated bundled IDE plugins, eliminating environment-dependent fixture shutdown failures.
- Replaced deprecated IntelliJ renderer and Kotlin JVM-default compatibility paths used by the plugin.
- Aligned the root Python requirement with Blender 4.2's bundled Python baseline by lowering it from 3.14 to 3.11.7.

### Security

- Finalized per-launch HMAC-SHA-256 authentication for runtime commands, setup messages, and early bootstrap or dependency
  failures, with exact-body signing, bounded JSON requests, schema validation, replay and cross-session rejection, distinct
  HTTP failure responses, and authentication-key cleanup. Localhost traffic remains unencrypted; HMAC provides authenticity
  and integrity, not confidentiality.

### Known Issues

- Managed Blender archives are not yet verified against published checksums before extraction.
- Project-template values are not yet escaped specifically for TOML and Python output formats.
- Runtime actions still target the most recently registered Blender session when multiple sessions are active.
- Several visible cache, stub-path, and logging-path settings are persisted but not yet applied at runtime.
- Replacing an installed Blender API stub package is not transactional if installation of the new package fails.

## [1.0.0-beta.2] - 2026-08-10

This beta expands Blender's Run and Debug integration with dedicated command and extension-build configurations, while
improving compatibility with PyCharm 2026.1 and making managed Blender installations more reliable. Plugin settings now
present Blender discovery and version management as one workflow, with immediate installation-state updates and built-in
system diagnostics.

### Added

- Added a dedicated **Run Blender Command** configuration with an editable command selector, guided completion for built-in
  extension commands and options, validation, and support for installation-defined commands.
- Added a dedicated **Build or Validate Extension** configuration with operation-specific source and output path controls.
  Paths can be project-relative or absolute, with portable project-relative defaults.
- The Install Path now renders on the Settings Interface for the selected Blender version

### Changed

- Synchronized Marketplace change notes directly from the full changelog instead of publishing only a link to it.
- Consolidated Blender discovery and version management into a single **Blender Versions** settings section.
- Refactored global Blender settings into focused UI, coordination, and operation components with explicit lifecycle ownership.
- Reorganized the Visual Flow of the Blender settings.
- Moved managed-installation actions beside the version table as accessible icon-only buttons.
- Updated Blender API stub package installation and removal to invoke `pip` through the selected Python interpreter.
- Updated debugger attachment and tool-window lifecycle handling to avoid removed or internal PyCharm APIs.
- Standardized plugin diagnostics around stable error codes for process execution, Blender runtime communication, installation
  scanning, configuration, project generation, notifications, and API stub management.
- Embedded generated plugin-version metadata for runtime resource extraction.
- Improved the Pyproject.toml file template to be more accurate to Blender's style guide.
- Changed macOS DMG extraction by mounting images through `diskutil`, copying only `Blender.app`, parsing the attached disk
  identifier, and ejecting the image during cleanup.
  - macOS 27 Golden Gate deprecates the `hdiutil` cli tool, as such, the extraction pipeline now uses its newer alternative, `diskutil`.

### Fixed

- Fixed managed installations so successful install and removal operations update the selected version row immediately.
- Fixed installation scans so the overall scan and each Blender version probe have bounded runtimes, active probes stop on
  cancellation, and interrupted scans preserve the previous detected-installation cache.
- Fixed archive extraction collisions by using an isolated temporary directory for each managed Blender installation.


### Known Issues

- Managed Blender installations do not yet support in-place version updates.
- Blender API stub integration and Python-version update workflows remain incomplete.
- Run and Debug integration still requires stabilization before the full 1.0 release.
- Managed Blender archives are not verified against published checksums before extraction.
- Live Blender integration and platform-specific archive extraction are not fully covered by automated tests.

## [1.0.0-beta.1] - 2026-08-07

The 1.0.0-beta.1 release begins the final stabilization phase toward 1.0. The core feature set is largely complete, while
the remaining work focuses on Python-version updates, Blender API stub integration, Run and Debug behavior, release
hardening, and code-quality review.

### Added

- Added a combined release workflow for building, validating, tagging, and publishing pre-releases to GitLab and the
  JetBrains Marketplace `dev` channel.
- Added destination-specific release options for repository-only and Marketplace-only publication or recovery.
- Added explicit publication confirmation, clean-worktree validation, local tag verification, and optional branch and tag
  pushing to the combined release workflow.
- Added JetBrains Marketplace signing and publishing configuration using protected certificate files and an environment
  token.

### Changed

- Updated the IntelliJ Platform Gradle settings plugin from 2.16.0 to 2.18.1.
- Reworked Marketplace change notes to reference the full changelog instead of duplicating a release-specific summary in
  the build configuration.
- Extended release publication and validation to support ordinal SemVer `alpha`, `beta`, and `rc` versions while retaining
  compatibility with legacy snapshot version mapping.

### Fixed

- None.

### Removed

- None.

### Known Issues

- Managed Blender installations do not yet support in-place version updates.
- Blender API stub integration and Python-version update workflows remain incomplete.
- Run and Debug integration still requires stabilization before the full 1.0 release.

## [0.10.0-Snapshot] - 2026-07-23

The 0.10.0 snapshot does not add many new features. With the plugin's core feature set largely established in 0.9.0, this
release instead focuses on refining the underlying configuration systems, improving state consistency, and polishing the
overall user experience.

Application- and project-level configuration now expose immutable observable snapshots, giving the Project Blender Manager
a more reliable source of state and ensuring persisted Blender installations are available when a project starts. Blender
installation discovery and caching were strengthened so detected names, versions, and paths survive IDE restarts, while
relative executable paths are resolved consistently against the project directory.

The Project Blender Manager was also reorganized to make its most important controls easier to find and understand. Blender
installation selection is more compact, related launch controls are grouped under `Run and Debug`, and redundant target
version and repository controls were removed. Alongside these structural changes, 0.10.0 fixes recursive table-editor updates
and other state synchronization problems that could previously cause stack overflows or leave the UI out of step with saved
configuration.

### Added

- Added installation-location metadata to Blender compatibility entries, keyed by whether Blender was installed by the user
  or managed by PyCharm.
- Added read-only observable snapshots for application- and project-level configuration.

### Changed

- Compacted the Blender installation selector into a full-width combo box with a right-aligned, icon-only refresh action.
- Consolidated the add-on name, source folder, and launch controls under `Run and Debug`.
- Reworked Project Blender Manager around immutable configuration-driven UI state with tool-window-scoped observation.
- Exposed Blender and Python version component lists directly on `BlenderVersion` as `blVersionList` and `pyVersionList`.

### Deprecated

- Deprecated `BlenderVersions.mergeDiscoveredVersions` pending replacement by UI-specific version handling.

### Fixed

- Fixed stack overflows when editing environment variables or browsing for a script directory.
- Fixed discovered Blender installation caching so names, versions, and paths persist across IDE restarts.
- Fixed managed Blender installation and download paths so Settings changes take effect without restarting the IDE.
- Fixed Project Blender Manager startup so persisted Blender installations populate without requiring another scan.
- Fixed relative project Blender executable paths so they resolve against the project directory before being stored.

### Removed

- Removed the editable extension repository field from Project Blender Manager. The persisted repository name remains
  available for internal runtime use.
- Removed the redundant Target Blender Version selector, API stub update action, and persisted target-version setting from
  Project Blender Manager.

### Known Issues

- This remains a pre-release and is not production-hardened.
- Managed Blender installations do not yet support in-place version updates.
- Managed Blender downloads do not yet enforce automatic cleanup or cache-size limits.
- Managed Blender archives are not verified against published checksums before extraction.
- Managed Blender extraction uses a shared staging directory and is not isolated against concurrent or stale extractions.
- Live Blender integration and platform-specific archive extraction are not covered by the automated JVM and Python test
  suites.

## [0.9.0-Snapshot] - 2026-07-22

This pre-release adds online Blender release discovery and managed Blender installations, allowing compatible Blender
versions to be refreshed, installed, and removed directly from the plugin settings.

### Added

- Added online Blender release discovery with a persistent application-level cache and configurable minimum release.
- Added scheduled background refreshes, including an immediate refresh when no valid cache exists at startup.
- Added Settings controls to refresh and clear the version cache, with notification feedback and plugin logging.
- Added managed download, extraction, installation, and removal for host-compatible Blender distributions.
- Added compatibility-table rows for discovered minor releases while preserving declared Python metadata for built-in rows.

### Changed

- Updated Blender version management to retain the newest discovered patch per minor release, fall back to the built-in
  compatibility table while offline, and update the installation status without reopening Settings.
- Updated the plugin description to reflect the current PyCharm project, run, debug, and Blender integration workflows.

### Fixed

- Fixed Blender download URL generation and validation for the current host operating system and architecture.
- Fixed the plugin settings page so its content scrolls vertically when the available height is limited.

### Removed

- None.

### Known Issues

- This remains a pre-release and is not production-hardened.
- Managed Blender installations do not yet support in-place version updates.
- Live Blender integration and platform-specific archive extraction are not covered by the automated JVM and Python test suites.

## [0.8.1-Snapshot] - 2026-07-22

This patch release makes repository-synced Blender extension launches reliably switch from stale source or symlink module
names to the extension ID declared in `blender_manifest.toml`.

### Added

- Added regression coverage for persisted extension module names during repository-sync launches.

### Changed

- None.

### Fixed

- Fixed Run and Debug repository-sync launches by disabling stale extension module names before enabling the manifest ID.

### Removed

- None.

### Known Issues

- This remains a pre-release and is not production-hardened.
- Live Blender integration is not covered by the automated JVM and Python test suites.


## [0.8.0-Snapshot] - 2026-07-20

This pre-release builds on `v0.7.0-Snapshot` with structured Blender extension metadata handling, expanded runtime
diagnostics, a simplified runtime source layout, and a repeatable hosted release workflow.

### Added

- Added a typed TOML reader for Blender extension manifests, including focused parsing and validation tests.
- Added Blender-side runtime communication and repository-sync logging with Python regression coverage.
- Added an idempotent snapshot release script that validates the tag and changelog, builds the plugin,
  publishes the pre-release, uploads the distribution ZIP, and verifies its SHA-256 hash.
- Added repository issue templates and a shared Gradle run configuration for building the plugin distribution.
- Added project documentation for snapshot publishing and updated runtime workflow and troubleshooting guidance.

### Changed

- Flattened the bundled Blender runtime sources into `src/main/python` and updated packaging and attribution paths.
- Reworked extension reloads to read manifest metadata through the structured manifest model.
- Expanded runtime command diagnostics around requests, responses, reloads, script execution, and Blender shutdown.

### Fixed

- Fixed extension reloads so the manifest extension ID is used instead of the source directory name.
- Fixed runtime repository-sync launch behavior and covered generated launch scripts with Python tests.
- Fixed plugin packaging so transitive Kotlin standard-library jars cannot shadow the IntelliJ Platform runtime.

### Removed

- Removed the superseded nested `src/main/blender-runtime/include/blender_pycharm` source layout.

### Known Issues

- This remains a pre-release and is not production-hardened.
- Live Blender integration is not covered by the automated JVM and Python test suites.

## [0.7.0-Snapshot] - 2026-07-19

This pre-release is a substantial PyCharm-focused rewrite. The best-effort comparison baseline is
`v0.6.0-Snapshot`; because the current `main` history was rebuilt independently, this summary was derived from
the current branch history and a direct tree comparison rather than a shared-ancestor commit range.

### Added

- Added a PyCharm-native Blender project wizard with native Python environment selection, optional uv support,
  source-root setup, and generated add-on or extension scaffolding.
- Added generation of `pyproject.toml`, `blender_manifest.toml`, GPL licensing, project README content, and
  optional example add-on code.
- Added the Project Blender Manager tool window for Blender paths, target versions, source folders, module names,
  command-line arguments, log levels, reload-on-save, just-my-code, extension repositories, script directories,
  and project environment variables.
- Added application-level environment variables, installation scan configuration, and plugin log settings.
- Added Windows, macOS, Linux, and custom-root Blender installation discovery with version probing and cached
  selection state.
- Added Blender Run and Debug configurations, generated bootstrap scripts, bundled runtime extraction, debugger
  attachment, process-lifecycle cleanup, and stale-script cleanup.
- Added runtime actions for running a Python script, reloading configured add-ons, and stopping Blender.
- Added Debug reload-on-save support and project/add-on path mapping.
- Added version-matched `fake-bpy-module` dependency resolution and project dependency updates for Blender 4.2,
  4.5, and 5.2 targets.
- Added project and application services for configuration persistence, runtime sessions, external processes,
  logging, notifications, installation scanning, and stub installation.
- Added focused tests for project configuration, plugin configuration, Blender versions, process handling,
  runtime cleanup, project generation, project settings, and Blender API stub workflows.
- Added project-facing HTML documentation, contributor guidance, runtime attribution, and source-level notices.

### Changed

- Retargeted the plugin to PyCharm 2026.1 or newer, Java 21, and Kotlin/JVM 21.
- Reorganized production code under the `com.sakurasedaia.blenderdevelopment` package and separated core,
  process, run, state, stubs, UI, utility, and wizard responsibilities.
- Replaced the legacy project wizard and interpreter automation with PyCharm platform APIs and native Python
  environment controls.
- Replaced direct process helpers with `ExternalProcessBuilder` and consistent `ProcessHandler` lifecycle
  propagation.
- Reworked runtime bootstrap packaging so Blender-side Python files are archived into the plugin and extracted
  using a plugin-version and archive-hash fingerprint.
- Consolidated user-visible strings into `MessageBundle.properties` and refreshed icons for light and dark themes.
- Updated the selectable Blender/Python registry and added an explicit latest-stub mapping for Blender 5.2.
- Updated repository guidelines, build metadata, README content, licensing, and Blender runtime attribution.

### Fixed

- Fixed Blender stub requirement overrides and project dependency-file updates.
- Fixed runtime bootstrap cleanup, session expiration, termination propagation, and missing-dependency reporting.
- Fixed project wizard layout, Python module-name validation, source-root assignment, and generated project
  metadata handling.
- Fixed notification title resolution and removed redundant message bundle entries.
- Fixed generated main-script indentation so the default example project is syntactically structured correctly.
- Fixed runtime archive packaging so Python cache files are excluded.

### Removed

- Removed the legacy automated Blender download, installation, update, and sandbox workflows; installation
  management is discovery-only in this snapshot.
- Removed the legacy automated Python/uv setup path in favor of native PyCharm environment selection.
- Removed the previous `com.sakurasedaia.blenderextensions` implementation after migrating the supported workflow
  into the rewritten package structure.
- Removed the old telemetry, status widget, localized bundle set, live templates, and superseded project/run
  configuration implementations.
- Removed tracked internal wiki documents; internal technical notes now remain local-only.

### Known Issues

- This remains a pre-release and is not production-hardened.
- Local runtime HTTP command channels are loopback-only but are not yet authenticated.
- Some discovered Windows and custom installation records may contain a directory rather than the final executable.
- The default runtime dependency bootstrap does not yet enforce installed versions or wheel hashes.
- Normal Run does not establish the full runtime command/debug handshake used by Debug.
- Live Blender integration is not covered by the JVM test suite.

## [0.6.0] - 4-26-2026
### Added
- **uv-Powered Python Integration**: Implemented a mandatory `uv` integration for ultra-fast virtual environment management and linter installation.
  - Added automated `uv` installation for the user and support for specific Python versions per Blender release.
  - Deep integration with PyCharm's native `uv` metadata.
  - Enhanced `uv` venv creation with the `--seed` flag to ensure `pip`, `setuptools`, and `wheel` are always present, resolving packaging tool failures.
- **Project Traits Management**: Added new capabilities to the Blender tool window to generate essential project files for existing projects.
  - Supports generation of Junie Agent Guidelines, default Run Configurations, `.gitignore` templates, and GPL V3 LICENSE files.
- **Background Task Management**: Centralized all long-running operations (linter setup, downloads, reloads) into a new `BlenderTaskManager` to ensure a smooth, non-blocking IDE experience.
- **Execution Validation**: Implemented proactive detection of filesystem execution restrictions (e.g., `noexec` on Linux). The plugin now identifies restricted partitions and provides actionable troubleshooting guides.
- **User Permission Prompt**: Added explicit confirmation dialogs before the plugin performs invasive operations like Python SDK management or environment recreation.
  - Improved Python version tracking to use full semantic versions (e.g., 3.11.7) for better compatibility with specific Blender releases.
- **Unified Path Utility**: Consolidated `BlenderProjectPaths`, `FileUtil`, and `PathUtils` into a single, well-organized `PathUtils.kt` class. This eliminates architectural technical debt and provides a single, logical authority for path definitions, filesystem operations, and discovery.
- **Improved Deletion Safety**: Implemented `PathUtils.safelyDeleteRecursively` as a centralized, protected entry point for all recursive file operations, ensuring source and system files are never deleted by accident.
- **Architectural Refactoring**: Split `PathUtils` into a multi-file submodule under `common.utils.paths` for better maintainability and clearer logical separation of concerns (Constants, Project Paths, Discovery, Analysis, etc.).
- **Enhanced Blender Discovery**: Improved `BlenderScanner` and `PathUtils` for more reliable detection of system-wide installations across all platforms.
- **Robust Downloader**: Overhauled `BlenderDownloader` and `ArchiveUtil` to improve extraction reliability and properly handle Unix execution permissions.
- **Internationalization (i18n)**: Audited and cleaned up the `LangManager` message bundles.
  - Removed redundant and unused keys and standardized terminology (using "Module" consistently for Blender components).
  - Synchronized all 11 supported language bundles.
  - Created unified `button.yes` and `button.no` keys and updated confirmation dialogs across the UI for better consistency.
  - Migrated all hardcoded UI strings from the project generators (Extension and Single-File Add-on) — checkbox labels, row labels, tooltips, and validation messages — into `LangManager.properties` under a new `project.generator.*` namespace, and removed an additional 18 unused keys.
- **Version Updates**: Updated supported Blender and Python version metadata to include the latest releases.

### Changed
- **UI Modernization**: Updated the Virtual Environment recreation prompt to display the project name instead of the absolute file path for a cleaner, more user-friendly interface.
  - Refactored the Tool Window, Settings, and Run Configuration editors using modern Kotlin DSL components for a cleaner, more responsive interface.
- **Repository & CI Migration**: Migrated the project templates and CI workflows to the repository's then-current forge configuration.
  - Fixed the `release.yml` workflow to use the correct `inputs.version` syntax for `workflow_dispatch`.
- **Process Management**: Refactored Blender process launching to use `KillableProcessHandler`, improving responsiveness and termination handling.
- **Run Configuration Flow**: Improved path resolution and validation to ensure managed versions are correctly handled before launch.
- **Enhanced Build & Validate**: Added dedicated inputs for source and output directories in Build and Validate run configurations, allowing for more flexible extension packaging.
- **Reactive Error Handling**: Refactored permission checks to be reactive, triggering detailed diagnostics only when a "Permission denied" error occurs.
- **Sandbox Management**: Relocated the Sandbox directory from `.venv/blender_sandbox` to `.blender_sandbox` at the project root level.
  - This preserves sandbox data when switching Python virtual environments or recreating the `.venv`.
  - The folder is now automatically marked as "excluded" in the IDE to prevent indexing and tracking.

### Fixed
- **Filesystem Safety & Protection**: Implemented robust protections to prevent accidental deletion of source files or system directories.
  - Added strict `isVenv` validation that excludes system paths and requires the presence of `pyvenv.cfg`.
  - Introduced `isSafeToDelete` checks for all recursive deletion operations, ensuring they are contained within project boundaries or managed download directories.
  - Added explicit blacklisting for critical system paths and common user folders (Documents, Desktop, etc.).
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
- **Documentation**: Simplified internal documentation to English-only to ensure maintainability. Moved comprehensive and localized guides to the [external documentation site](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/index.html).
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
