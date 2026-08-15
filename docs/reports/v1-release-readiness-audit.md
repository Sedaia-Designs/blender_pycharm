# V1 Release-Readiness Audit

Audit date: 2026-08-10

## Outcome

**The project is not ready for a stable V1 release yet.**

The audit found four release blockers, several high-priority correctness and security gaps, misleading settings that are
persisted but never honored, and incomplete validation. The project compiles and most tests pass, but the full release
validation currently fails.

## Reverification — 2026-08-14

The release outcome remains unchanged. The current working implementation partially remediates the localhost authentication
blocker:

- Each launch receives a random 32-byte key through the Blender process environment.
- Blender setup requests and regular IDE-to-Blender runtime commands use HMAC-SHA-256 signatures over the exact request body.
- Unknown sessions, missing signatures, invalid signatures, cross-session credentials, and replayed setup requests are rejected.
- Kotlin HTTP integration tests and Python protocol tests cover the authenticated request path.

The authentication gate is not complete. Bootstrap and dependency failure reports are still unsigned, and the IDE server does
not yet enforce content type, request-size limits, or the complete setup schema. The remaining release blockers and
high-priority issues below are also still open. The generated starter Python indentation defect is fixed and covered for both
minimal and example-code template branches.

Current verification results:

| Check                                         | Result                       |
|-----------------------------------------------|------------------------------|
| Kotlin compilation                            | Passed                       |
| Authenticated editor-server integration tests | Passed: 10 of 10             |
| Python tests                                  | Passed: 18 of 18             |
| Full JVM tests                                | Failed: 193 passed, 2 failed |

The two JVM failures remain the `BlenderProjectGeneratorTest` Vue LSP/plugin-layout failures described below.

## Release Blockers

### 1. Local runtime command server is unauthenticated

`src/main/python/communication.py:114` accepts arbitrary POST requests from any local process. Supported commands can:

- Execute an arbitrary Python file through `src/main/python/operators/script_runner.py:23`.
- Reload add-ons.
- Quit Blender through `src/main/python/operators/stop_blender.py:9`.

Binding to `127.0.0.1` prevents direct network access, but it does not authenticate the IDE. Malware, another local
application, or a browser-originated localhost request could target the server.

The IDE-side server has the corresponding weakness: `BlenderEditorServerService.kt:208` warns about an unknown session
identifier but registers it anyway.

Required remediation:

- Generate a cryptographically random secret per launch.
- Pass it to Blender through the process environment.
- Require it on every IDE-to-Blender and Blender-to-IDE request.
- Reject unknown identifiers and invalid credentials with `401` or `403`.
- Validate request content type and command schema.
- Add request-size limits.
- Add negative tests for missing, wrong, expired, and cross-session credentials.

### 2. Generated starter Python file has invalid syntax

`src/main/resources/fileTemplates/internal/NewProjectMainScript.ft:64` renders:

```python
if __name__ == "__main__":
register()
```

`register()` is not indented, so every generated project using this template receives a `SyntaxError`.

Existing wizard tests verify file presence but never compile the generated Python source. Add a regression test equivalent
to the template compilation test already used for the runtime repository-sync template.

### 3. Plugin Verifier fails for every declared target IDE

`verifyPlugin` reports unresolved `com.jetbrains.python` classes against both:

- PyCharm `261.27258.30`
- PyCharm `262.9437.71`

Affected functionality includes the new-project wizard and Python project APIs. A verifier failure means runtime
`NoClassDefFoundError` remains possible.

The likely immediate problem is verifier dependency resolution rather than all referenced APIs actually being absent, but
this must be resolved before release. Confirm the correct bundled Python plugin dependency and verifier configuration, then
rerun both targets.

PyCharm 2026.1 also reports three experimental `XDebugSessionBuilder` usages in
`src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderDebugAttachService.kt:215`. Those usages are narrowly
isolated, but require explicit compatibility acceptance and runtime testing.

### 4. JVM test suite is red

Result: **183 tests, 2 failures**.

Both failures are in `BlenderProjectGeneratorTest` and originate from a Vue LSP/plugin-layout initialization error. Shutdown
also reports a stale `Stubs` index entry associated with the generated project.

This may be test-environment configuration rather than product behavior, but a stable V1 should not ship with a consistently
red test task. The tests need isolation from unrelated JavaScript/Vue plugin initialization, and their generated VFS/index
state needs proper cleanup.

## High-Priority Issues

### Downloaded Blender executables are not authenticated

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

### Template variables are inserted without format-specific escaping

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

### Several visible settings do nothing

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

### Runtime server accepts malformed session setup

`src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt:212` accepts:

- Invalid or out-of-range ports.
- Empty scripts folders.
- Arbitrary path mappings.
- Unknown message types, which still receive `200 OK`.
- Unbounded request bodies.

Validate the complete setup payload before storing it and return an error for unknown payload types.

### Runtime command actions use the latest session implicitly

`src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderRuntimeCommandService.kt:103` sends commands to whichever
active session was registered last.

With multiple Blender runs or projects, reload, run, or stop may affect the wrong process. V1 should associate commands with
the relevant execution session or present a session chooser.

### Stub replacement is not transactional

`src/main/kotlin/com/sakurasedaia/blenderdevelopment/stubs/BlenderStubInstallationService.kt:142` uninstalls the old stub
package before installing the new one. If the new installation fails, the working old package is gone.

Prefer installing the new requirement first when pip semantics allow it, or restore the previous requirement after a failed
replacement.

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
- `BlenderEditorServerService.getActiveSessionPayloads()` refreshes every session's TTL merely by listing sessions. Querying
  state should not change session lifetime.
- Background executor tasks in the runtime command and debug services are not directly tied to project disposal. Prefer
  project-owned coroutine scopes or expiration conditions.
- `PluginConfig` uses tabs and IntelliJ-style-inconsistent indentation throughout. Several Kotlin files use four-space
  formatting even though the repository standard is two spaces.
- The root Python project requires Python `>=3.14`, while Blender 4.2+ embeds older Python versions. This is acceptable for
  developer tooling only, but the distinction should be explicit so contributors do not mistake it for bundled-runtime
  compatibility.

## Redundant and Dead Code

Confirmed unused or placeholder declarations include:

- Both asynchronous `ExternalProcessBuilder.launchAndCaptureOutputAsync` overloads.
- `BlenderEditorServerService.findSetupPayload`.
- `BlenderEditorServerService.findActiveSessionPayload`.
- `BlenderEditorServerService.getActiveSessionPayloads`.
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

| Check | Result |
| --- | --- |
| Kotlin compilation | Passed |
| Gradle project configuration verification | Passed |
| JVM tests | Failed: 181 passed, 2 failed |
| Plugin Verifier | Failed for both target IDEs |
| Python tests | Passed: 8 of 8 |
| Dependency lock validation | Passed |
| Ruff | Failed: 34 findings |

Ruff findings include unsorted imports, wildcard imports, unused imports, excessive line length, trailing whitespace, and
redundant f-strings. Vendored `src/main/python/external/get-pip.py` should be excluded from linting rather than modified; the
remaining first-party findings should be fixed before release.

## Recommended V1 Gate Order

1. Authenticate both localhost protocols and strictly reject unknown sessions.
2. Fix and compile-test every generated Python and TOML artifact.
3. Add checksum verification for downloaded Blender distributions.
4. Resolve Plugin Verifier dependency configuration.
5. Fix or isolate the two failing JVM tests.
6. Implement or remove nonfunctional settings.
7. Add real Run and Debug smoke tests on Windows, macOS, and Linux.
8. Clean first-party Ruff and Kotlin formatting findings.
9. Remove or formally defer dead and TODO-only production code.
10. Run `clean test verifyPlugin buildPlugin` from an uncontaminated release environment.

## Audit Scope

This was a read-only source and validation audit covering Kotlin production and test code, the bundled Python runtime,
templates, Gradle/plugin metadata, release tooling, changelog material, static-analysis reports, and local build validation.
The protected `.env/` directory was excluded and was not inspected.
