# Qodana Unused Operations Review

This report covers executable declarations from `docs/Qodana audit filtered.md` that currently have no production call site but
contain potentially useful behavior. They are intentionally left in place without an unused-code suppression so their missing
integration remains visible.

## Blender installation scanning

### `BlenderInstallationScanner.logNoInstallsSummary`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderInstallationScanner.kt`

The function records whether an empty scan was conclusive or affected by inaccessible roots. This is useful diagnostic behavior,
but `refreshInstalledVersionsCache()` currently calls only `notifyCriticalScanFeedback()`.

Suggested integration: call it once after all OS-specific and configured-root scans finish, using the current OS name, final
installation count, and `ScanDiagnostics.inaccessibleRoots`. Review its overlap with `notifyCriticalScanFeedback()` first so an
empty scan does not produce redundant warning messages.

## Blender editor sessions

### `BlenderEditorServerService.findSetupPayload`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt`

This is a non-destructive lookup for a setup payload. The current launch flow instead consumes setup state through
`removeSetupPayload()`. It would be useful for status inspection or retry logic that must not consume the payload.

### `BlenderEditorServerService.findActiveSessionPayload`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt`

This retrieves one active session and refreshes its activity timestamp. It is a useful primitive for commands targeted at a
specific Blender session.

### `BlenderEditorServerService.getActiveSessionPayloads`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt`

This returns every active session and refreshes all activity timestamps. It could support a session picker or broadcast command,
but callers should be aware that a read also extends every session's lifetime.

Suggested integration: introduce these lookups only through a concrete session-selection feature. Consider separating retrieval
from activity renewal first, because query methods with hidden lifetime effects are difficult to reason about and test.

## External processes

### `ExternalProcessBuilder.launchAndCaptureOutputAsync`

Locations: both overloads in `src/main/kotlin/com/sakurasedaia/blenderdevelopment/process/ExternalProcessBuilder.kt`

These overloads move synchronous process capture onto the application executor and return a `Future`. They are potentially useful
for callers that cannot block, although new IntelliJ code should generally prefer lifecycle-aware coroutines or platform progress
APIs so cancellation and project disposal propagate correctly.

Suggested integration: retain these only if an existing Java or `Future`-based caller is planned. For Kotlin UI and service code,
prefer a suspending adapter with explicit lifecycle ownership.

## UI state extraction

### `ScriptDirectoriesTable.getDirectories`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/components/ScriptDirectoriesTable.kt`

This commits an active cell edit before returning normalized directory values. The current UI pushes updates through
`setOnChangeListener()`, so there is no pull-based caller.

Suggested integration: use this at an explicit apply/validation boundary if the settings UI needs a final authoritative snapshot.
Otherwise, the listener-driven state flow makes the method removable.

## Blender version discovery

### `ScrapeBlenderVersionLists.getAvailableVersions`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/services/ScrapeBlenderVersionLists.kt`

This fetches and filters available minor versions without updating `BlenderVersionCache`. It is useful for previews, validation,
or a version selector that should not mutate shared cache state. The production refresh path currently uses
`refreshVersionCache()`.

Suggested integration: use it only for a read-only version-list workflow. Cache-refreshing workflows should continue to call
`refreshVersionCache()`.

## Resource generation

### `PluginResources.importResource`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/util/PluginResources.kt`

This loads a bundled text resource, optionally formats it, appends it to a destination file, and refreshes the virtual file
system. That is useful for additive project scaffolding, but append semantics make accidental duplicate content possible.

Suggested integration: define an idempotency rule before wiring it into a wizard or action. Depending on the resource, replacing
the file or detecting an existing generated block may be safer than unconditional append.

## Version-derived properties

### `BlenderVersion.pyMajorMinor`, `BlenderVersion.blFallback`, and `BlenderVersion.pyFallback`

Location: `src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/BlenderVersions.kt`

These derived values are useful for Python compatibility selection and fallback-version display, but they currently have no
repository callers. `blFallback` also assumes at least three Blender version components, while `pyFallback` safely handles a
missing patch component.

Suggested integration: use them only when implementing Python runtime or stub compatibility selection. Before doing so, align
their bounds behavior so malformed or shortened version lists cannot fail inconsistently.

## Intentionally preserved declarations

- `InstallBlender.updateVersion()` remains annotated with `@Suppress("unused")` because it is a TODO-only placeholder.
- `Launcher.startProcess()` remains annotated with `@UsedImplicitly` because it is an intended implicitly invoked API surface.

## Removed dead declarations

- The zero-argument `SettingsInstallationScanService.scanInstallations()` wrapper duplicated an overload whose arguments already
  have defaults.
- `BlenderToolWindowController.detectedInstallations` was injected but never read; the controller uses `PluginConfig.stateFlow`
  as its state source.
- `PluginLogger.project` retained an injected project as a property without reading it after construction.
