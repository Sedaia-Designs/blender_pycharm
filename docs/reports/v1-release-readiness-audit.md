# V1 Release-Readiness Audit

Audit date: 2026-08-10

## Outcome

**The project is not ready for a stable V1 release yet.**

The audit found four release blockers, several high-priority correctness and security gaps, misleading settings that are
persisted but never honored, and incomplete validation. The project compiles and most tests pass, but the full release
validation currently fails.

Status markers used throughout this audit:

- ✅ **Complete** — implemented and verified for the stated scope.
- ⚠️ **Partial** — meaningful remediation exists, but release requirements or validation remain incomplete.
- ❌ **Open** — no verified remediation closes the finding.

## Reverification — 2026-08-15

The release outcome remains unchanged because other V1 blockers are still open. The local-runtime authentication gate is now
complete for its stated scope:

- ✅ Each launch receives a random 32-byte key through the Blender process environment.
- ✅ Runtime commands, setup messages, and bootstrap or dependency failure reports use HMAC-SHA-256 signatures over the exact
  request body.
- ✅ Unknown sessions, missing or invalid signatures, cross-session credentials, expired credentials, and replayed setup
  requests are rejected without mutating the intended session.
- ✅ The IDE boundary enforces POST requests, JSON content types, a 64 KiB body limit, supported message types, and validated
  setup and failure schemas before state mutation.
- ✅ Removed authentication-key byte arrays are overwritten when sessions and command requests complete.
- ✅ Focused Kotlin HTTP tests, the Python protocol suite, compilation, the full JVM suite, Plugin Verifier, and a manual
  startup/failure smoke test pass.

HMAC authenticates possession of the per-launch key and detects request-body modification, but localhost HTTP remains
unencrypted. It does not provide confidentiality against another local process that can observe loopback traffic or inspect
the Blender process environment. The gate also does not resolve the separate multi-session targeting limitation. The
remaining release blockers and high-priority issues below are still open.

Current verification results:

| Check                                         | Result                                                |
|-----------------------------------------------|-------------------------------------------------------|
| Kotlin compilation                            | Passed                                                |
| Authenticated editor-server integration tests | Passed: 36 of 36                                      |
| Python tests                                  | Passed: 24 plus 2 subtests                            |
| Full JVM tests                                | Passed: 222 of 222                                    |
| Plugin Verifier                               | Compatible: 2 of 2 targets                            |
| Manual authentication smoke                   | Passed: startup and induced dependency failure report |

The `BlenderProjectGeneratorTest` failures are resolved by restricting Gradle test workers to the plugin under test and its
non-optional dependencies. The IntelliJ Platform then excludes unrelated bundled plugins such as Vue.js from the test
environment. The full suite passes without the previous stale `Stubs` index shutdown report.

### New implementation assessment

| Implementation                       | Status                                 | Verified behavior                                                                                                                                                                                                                  | Remaining shortcomings                                                                                                                                                                                       |
|--------------------------------------|----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Runtime HMAC authentication          | ✅ Complete for the local-runtime gate | Per-launch keys, exact-body HMAC-SHA-256 for every message type, authentication-specific responses, replay and cross-session rejection, key cleanup, bounded request handling, schema validation, and bidirectional protocol tests | Localhost HTTP remains unencrypted, so HMAC provides authenticity and integrity but not confidentiality; runtime actions still target the latest active session                                              |
| Starter Python template              | ✅ Complete for syntax defect          | Minimal and example-code branches compile in Python regression tests                                                                                                                                                               | Template values are still substituted without Python-aware escaping                                                                                                                                          |
| Ruff configuration                   | ⚠️ Partial                             | Ruff discovers `.ruff.toml`; Python 3.12, 120-character lines, selected lint rules, formatting style, and vendored `get-pip.py` exclusion are explicit                                                                             | Only focused Ruff validation is clean; the complete first-party Python tree still requires lint and format cleanup; `fix = true` means ordinary `ruff check` mutates fixable files unless `--no-fix` is used |
| Consolidated `BlenderLauncher.start` | ⚠️ Partial                             | Explicit and configured fallback paths share one launch path; macOS `.app` resolution uses the final path; environment precedence remains global → project → request                                                               | Covered indirectly by process and argument tests, but there is no direct service-level test for fallback path resolution, macOS bundle selection, or environment precedence                                  |

## Release Blockers — ✅ Complete

### 1. Local runtime authentication — ✅ Complete

All supported localhost message types now use the same authenticated request pipeline. This closes the V1 authentication gate
for command execution, setup, and early failure reporting.

Remediation status:

- [x] Generate a cryptographically random secret per launch.
- [x] Pass it to Blender through the process environment.
- [x] Sign regular IDE-to-Blender commands.
- [x] Sign Blender-to-IDE setup requests.
- [x] Reject unknown identifiers, missing or invalid signatures, cross-session credentials, and setup replays.
- [x] Add negative tests for missing, wrong, expired, replayed, and cross-session credentials.
- [x] Sign bootstrap and dependency failure reports.
- [x] Return authentication-specific HTTP 401 responses from the IDE server.
- [x] Validate request content type and command/setup schemas.
- [x] Add request-size limits before reading complete request bodies.

Remaining limitations:

- HMAC provides authentication and integrity, not confidentiality. Requests still travel over unencrypted localhost HTTP.
- A sufficiently privileged local process may inspect process environment values or observe loopback traffic.
- Runtime actions still target the most recently registered session when multiple Blender sessions are active.

### 2. Generated starter Python syntax — ✅ Complete

`src/main/resources/fileTemplates/internal/NewProjectMainScript.ft` now renders an indented registration call:

```python
if __name__ == "__main__":
    register()
```

Regression tests compile both the minimal and example-code template branches. This closes the indentation defect only; the
separate format-specific escaping issue remains open.

### 3. Plugin Verifier fails for every declared target IDE — ✅ Complete

`verifyPlugin` reports both declared targets as compatible:

- PyCharm `261.27258.30`
- PyCharm `262.9437.71`

The failure was caused by declaring the legacy `com.intellij.modules.python` compatibility module. Plugin Verifier could not
reliably resolve that nested module alias from the transformed PyCharm distribution, so the owning Python plugin and its
`com.jetbrains.python` classes were absent from verification.

The plugin now declares `PythonCore` directly in `plugin.xml`, matching the bundled plugin that owns every Python API used by
the implementation. The Gradle build classpath likewise uses only `bundledPlugin("PythonCore")`; the unused Professional
`Pythonid` dependency was removed. Verification completes successfully without missing dependencies or unresolved classes.

Residual warnings do not fail verification: PyCharm 2026.1 reports three experimental `XDebugSessionBuilder` usages and two
experimental `MessageError` usages, while PyCharm 2026.2 reports only the two `MessageError` usages. These narrowly isolated
APIs still require compatibility acceptance and runtime smoke testing, but both configured targets are verifier-compatible.

### 4. JVM test suite is red — ✅ Complete

Current result: **195 tests passed**.

The two failures in `BlenderProjectGeneratorTest` originated from an unrelated Vue LSP service loaded from the Gradle test
sandbox. Vue's bundled-package lookup rejected the transformed PyCharm plugin-cache layout while the fixture deleted its VFS
content during teardown. That exception interrupted normal cleanup and produced the secondary stale `Stubs` index report.

Gradle `Test` workers now set `idea.load.plugins.id` to this plugin's ID. IntelliJ automatically enables its non-optional
Python and PyCharm dependencies while leaving unrelated bundled plugins disabled. Both focused generator tests and the full
JVM suite pass, and shutdown no longer reports stale generated-project index entries.

## High-Priority Issues — ❌ Open

### Downloaded Blender executables are not authenticated — ❌ Open

`src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/InstallBlender.kt:205` downloads and extracts executable
distributions without checking an official checksum or signature.

A compromised download, cache, redirect path, or upstream server could lead to execution of a modified Blender binary. The
changelog already acknowledges this.

Before V1:

- Require HTTPS and restrict downloads to expected Blender domains.
- Verify an official SHA-256 checksum before extraction.
- Delete cached archives that fail verification.
- Store verification metadata alongside cached artifacts.
- Test mismatch, missing-checksum, redirect, truncated-download, and stale-cache cases.

### Template variables are inserted without format-specific escaping — ❌ Open

`src/main/kotlin/com/sakurasedaia/blenderdevelopment/util/PluginResources.kt:43` inserts wizard values directly into TOML and
Python templates.

Examples include:

- Description, author, website, and permission strings in
  `src/main/resources/fileTemplates/internal/BlenderManifest.ft:4`.
- Name and license in `src/main/resources/fileTemplates/internal/Pyproject.ft:1`.
- Author and description in Python string literals.

Quotes, backslashes, newlines, or TOML control characters can produce invalid files or inject additional values. Tags are
manually quoted without escaping at
`src/main/kotlin/com/sakurasedaia/blenderdevelopment/wizard/BlenderNewProjectGenerator.kt:203`.

Use TOML/Python-aware escaping or structured serialization and test hostile-but-valid user input.

### Several visible settings do nothing — ❌ Open

These settings are persisted and exposed in Settings but have no production consumer:

- `clearDownloadsAfterInstall`
- `downloadCacheMaxSize`
- `bpyApiInstallPath`
- Configurable `logPath`

Their definitions are in `src/main/kotlin/com/sakurasedaia/blenderdevelopment/state/PluginConfig.kt:76`.
`src/main/kotlin/com/sakurasedaia/blenderdevelopment/logging/PluginLogger.kt:50` uses a fixed log directory instead of the
configured path.

This creates false expectations around disk cleanup, cache limits, stub locations, and logging. Either implement each setting
before V1 or remove or hide it until supported.

### Runtime command actions use the latest session implicitly — ❌ Open

`src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderRuntimeCommandService.kt:103` sends commands to whichever
active session was registered last.

With multiple Blender runs or projects, reload, run, or stop may affect the wrong process. V1 should associate commands with
the relevant execution session or present a session chooser.

### Stub replacement is not transactional — ❌ Open

`src/main/kotlin/com/sakurasedaia/blenderdevelopment/stubs/BlenderStubInstallationService.kt:142` uninstalls the old stub
package before installing the new one. If the new installation fails, the working old package is gone.

Prefer installing the new requirement first when pip semantics allow it, or restore the previous requirement after a failed
replacement.

### Runtime server payload validation — ✅ Complete

The server now rejects:

- Invalid or out-of-range ports.
- Empty scripts folders.
- Arbitrary path mappings.
- Unknown message types.
- Bodies larger than 64 KiB, including chunked requests.

Validation occurs before setup or failure payloads can mutate session state. Unknown setup fields remain accepted for forward
compatibility after all required fields have passed validation.

## Medium-Priority Stability and Maintainability Issues

- `PluginConfig.kt:146` mutates shared state from UI and background coroutine paths without a common synchronization
  mechanism. Concurrent updates can overwrite one another.
- `PluginConfig.getBlenderUpdateCheck()` returns the mutable internal persisted object. A caller can mutate it without
  validation or publishing a new `StateFlow` snapshot.
- `ExternalProcessBuilder.kt:96` waits indefinitely for the output-reading future after process destruction. A subprocess
  descendant retaining stdout could leave the worker blocked.
- Process diagnostic strings include every argument at `ExternalProcessBuilder.kt:300`. Blender's command catalog contains
  `--access-token`, so credentials can be written to logs. Introduce redaction for secret-bearing arguments and environment
  variables.
- The Python runtime logs full outbound payloads at `communication.py:178`, including paths and future authentication data
  unless redacted.
- ✅ The unused `BlenderEditorServerService.findSetupPayload`, `findActiveSessionPayload`, and `getActiveSessionPayloads`
  methods were removed, eliminating the query-driven TTL refresh behavior.
- Background executor tasks in the runtime command and debug services are not directly tied to project disposal. Prefer
  project-owned coroutine scopes or expiration conditions.
- ✅ Kotlin and Gradle Kotlin files are formatted consistently by Spotless with ktfmt, using two-space block indentation and
  a 140-column limit. `spotlessCheck` provides a repeatable repository formatting gate.
- The root Python project now requires `>=3.11.7`, matching the python version bundled with Blender 4.2

## Redundant and Dead Code

Confirmed unused or placeholder declarations include:

- Both asynchronous `ExternalProcessBuilder.launchAndCaptureOutputAsync` overloads.
- ✅ ~~`BlenderEditorServerService.findSetupPayload`.~~ Removed.
- ✅ ~~`BlenderEditorServerService.findActiveSessionPayload`.~~ Removed.
- ✅ ~~`BlenderEditorServerService.getActiveSessionPayloads`.~~ Removed.
- `BlenderInstallationScanner.logNoInstallsSummary`.
- `ScrapeBlenderVersionLists.getAvailableVersions`.
- `ScriptDirectoriesTable.getDirectories`.
- `PluginResources.importResource`.
- `BlenderVersion.pyMajorMinor`, `BlenderVersion.blFallback`, and `BlenderVersion.pyFallback`.
- The empty `InstallBlender.updateVersion()` placeholder.

Some declarations could support future features, but speculative code increases the V1 maintenance surface. Remove it or
create a concrete post-V1 issue rather than leaving unused execution paths in production.

Other redundancy:

- Repeated `valOrEmpty()` helpers in `BlenderNewProjectGenerator`.
- `communication.py` initializes `last_exception` redundantly.
- `load_addons.py` repeats a loop condition inside the loop.
- Several services repeat similar relative/absolute path normalization logic.

## TODOs and Acknowledged Incomplete Work

Production TODOs:

- Cache-loading startup step in `ProjectConfigStartupLoader`.
- In-place Blender updates in `InstallBlender.updateVersion`.
- Log-file location behavior in `PluginConfig`.
- Debugpy exception classification in `communication.py`.

The changelog also acknowledges:

- Missing checksum verification.
- Incomplete Python and stub update workflows.
- Run and Debug stabilization.
- Incomplete platform-specific and live-Blender test coverage.

These should be converted into an explicit V1 gate list. At minimum, authentication, generated-project validity, verifier
success, test-suite success, and artifact verification should be mandatory.

## Code-Quality Validation

| Check                                         | Status | Current result                                                                                  |
|-----------------------------------------------|--------|-------------------------------------------------------------------------------------------------|
| Kotlin compilation                            | ✅     | Passed                                                                                          |
| Gradle project configuration verification     | ✅     | Passed                                                                                          |
| Focused launcher/process tests                | ✅     | Passed                                                                                          |
| Authenticated editor-server integration tests | ✅     | Passed: 36 of 36                                                                                |
| Python tests                                  | ✅     | Passed: 24 plus 2 subtests                                                                      |
| JVM tests                                     | ✅     | Passed: 222 of 222 across 37 suites                                                             |
| Plugin Verifier                               | ✅     | Compatible with both declared PyCharm targets; experimental API warnings remain                 |
| Manual authentication smoke                   | ✅     | Phase-one startup and an intentionally induced, authenticated dependency-failure report passed  |
| Dependency lock validation                    | ✅     | Passed in the original audit; not rerun during this authentication-focused reverification       |
| Ruff configuration discovery                  | ✅     | `.ruff.toml` is discovered with the intended settings and vendored-file exclusion               |
| Full first-party Ruff lint/format             | ⚠️     | Configuration is corrected, but repository-wide cleanup and a clean full run remain outstanding |

The Ruff configuration now excludes vendored `src/main/python/external/get-pip.py`. Remaining first-party findings should be
fixed before release. Because `.ruff.toml` enables `fix = true`, release validation should use `ruff check --no-fix` when the
goal is a read-only audit and should run `ruff format --check` separately.

## Recommended V1 Gate Order

1. ✅ Authenticate both localhost protocols and strictly reject unknown sessions — commands, setup, early failure reports,
   HTTP boundary validation, and session-state invariants are complete.
2. ⚠️ Fix and compile-test every generated Python and TOML artifact — starter Python syntax is covered; escaping and other
   generated artifacts remain open.
3. ❌ Add checksum verification for downloaded Blender distributions.
4. ✅ Resolve Plugin Verifier dependency configuration and verify both target IDEs.
5. ✅ Isolate JVM tests from unrelated bundled plugins and verify the full suite.
6. ❌ Implement or remove nonfunctional settings.
7. ❌ Add real Run and Debug smoke tests on Windows, macOS, and Linux.
8. ⚠️ Clean first-party Ruff and Kotlin formatting findings — Ruff configuration is complete; full cleanup is not.
9. ⚠️ Remove or formally defer dead and TODO-only production code — three editor-server query methods were removed; the
   remaining inventory is open.
10. ❌ Run `clean test verifyPlugin buildPlugin` from an uncontaminated release environment.

## Audit Scope

This was a read-only source and validation audit covering Kotlin production and test code, the bundled Python runtime,
templates, Gradle/plugin metadata, release tooling, changelog material, static-analysis reports, and local build validation.
The protected `.env/` directory was excluded and was not inspected.
