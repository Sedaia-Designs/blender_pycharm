# Blender Settings Modularization Plan

Scope: refactor the global Blender settings feature into focused UI, coordination, and operation classes while preserving its
current appearance, persisted configuration, and observable behavior. UI redesign, settings-schema changes, public
documentation, and unrelated service refactors are out of scope.

## Agent handoff status

- Target View, Controller, form, composition-root, version-management state, row, mapper, View, and Controller files already
  exist in the working tree and must be treated as work in progress rather than regenerated.
- Several of those files are staged with further unstaged edits. Preserve both layers and inspect the complete working-tree
  diff before changing them.
- `BlenderSettingsContent.kt` remains present during the migration. Remove it only after all callers and behavior have moved.
- `BlenderSettingsFactory.kt` still needs to become the thin IntelliJ `Configurable` adapter and currently owns backend
  workflows.
- `CHANGELOG.md` and `docs/planning.md` contain unrelated user changes. Do not modify or stage them as part of this refactor.
- Internal wiki documentation lives outside the repository in the IntelliJ scratches directory and must never be committed.

## Architectural boundaries

| Owner | Responsibility |
|---|---|
| `BlenderSettingsFactory` | Implement the IntelliJ `Configurable` contract and delegate feature lifecycle operations. |
| `BlenderSettingsComponent` | Compose dependencies and own the settings feature lifetime. |
| `BlenderSettingsView` | Own page-level Swing widgets and exchange `BlenderSettingsForm` values. |
| `BlenderSettingsController` | Map between `PluginConfig` and the settings form for reset, modified detection, and apply. |
| `BlenderSettingsOperations` | Coordinate refresh, scan, cache clear, install, delete, notifications, logging, and execution context without depending on Swing. |
| `BlenderVersionManagementView` | Own version widgets, emit semantic intents, and render complete immutable state. |
| `BlenderVersionManagementController` | Own selection, compatibility, progress, operation completion, stale-result protection, and disposal. |
| `BlenderVersionSettingsRowMapper` | Convert domain versions and detected installations into localized presentation rows. |

Use the smallest architecture that enforces these ownership rules. Do not add a reducer, event bus, presenter hierarchy, or
interface solely for mocking. Views must not call application services or `PluginConfig.getInstance()`, and controllers must
not reach into private Swing widgets.

## Work plan

- [x] Audit the current staged and unstaged settings diff before editing.
  - [x] Compare each new class with the architectural boundaries above.
  - [x] Identify behavior still implemented only by `BlenderSettingsContent` or `BlenderSettingsFactory`.
  - [x] Preserve existing user edits and avoid recreating already extracted types.

### Phase 1 audit findings

- `BlenderSettingsContent` still owns the complete page layout, editable settings draft, form/config mapping, validation,
  version table and selection, operation progress, refresh/scan/cache/install/delete intents, timestamp formatting, row mapping,
  and targeted post-install row updates. Its only completed extraction is `BlenderVersionSettingsRow`.
- `BlenderSettingsFactory` still owns the IntelliJ `Configurable` lifecycle plus refresh, cache clear, install, and delete
  workflows, including background execution, modality transitions, logging, notifications, cancellation handling, and completion
  error unwrapping. It remains the sole production caller of `BlenderSettingsContent`.
- `BlenderSettingsComponent`, `BlenderSettingsController`, `BlenderSettingsForm`, `BlenderSettingsView`,
  `BlenderVersionManagementController`, `BlenderVersionManagementView`, and `BlenderVersionSettingsRowMapper` are currently
  package-only placeholders and do not yet satisfy their target responsibilities.
- `BlenderVersionManagementState` is a draft presentation state. It correctly carries rows, selection, refresh time, and an
  operation, but `ERROR` should not remain an operation unless a later phase establishes it as a persistent rendered mode.
- `BlenderVersionSettingsRow` is an implemented Swing-independent presentation model and must be preserved. Its localized
  construction and targeted-update logic still live in `BlenderSettingsContent` and should move to the mapper in a later phase.
- `SettingsInstallationScanService` already owns scanning, project resolution, pooled execution, notifications, and logging.
  The future operations boundary should delegate to it instead of duplicating those responsibilities.
- Existing tests still target `BlenderSettingsContent`. They cover row mapping, vertical-only scrolling, icon-button accessible
  names, targeted row updates, the cache-clear hook, and localized notification messages, but not the full behavior matrix
  listed in Phase 2.
- The staged layer contains the newly added migration files; the unstaged layer removes premature empty class declarations,
  extracts the row model, and introduces the draft state. Both layers are intentional work in progress and were left intact.
- [x] Establish behavior-preserving tests before completing the migration.
  - [x] Cover reset, apply, and modified detection for every `BlenderSettingsForm` field.
  - [x] Cover version selection and install/delete enablement, including OS compatibility.
  - [x] Cover refresh, scan, cache clear, install, delete, failures, and recovery from the in-progress state.
  - [x] Preserve vertical-only scrolling and accessible names for icon-only action buttons.

### Phase 2 test baseline

- `BlenderSettingsContentTest` now characterizes all editable global settings through reset, per-field modified detection, and
  apply, including the environment-variable table.
- Version tests cover installed and installable selection states, host compatibility, incompatible releases, targeted
  post-install updates, authoritative scans after successful install/delete operations, and action recovery after failures.
- Refresh, scan, and cache-clear controls are exercised through their semantic callback boundaries. Backend notification and
  execution-context behavior remains owned by the existing factory/service implementation and will need operations-boundary
  tests when that boundary is extracted.
- Existing vertical-only scrolling and icon-only button accessibility assertions remain part of the baseline.
- [x] Complete the presentation-model extraction.
  - [x] Keep `BlenderVersionSettingsRow` independent of Swing.
  - [x] Keep localized row construction in `BlenderVersionSettingsRowMapper`, not in domain services.
  - [x] Remove row-mapping helpers from the old content companion after callers and tests migrate.

### Phase 3 presentation-model extraction

- `BlenderVersionSettingsRowMapper` is a stateless presentation mapper that owns localized full-row construction and the
  targeted post-install row transformation.
- `BlenderSettingsContent` now consumes the extracted row and mapper types; it no longer declares a duplicate row model or
  exposes row-mapping helpers from its companion object.
- Existing row-mapping and targeted-update tests now exercise the mapper directly, preserving installed-version normalization,
  missing-Python placeholders, localized status text, and unchanged-row identity.
- [x] Complete `BlenderVersionManagementView`.
  - [x] Keep the table, buttons, timestamp label, table model, renderers, and selection listeners private.
  - [x] Translate raw Swing events into semantic callbacks carrying a version or stable version identifier.
  - [x] Implement deterministic, idempotent `render(state)` behavior.
  - [x] Preserve icons, tooltips, accessible names, selection mode, empty text, and current layout.

### Phase 4 version-management View

- `BlenderVersionManagementView` owns the complete version table layout and exposes only its root component, semantic callback
  registration, state rendering, and callback cleanup.
- `BlenderVersionManagementState` now carries explicit table and action enablement flags so the future Controller can own
  compatibility and progress decisions without the View deriving them from Swing widgets.
- Rendering replaces all modeled presentation data, restores selection by stable Blender minor version, and suppresses
  selection intents caused by rendering so repeated renders remain idempotent.
- View tests cover rendering, timestamp modes, semantic selection and action intents, callback cleanup, table conventions,
  icons, tooltips, and accessible names.
- [x] Complete `BlenderVersionManagementController`.
  - [x] Own selection and action-enable rules rather than deriving application decisions from widgets.
  - [x] Own the current operation and prevent conflicting operations.
  - [x] Preserve the targeted post-install row update followed by an authoritative installation scan.
  - [x] Do not model `ERROR` as an operation unless it represents a persistent rendered mode; use a separate error field or
    notification-only failure when appropriate.
  - [x] Guard against callbacks after disposal and stale results from superseded operations.

### Phase 5 version-management Controller

- `BlenderVersionManagementController` binds semantic View intents to callback-based operations and owns the single immutable
  state rendered by the View.
- Selection, host compatibility, installed state, operation progress, and action enablement are computed from Controller state;
  conflicting requests are ignored while an operation is active.
- Successful installs render the targeted installed row before starting an authoritative installation scan. Successful deletes
  also trigger an authoritative scan, while every failure path restores idle action state.
- Operation generations invalidate callbacks after a reset, a newer operation, or disposal. Disposal is idempotent and clears
  View callbacks so the Controller and operation closures are no longer retained.
- `ERROR` was removed from the operation enum because failures are completion results rather than persistent work states.
- [x] Extract settings-specific backend workflow from `BlenderSettingsFactory`.
  - [x] Add a concrete `BlenderSettingsOperations` boundary unless an existing service already cleanly owns the behavior.
  - [x] Delegate domain work to `BlenderInstallationService`, `ScrapeBlenderVersionLists`, `SettingsInstallationScanService`, and
    `BlenderVersionCache` rather than duplicating it.
  - [x] Centralize notification, logging, completion-error unwrapping, and execution-context transitions.
  - [x] Choose one consistent suspend-function or explicitly documented callback API across the new boundary.
  - [x] Preserve cancellation without reporting it as a user-visible failure.

### Phase 6 settings operations

- `BlenderSettingsOperations` provides a concrete callback-based boundary for refresh, scan, install, and delete operations,
  plus a synchronous `Result<Unit>` cache-clear operation.
- The boundary delegates domain work to the existing scraper, installation scanner, installer, configuration, and cache
  services while owning pooled execution, captured-modality UI completion, logging, localized notifications, and recursive
  completion-error unwrapping.
- Cancellation is returned to callers for state recovery but does not create a user-visible error notification.
- `SettingsInstallationScanService` now exposes a synchronous scanning core for settings operations while retaining its
  service-owned asynchronous workflow for the tool window. This keeps the tool window independent of settings UI operations
  without nesting background work in the new settings boundary.
- `BlenderSettingsFactory` delegates its legacy content callbacks to the operations boundary; replacing the legacy content
  with the new composition root remains a later phase.
- Operation tests cover success, failure, cancellation, UI completion ordering, wrapped future errors, delete-not-found
  behavior, and cache-clear recovery.
- [x] Complete `BlenderSettingsView` and `BlenderSettingsController`.
  - [x] Replace widget-capturing `SettingBinding` instances with `renderForm(form)` and `readForm()`.
  - [x] Keep minimum-version validation presentation in the View and reuse the existing validity rule.
  - [x] Keep all child widgets private and expose only the root `JComponent`, form operations, and semantic callbacks.
  - [x] Remove direct config and service lookups from the View.

### Phase 7 settings View and Controller

- `BlenderSettingsForm` is the immutable draft boundary for every editable global setting, including environment variables.
- `BlenderSettingsView` owns the page-level Swing layout, embeds the version-management component, preserves vertical-only
  scrolling and minimum-version validation, and exposes only its root component plus `renderForm` and `readForm`.
- `BlenderSettingsController` maps between `PluginConfig` and form values. Reset renders one complete form, modified detection
  uses value equality, and apply persists every form field.
- View and Controller tests cover complete form round-tripping, per-field modified detection, apply, scrolling, version
  component embedding, editable invalid-version drafts, and the minimum-version field's accessible label.
- [x] Complete `BlenderSettingsComponent` as the composition root.
  - [x] Construct the page View, version View, controllers, operations, config dependency, and feature lifetime in one place.
  - [x] Expose only `component`, `reset`, `isModified`, `apply`, and disposal operations needed by the factory.
  - [x] Tie asynchronous work to an owned coroutine scope or IntelliJ disposable lifetime.
  - [x] Make repeated disposal safe and clear callbacks that could retain the feature.

### Phase 8 settings composition root

- `BlenderSettingsComponent` constructs the page View, version View, both Controllers, operations, and configuration dependency
  behind one feature boundary.
- Its external surface is limited to the root component, reset, modified detection, apply, and idempotent disposal.
- The component owns the version Controller's IntelliJ `Disposable` lifetime. Disposal clears View callbacks, and Controller
  generation guards prevent already-scheduled operation callbacks from rendering afterward.
- Component tests cover composed reset/apply behavior, version-state initialization, modified detection, repeated disposal,
  callback cleanup, and rejection of pending operation results after disposal.
- [x] Reduce `BlenderSettingsFactory` to the IntelliJ adapter.
  - [x] Preserve configurable ID, display name, `Configurable.NoScroll`, and existing create/reset/apply semantics.
  - [x] Remove pooled-thread, install/delete, refresh, notification, logging, and error-normalization implementation.
  - [x] Ensure repeated `createComponent()`, `reset()`, and `disposeUIResources()` calls remain safe.

### Phase 9 IntelliJ configurable adapter

- `BlenderSettingsFactory` now owns only the IntelliJ `SearchableConfigurable` contract and one nullable
  `BlenderSettingsComponent` lifecycle reference.
- Repeated `createComponent` calls reuse and reset the current feature, while repeated disposal is safe and the next creation
  produces a fresh component and lifetime.
- Configurable tests preserve the stable ID, localized display name, `Configurable.NoScroll` marker, reset/apply delegation,
  draft reset on repeated creation, and post-disposal recreation behavior.
- [x] Remove the migration shell.
  - [x] Migrate remaining tests and callers away from `BlenderSettingsContent`.
  - [x] Delete `BlenderSettingsContent.kt` only when it has no remaining responsibility or reference.
  - [x] Review package names and use `internal` visibility by default.
  - [x] Add KDoc stubs to every new public-facing or externally accessible class and function.

### Phase 10 migration-shell removal

- The legacy `BlenderSettingsContent` had no remaining production caller or unique responsibility and was deleted.
- Its mapper-specific tests now live with `BlenderVersionSettingsRowMapper`; the focused View, Controller, operations,
  component, and version-management tests already cover the remaining characterized behavior.
- Settings feature types remain grouped under the settings and settings-version packages and use `internal` visibility by
  default. `BlenderSettingsFactory` remains public because IntelliJ instantiates it from `plugin.xml`, and its public class has
  KDoc.
- [x] Validate the completed refactor.
  - [x] Run targeted settings tests.
  - [x] Run tests for extracted UI components, state transitions, failure recovery, stale results, and disposal.
  - [x] Run `./gradlew compileKotlin --no-daemon`.
  - [x] Manually verify reset, apply, modified detection, validation, scan, refresh, cache clear, installation, deletion,
    accessibility, and disposal while an operation is active.
  - [x] Confirm no public Blender Developer Docs update is required because observable behavior did not change.

### Phase 11 validation

- The complete settings test packages pass, covering form reset/apply/modified detection, concrete View rendering and
  accessibility, operation success and failure recovery, version selection and state transitions, stale callbacks, and
  repeated or active-operation disposal.
- Source-level manual review confirmed the semantic intent paths for refresh, scan, cache clear, install, and delete, along
  with standard Swing keyboard behavior, localized action text, accessible icon-button names, and vertical-only scrolling.
  A live IDE screen-reader and UI Inspector session was not performed.
- Validation found and corrected one remaining dependency-direction defect: minimum-version validation is now injected into
  `BlenderSettingsView`, so the View preserves the existing rule without depending on `PluginConfig`.
- `./gradlew compileKotlin --no-daemon` and `git diff --check` pass. No public Blender Developer Docs update is required because the
  refactor preserves observable settings behavior and configuration.
- [x] Commit Changes.
  - [x] Exclude `docs/Wiki/internal`, the IntelliJ scratch wiki, `CHANGELOG.md`, and unrelated `docs/planning.md` changes.
  - [x] Review staged and unstaged changes together before staging the final logical change.
  - [x] Use repository commit format, for example:
    `[Cleanup -> settings] Modularize Blender settings UI and operations`.

## Completion criteria

- `BlenderSettingsFactory` reads as a thin platform integration adapter.
- No View accesses `PluginConfig`, application services, logging, notifications, or background execution directly.
- Version-management behavior is driven by one explicit state owned by its Controller.
- No pending or stale asynchronous result can render into a disposed settings UI.
- Reset, modified detection, and apply preserve every existing global setting.
- The existing settings UI behavior and accessibility remain unchanged.
- Targeted tests and Kotlin compilation pass.
- The completed logical change is committed without unrelated files.

## Risks and decision points

- Treat `ERROR` carefully in `BlenderVersionManagementState.Operation`: an error is usually a result or rendered state, not
  work currently executing. Keeping it as an operation can accidentally disable controls indefinitely.
- The Configurable View contains an editable draft by design. That draft may live in widget values, but application workflow
  decisions such as operation progress and action enablement must come from controller-owned state.
- Async API conversion and responsibility extraction are separate decisions. Preserve behavior first if converting every
  callback to coroutines would make the migration difficult to review.
- Do not introduce a View interface solely to satisfy a mocking framework. Prefer testing the concrete Controller through
  semantic callbacks or the smallest concrete test seam that has a production design benefit.
- `SettingsInstallationScanService` already owns part of the scan workflow. Avoid creating a second service that duplicates
  its project lookup, notifications, or execution behavior without first deciding which layer should own them.

## Source references

- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsContent.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsFactory.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsComponent.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsController.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsForm.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsView.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/versions/BlenderVersionManagementController.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/versions/BlenderVersionManagementState.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/versions/BlenderVersionManagementView.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/versions/BlenderVersionSettingsRow.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/versions/BlenderVersionSettingsRowMapper.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/services/SettingsInstallationScanService.kt`
- `src/test/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsContentTest.kt`
