# IntelliJ API Compatibility Remediation Plan

> [!WARNING]
> This plan is now stale, please refer to [intellij-marketplace-api-warning-remediation-plan.md](intellij-marketplace-api-warning-remediation-plan.md) for the updated plan.

Scope: remove or isolate IntelliJ/PyCharm internal API dependencies reported for PyCharm 2026.1 and 2026.2, then resolve the associated binary incompatibilities. Deprecated and experimental APIs are tracked after the internal-API work. Unrelated Qodana cleanup and feature changes are out of scope.

## Evidence and constraints

- PyCharm 2026.1 (`PY-261.27258.30`) reports one binary incompatibility, 22 internal API usages, five deprecated API usages, and 21 experimental API usages.
- PyCharm 2026.2 (`PY-262.9437.71`) reports three binary incompatibilities, 21 internal API usages, five deprecated API usages, and 21 experimental API usages.
- The 2026.1 binary break is `PyDebugRunner.createConsoleCommunication(...)` from `BlenderDebugAttachService`.
- The 2026.2 report adds broken calls to `PythonPackageManager.installPackageDetached(...)` and `PythonPackageManager.uninstallPackage(...)` from `PyCharmBlenderPythonPackageInstaller`.
- The local `intellij-community` checkout is the primary source reference. An annotation such as `@ApiStatus.Internal` means source availability is not a compatibility promise to third-party plugins.
- Each migration must retain the oldest supported PyCharm build (`sinceBuild = 261`) unless a deliberate compatibility-range change is approved.

## Current verification status

- The latest verifier run reports zero internal API usages and none of the original unresolved-method binary incompatibilities on either target.
- PyCharm 2026.1 currently reports four deprecated `ToolWindowFactory` bridge usages and nine experimental usages: six generated `ToolWindowFactory` bridges plus three `XDebugSessionBuilder` references.
- PyCharm 2026.2 currently reports four deprecated `ToolWindowFactory` bridge usages, one scheduled-for-removal `SimpleListCellRenderer.create(...)` usage, and six experimental `ToolWindowFactory` bridge usages.
- Plugin Verifier still exits unsuccessfully because `com.intellij.modules.python` is not resolved from the supplied IDE layout. The resulting unresolved `com.jetbrains.python` wizard classes are not evidence that those classes were removed until dependency resolution is corrected.
- Compatibility-focused tests pass. The full suite currently has two failures in `BlenderProjectGeneratorTest` during `VueLspServerLoader` initialization, followed by a stale-index assertion.

## Recommended replacements

| Current dependency                                                                                | Problem                                                                                                             | Recommended direction                                                                                                                                                                                             | Validation gate                                                                                                            |
|---------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `ComponentManagerEx.getCoroutineScope()`                                                          | Internal component-container API                                                                                    | Give `BlenderToolWindowContent` an explicitly owned `CoroutineScope` with a `SupervisorJob`, meaningful coroutine name, and cancellation in `dispose()`                                                           | Controller jobs are cancelled when tool-window content is disposed; no `ComponentManagerEx` reference remains              |
| `PluginManagerCore.getPlugin(...)`                                                                | Internal plugin-manager implementation                                                                              | Read the plugin version from build-generated resource metadata or the packaged JAR manifest, retaining `"dev"` for source/test execution                                                                          | Packaged archive fingerprint contains the configured Gradle version; tests/source runs retain a deterministic fallback     |
| `PyDebuggerSettings` getters/setters                                                              | Internal, application-global Python debugger state                                                                  | Stop mutating global debugger preferences. Carry `justMyCode` as session configuration where the selected debugger protocol supports it; otherwise document the protocol-specific limitation                      | Starting and terminating a Blender debug session never changes the user's global Python debugger settings                  |
| `PyDebugRunner.createConsoleCommunication(...)`                                                   | Internal and removed/changed across builds, causing `NoSuchMethodError`                                             | Remove the call if regression testing confirms the Blender execution console is not a Python debug console. Prefer the supported DAP run-configuration path for debugpy; keep the pydev path minimal and isolated | Attach, breakpoints, stepping, console behavior, termination, and path mapping work on 2026.1 and 2026.2                   |
| `PythonPackageManager`, `PythonPackageInstallRequest`, and `PythonRepositoryPackageSpecification` | Experimental manager with internal construction and mutation APIs; Kotlin default-argument bytecode broke in 2026.2 | Preserve `BlenderPythonPackageInstaller` as the project boundary, but implement it through the selected SDK interpreter and `python -m pip` using `GeneralCommandLine`, which avoids direct EEL linkage           | Install, already-installed, uninstall, missing-package, non-zero exit, cancellation, and interpreter-path cases are tested |
| `PyPackageName` normalization                                                                     | Internal value class and companion methods                                                                          | Use a small project-owned PEP 503 package-name normalizer (`[-_.]+` to `-`, lowercase) or compare installed distributions through `importlib.metadata`                                                            | Equivalent names such as `fake-bpy-module`, `fake_bpy_module`, and `fake.bpy.module` compare equal                         |
| `MessageError`, `PyError`, and `PyResult` exposed by package installation                         | Experimental Python error model leaks beyond the adapter                                                            | Introduce a project-owned sealed result/error type containing a localized user message and diagnostic cause/output                                                                                                | Callers handle success, process failure, cancellation, and unavailable interpreter without Python-internal error types     |

## Work plan

- [x] Capture the 2026.1 and 2026.2 verifier findings and group duplicate bytecode reports by source dependency.
- [x] Locate every reported internal API dependency in the plugin and inspect the corresponding local IntelliJ/Python source annotations.
- [x] Record a preferred supported boundary and a validation gate for each internal API group.
- [x] Add focused installer tests for command construction, failure mapping, missing interpreters, and service orchestration.
- [x] Replace `ComponentManagerEx` scope access with a content-owned structured coroutine scope.
- [x] Replace `PluginManagerCore` version lookup with packaged build metadata and verify the generated version value.
- [x] Introduce a project-owned package operation result; direct pip uninstall removes the need for package-name normalization.
- [x] Replace PyCharm package-management internals with SDK-interpreter Platform process execution, including cancellation and actionable diagnostics.
- [x] Remove global `PyDebuggerSettings` mutation; apply `justMyCode` to DAP configuration when its runtime type exposes a session setter.
- [x] Remove `PyDebugRunner.createConsoleCommunication(...)`, which is a no-op for Blender's non-Python execution console in current platform source.
- [x] Review the remaining experimental API inventory and separate generated `ToolWindowFactory` bridges from explicit debugger usage.
- [ ] Investigate Kotlin-generated deprecated and experimental `ToolWindowFactory` compatibility bridges; the source class has no explicit overrides to replace.
- [ ] Decide whether the 2026.1-only experimental `XDebugSessionBuilder` usage is preferable to the deprecated `XDebuggerManager.startSession(...)` API across the full support range.
- [ ] Replace the scheduled-for-removal `SimpleListCellRenderer.create(...)` overload in `BlenderExtensionBuildSettingsEditor`.
- [ ] Add a package-process cancellation test that proves cancellation terminates the launched interpreter process.
- [ ] Add a tool-window disposal test that proves disposing `BlenderToolWindowContent` cancels controller collection jobs.
- [x] Run Kotlin compilation and the targeted compatibility unit tests.
- [x] Run Plugin Verifier against 2026.1 and 2026.2: the original binary incompatibilities and all internal API usages are gone; dependency resolution for `com.intellij.modules.python` still prevents a clean verifier exit.
- [ ] Correct the Plugin Verifier dependency inputs so `com.intellij.modules.python` and the referenced Python wizard classes resolve on both target IDEs.
- [ ] Investigate and repair the two `BlenderProjectGeneratorTest` failures caused by `VueLspServerLoader` initialization and stale index state.
- [ ] Manually verify Blender launch, DAP attach, pydev fallback, path mappings, package install/uninstall, and tool-window disposal in the IDE sandbox.
- [ ] Manually verify package install/uninstall with local, WSL, and Docker-backed SDKs before treating `GeneralCommandLine` environment selection as complete.
- [x] Confirm that no public Blender Developer Docs update is required for this implementation-only compatibility migration.
- [ ] Commit Changes.

## Risks and decision points

- The DAP attach configuration is currently accessed reflectively, which indicates that its concrete PyCharm type may not be a supported compile-time API. Reflection avoids linkage failure but does not create a compatibility guarantee; failure must remain detectable and fall back cleanly.
- Removing `createConsoleCommunication(...)` is based on current source behavior: it returns without action unless the execution console is a Python debug console. This is an inference that must be checked on both target IDE lines.
- Direct `pip` execution is a stable Python boundary, but SDK types may represent local, WSL, Docker, or other target environments. The implementation must use the repository's environment-aware execution abstraction rather than assuming a local executable path.
- `justMyCode` support differs between DAP and pydev. It is better to expose an explicit per-protocol limitation than to change application-global debugger settings for every Python session.
- Kotlin default arguments on external APIs generate calls to synthetic `$default` methods. Avoid default arguments at volatile integration boundaries even when the underlying API is temporarily retained.

## Source references

- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderDebugAttachService.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/stubs/BlenderPythonPackageInstaller.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/toolwindow/BlenderToolWindowContent.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/util/BlenderRuntimeResources.kt`
- `/intellij-community/python/src/com/jetbrains/python/debugger/PyDebugRunner.java`
- `/intellij-community/python/src/com/jetbrains/python/debugger/settings/PyDebuggerSettings.java`
- `/intellij-community/python/src/com/jetbrains/python/packaging/management/PythonPackageManager.kt`
- `/intellij-community/python/src/com/jetbrains/python/packaging/management/PythonPackageInstallRequest.kt`
- `/intellij-community/python/openapi/src/com/jetbrains/python/packaging/PyPackageName.kt`
- `/intellij-community/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowFactory.kt`

---
