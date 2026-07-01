# Agents Guidelines (Kotlin + IntelliJ Platform)

This document defines practical coding guidance for AI/code agents working in this repository.

## Source References Reviewed

- `intellij-community/AGENTS.md`
- `intellij-community/CONTRIBUTING.md`
- `intellij-community/.agents/skills/code-style/SKILL.md`
- `intellij-community/.agents/skills/testing/SKILL.md`
- `intellij-community/.agents/skills/writing-tests/SKILL.md`
- `intellij-community/.agents/skills/module-dependencies/SKILL.md`

## Core Rules

- Use **Kotlin** for new code. Only edit Java when touching existing Java files.
- Prefer IntelliJ Platform APIs and existing project services/patterns over custom infrastructure.
- Keep changes scoped to the requested behavior; avoid unrelated refactors.
- Put user-visible text in `messages/MessageBundle.properties` (localizable strings), not inline literals.

## Kotlin Style

- Use idiomatic Kotlin (null safety, data classes where appropriate, extension functions when useful).
- Keep functions focused and small; extract helpers for repeated logic.
- Favor explicit naming over comments; add comments only for non-obvious behavior.
- Follow existing file/style conventions in this repository.

## IntelliJ Platform SDK Guidance

- Register services/extensions in `plugin.xml` when needed and keep IDs stable.
- Use `@Service` + `project.service()` / `ApplicationManager.getApplication().getService()` for service access.
- Avoid blocking the EDT. Long-running I/O/process calls should run in background/coroutines.
- Use `project.messageBus` listeners with proper lifecycle/disposal.
- Use `NotificationModal` + `PluginLogger` for user feedback and diagnostics.

## Run Config / Process Integration

- Keep run/debug lifecycle consistent: start, stop, and termination callbacks must propagate to `ProcessHandler`.
- When adding launch behavior, ensure both normal Run and Debug paths are considered explicitly.
- Reuse `ExternalProcessUtil` for external process handling unless there is a clear reason not to.

## Configuration Rules

- Project-scoped runtime behavior belongs in `BlenderProjectConfig`.
- UI editors (Run Configuration, tool window, wizard) should read/write shared project state consistently.
- Preserve backward compatibility for persisted run configuration fields when migrating state.

## Testing and Validation

- After code changes, run targeted validation at minimum:
  - `./gradlew compileKotlin --no-daemon`
- Run/add tests proportional to risk and changed behavior.
- If tests are skipped, call that out in the final report.

## Dependency / Build Metadata

- Do not edit generated artifacts unnecessarily.
- Keep Gradle and plugin metadata changes minimal and intentional.
- If module/build metadata is changed, verify the project still compiles cleanly.

