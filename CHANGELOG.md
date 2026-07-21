<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# BlenderPythonDevelopment Changelog

## [Unreleased]

## [0.8.0-Snapshot] - 2026-07-20

This pre-release builds on `v0.7.0-Snapshot` with structured Blender extension metadata handling, expanded runtime
diagnostics, a simplified runtime source layout, and a repeatable Codeberg release workflow.

### Added

- Added a typed TOML reader for Blender extension manifests, including focused parsing and validation tests.
- Added Blender-side runtime communication and repository-sync logging with Python regression coverage.
- Added an idempotent Codeberg snapshot release script that validates the tag and changelog, builds the plugin,
  publishes the pre-release, uploads the distribution ZIP, and verifies its SHA-256 hash.
- Added Codeberg issue templates and a shared Gradle run configuration for building the plugin distribution.
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
