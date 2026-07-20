# Agents Guidelines (Kotlin + IntelliJ Platform)

This document defines practical coding guidance for AI/code agents working in this repository.

## Local IntelliJ Community Reference

- Local clone path: `/Users/Sakura/Documents/IdeaProjects/intellij-community`
- Prefer referencing this local repository over the online `intellij-community` repository whenever possible.

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
- For internal-use documentation, prefer HTML documents over Markdown.

## Kotlin Style

- Use idiomatic Kotlin (null safety, data classes where appropriate, extension functions when useful).
- Keep functions focused and small; extract helpers for repeated logic.
- Favor explicit naming over comments; add comments only for non-obvious behavior.
- Follow existing file/style conventions in this repository.
- ALWAYS make a new kdoc stub with each new class and function

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
- When writing new features, add in test cases to check for edge case behaviors and ensure core functionality
- If tests are skipped, call that out in the final report.

## Dependency / Build Metadata

- Do not edit generated artifacts unnecessarily.
- Keep Gradle and plugin metadata changes minimal and intentional.
- If module/build metadata is changed, verify the project still compiles cleanly.

## Git Commit Guidelines

- Avoid committing partially complete changes.
- Avoid overly verbose commit messages.
- Commit subject lines must be 150 characters maximum.
- If a subclass is referenced (for example `PluginConfig.BlendInstallInfo`), include the parent class name when referring to that subclass.
- All commits must use standardized prefixing: `[Type -> module] Description`.
- Omit `-> module` when 3 or more modules are touched, using `[Type] Description` instead.
- **NEVER** perform `git push` or anything that affects the remote unless I explicitly give permission.
- If a module is deleted with a new module created in a new location, example below, treat it as a file move instead of a deletion and recreation
```diff
- /.../utils/ImageProcessor.kt
+ /.../image/ImageProcessor.kt
```

## Documentation

- Use `docs/Wiki/internal/index.html` as the launch page for internal documentation.
- Keep internal wiki styling centralized in `docs/Wiki/internal/wiki.css`; do not duplicate page-level style blocks unless there is a page-specific exception.
- Use `docs/Wiki/internal/wiki-page-template.html` as the baseline when creating new wiki pages.
- Every new internal wiki page should:
  - include `<link rel="stylesheet" href="./wiki.css" />` in `<head>`
  - include a top navigation link back to `index.html`
  - include a table of contents with anchored sections
  - include a “Source References” section with concrete file paths and/or upstream references
- Prefer concise, source-grounded technical documentation. Clearly separate facts, inferences, and implementation plans.
  - When referencing source files from any project, always use repo-relative source pathing (E.g. /Users/Sakura/Documents/IdeaProjects/intellij-community/platform/platform-impl/src/com/intellij/ui/dsl/builder/textFieldWithBrowseButton.kt -> /intellij-community/platform/platform-impl/src/com/intellij/ui/dsl/builder/textFieldWithBrowseButton.kt)
- Keep filenames kebab-case and descriptive (for example `intellij-run-configuration-system.html`).
- When a page is added or renamed in `docs/Wiki/internal`, update `docs/Wiki/internal/index.html` so the wiki remains navigable.
- Provide code snippets for examples on complex API's

## Project Documentation (External / Non-Internal)

- After code changes, update or add the associated documentation
- Project-facing documentation should live under `docs/Wiki/project`.
- Use `docs/Wiki/project/index.html` as the launch page for project documentation.
- Keep project wiki styling centralized in `docs/Wiki/project/wiki.css`; avoid per-page duplicated style blocks unless there is a true page-specific need.
- Use `docs/Wiki/project/wiki-page-template.html` as the baseline for new project documentation pages.
- Every new project documentation page should:
  - include `<link rel="stylesheet" href="./wiki.css" />` in `<head>`
  - include a top navigation link back to `index.html`
  - include a table of contents with anchored sections
  - include a “Source References” section
- Prefer concise, source-grounded technical writing and clearly separate facts, inferences, and plans.
- Use repo-relative source pathing for references (same rule as internal docs).
- Keep filenames kebab-case and descriptive.
- When a page is added or renamed in `docs/Wiki/project`, update `docs/Wiki/project/index.html` so navigation remains complete.
- Include code snippets for complex APIs, workflows, or integration points.

## Planning

- Write plans as checkbox task lists so completion can be tracked step by step.
- Start each plan with a short scope statement: what will be changed and what is explicitly out of scope.
- Break work into small, testable steps with clear completion criteria.
- Order steps by dependency (foundations first, integrations after) to reduce rework.
- Include explicit validation tasks (for example compile, targeted tests, and manual behavior checks).
- Include documentation update tasks when behavior, workflows, or configuration are changed.
- Track risks/unknowns early and add follow-up tasks to resolve them before final integration.
- Always include `Commit Changes` as the final plan item.
- Commit completed module work as soon as that module is done, even if the broader feature is still in progress.
  - Example: if a major feature plan includes a minor sub-feature, commit the minor feature once it is complete and awaiting integration.
