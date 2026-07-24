---
name: edit-changelog
description: Edit, curate, or review CHANGELOG.md and release notes using Keep a Changelog conventions. Use when Codex needs to update an Unreleased section, prepare a version section, derive notable changes since the latest release, split overloaded changelog bullets, remove commit-history noise, or verify that changelog entries describe only the final release delta.
---

# Edit Changelog

Produce a human-readable account of the notable net changes between the last released version and the current release state. Treat commits as evidence, not as changelog entries.

## Establish the Release Baseline

1. Read the changelog structure and repository release conventions before editing.
2. Identify the target release and its predecessor from the changelog and matching release tags. Prefer the project's explicit release metadata over lexical or semantic sorting of every tag.
3. Inspect the complete range from the predecessor release through `HEAD`, plus relevant staged and unstaged changes when updating `Unreleased`.
4. Compare the predecessor's behavior and files with the final current state. Use commit history to understand intent and evolution, but record only the resulting delta.
5. Ask for clarification only when the release boundary cannot be established safely.

Do not log an intermediate change that was fixed, reverted, replaced, or removed before release. For example, if one unreleased commit introduces a broken layout and a later unreleased commit fixes it, describe only the final layout change; do not add a `Fixed` entry for the temporary defect.

## Curate Notable Changes

Write for users and contributors rather than mirroring commit subjects or file diffs.

- Include every notable final change, especially deprecations, removals, security changes, breaking behavior, new capabilities, and meaningful fixes.
- Omit implementation churn, merge activity, formatting, test-only maintenance, and transient defects unless they materially affect users or contributors in the final release.
- Describe observable outcomes. Mention internal types, files, or architecture only when the intended audience needs that information.
- Do not pad entries with lists of behavior that remains unchanged. Include a compatibility assurance only when it resolves a realistic upgrade concern.
- Do not invent motivation, impact, or compatibility claims that the diff, tests, documentation, or user request cannot support.

## Keep Entries Atomic

Use one bullet for one independently meaningful change. Sharing a module, screen, commit, or implementation does not make separate outcomes one changelog entry.

Split a bullet when it contains separate actions that a reader may care about independently, especially when joined by “and,” a long comma-separated list, or a second sentence. Keep closely coupled cause-and-result wording together when splitting it would obscure the change.

Prefer:

```markdown
- Compacted the Blender installation selector into a simpler, more readable layout.
- Consolidated the symlink name, source folder, and launch controls under `Run and Debug`.
```

Avoid:

```markdown
- Compacted Blender installation selection into a full-width combo box with a right-aligned, icon-only refresh action, and
  consolidated the add-on name, source folder, and launch controls under Run and Debug. Installation discovery, cached
  selection, custom executable paths, and scan behavior remain unchanged.
```

Do not overcorrect into bullets for individual code edits. Group changes only when they express one coherent user-facing outcome.

## Apply Keep a Changelog

Maintain reverse chronological version order and an `Unreleased` section at the top. Use ISO dates (`YYYY-MM-DD`) for released versions.

Classify each entry under the standard headings:

- `Added` for new features.
- `Changed` for changes to existing functionality.
- `Deprecated` for features that will be removed.
- `Removed` for features removed now.
- `Fixed` for bug fixes present relative to the previous release.
- `Security` for vulnerability-related changes.

Preserve established project formatting unless it conflicts with the standard. Do not add nonstandard categories casually. Follow the repository's existing policy for empty headings; when no policy exists, omit empty headings rather than writing `None`.

When cutting a release, move the curated entries from `Unreleased` into a linkable version heading with its release date, retain an empty `Unreleased` heading, and update comparison links if the changelog uses them.

## Edit and Verify

Before writing, reconcile existing draft entries with the actual release delta. Rewrite, split, move, or remove stale bullets rather than only appending new ones.

After editing:

1. Confirm every bullet is true of the final state relative to the predecessor release.
2. Confirm temporary same-cycle defects and reverted changes are absent.
3. Confirm unrelated outcomes have separate bullets and duplicated outcomes are merged.
4. Confirm every entry uses the correct standard category.
5. Confirm headings, ordering, dates, links, wrapping, punctuation, and inline-code formatting match the file.
6. Review the changelog diff separately from the implementation diff.
