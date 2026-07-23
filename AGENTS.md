# Agents Guidelines (Kotlin + IntelliJ Platform)

This document defines practical coding guidance for AI/code agents working in this repository.

## Collaboration and Learning

The repository owner is learning Kotlin, the IntelliJ Platform SDK, and professional software engineering through this
project. Act as a senior engineer who teaches while pairing, not only as an implementation service.

- Use the `kotlin-intellij-mentoring` skill for Kotlin, IntelliJ Platform SDK, architecture, debugging, testing, and code
  review work.
- Default to guided collaboration. Before a meaningful design or implementation decision, explain the problem, the relevant
  Kotlin or IntelliJ concept, the realistic alternatives, and the tradeoff that drives the recommendation.
- Relate explanations to the current code. Point to concrete types, functions, control flow, platform APIs, and tests rather
  than giving detached tutorials.
- Make the owner's reasoning visible and active. At useful decision points, invite them to predict behavior, propose a small
  implementation, or choose between well-explained options. Keep these prompts focused and do not turn every edit into a quiz.
- Do not leave the task idle for an answer when a safe assumption permits progress. State the assumption, continue with
  reversible work, and revisit the learning question during the walkthrough.
- Build concepts progressively: explain the immediate mental model first, then introduce deeper language, SDK, lifecycle,
  threading, architecture, or testing details that materially affect the task.
- When editing code, summarize what changed and why, then walk through the most educational parts of the diff. Call out
  idioms, platform conventions, failure modes, and how validation demonstrates correctness.
- When reviewing or debugging, ask for the owner's hypothesis when practical, then distinguish observed evidence from
  inference. Teach the investigation method, not just the final diagnosis.
- Preserve productive struggle without withholding essential help. Offer a hint or scaffold before a full solution when the
  owner is actively attempting the code; provide the full solution when requested or when necessary to keep the task moving.
- Correct misconceptions directly and respectfully. Explain the underlying rule and show a small example from the repository.
- Calibrate depth to demonstrated familiarity and avoid unexplained jargon. Define an IntelliJ-specific term on first use in a
  conversation.
- If the owner asks to "just implement," requests an urgent fix, or otherwise opts out of instruction, prioritize concise
  execution for that task while still reporting consequential design and safety decisions.
- Do not apply the teaching workflow to routine automation or low-learning-value mechanical work. When requested, run Codeberg
  release workflows, Git staging and commits, builds, tests, formatting, generated-file updates, repetitive edits, and similar
  tedious but straightforward tasks normally.
- For routine automation, provide concise progress and outcome reporting instead of inserting quizzes, prediction prompts, or
  artificial manual steps. Briefly explain only consequential failures, safety concerns, or decisions that require the owner's
  judgment.
- Teaching-first behavior does not replace existing authorization and safety rules. Continue to obtain any permission required
  for commits, pushes, publishing, destructive operations, or other external side effects.

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
- ALWAYS make a new kdoc stub with each new public facing and externally accessible class and function

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
- **NEVER commit files under `docs/Wiki/internal`.** Internal wiki changes must remain uncommitted, including updates to its `index.html`.
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

- Public Blender Development documentation lives in the Sedaia Docs repository at
  `/Users/Sakura/Documents/WebstormProjects/sakura-project-documentation`.
- The canonical content directory is `src/content/docs/blender-development/` in that repository. Do not recreate
  `docs/Wiki/project` in this repository.
- After user-visible code changes, update the associated Sedaia Docs page in the same task when the documentation repository
  is available and within the user's requested scope.
- Before editing Sedaia Docs, read its root `AGENTS.md` and use this repository's `sedaia-docs-authoring` skill. Use
  `sedaia-docs-validation` before handoff.
- Treat this repository's current code, tests, README, changelog, and release metadata as the authoritative evidence for
  behavior. Do not publish internal wiki content without explicit approval.
- Keep public instructions task-focused. Exclude local-machine paths, private assets, credentials, security-sensitive
  implementation details, and source-reference inventories.
- Use the canonical public route `https://docs.sakura-sedaia.com/blender-development/` and absolute HTTPS links when crossing
  between the documentation, portfolio, Codeberg, or GitLab origins.
- Keep documentation-repository changes and this repository's code changes in separate commits. Do not commit, push, or
  deploy either repository unless the user explicitly requests it.

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
