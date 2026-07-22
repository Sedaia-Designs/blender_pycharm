---
name: codeberg-release-publishing
description: Prepare and publish Blender Python Development snapshot releases to Codeberg and synchronize published releases to the Sakura portfolio data. Use when Codex is asked to prepare, build, validate, tag, or publish a plugin release; update CHANGELOG.md from commits since the latest release; produce the Gradle distribution ZIP; run and verify the repository's Codeberg release automation; or update the portfolio's Blender Development release index.
---

# Codeberg release publishing

## Preserve repository state

- Work from the repository root containing `build.gradle.kts`, `CHANGELOG.md`, and `scripts/release-codeberg.sh`.
- Read the root `AGENTS.md` and obey its commit, documentation, and remote-operation rules.
- Inspect `git status --short --branch` before editing. Preserve unrelated and pre-existing changes; never discard or absorb them into a release commit.
- Treat commits, tags, pushes, and Codeberg API calls as distinct release stages. Do not push, publish, replace an asset, or otherwise change Codeberg unless the user explicitly requested that remote action.
- Never print, persist, or commit `CODEBERG_TOKEN` or Keychain output.

## Establish the release range

1. Read the literal `version` in `build.gradle.kts`. Require a `-SNAPSHOT` version and map it to the changelog/tag spelling `X.Y.Z-Snapshot` and `vX.Y.Z-Snapshot`.
2. List reachable release tags with `git tag --merged HEAD --list 'v*-Snapshot' --sort=-version:refname`. Select the newest prior release tag, excluding the tag being prepared. Verify its commit with `git show --no-patch --format=fuller <tag>`.
3. If tags and version ordering disagree, or no trustworthy prior release exists, stop and ask the user for the baseline rather than guessing.
4. Review the complete committed range with `git log --reverse --stat <previous-tag>..HEAD`, `git diff --stat <previous-tag>..HEAD`, and focused diffs for behavior, tests, documentation, metadata, and release tooling. Also inspect `git diff` and `git diff --cached` for intended release work not yet committed. Do not derive notes from commit subjects alone.

## Update the changelog

1. Preserve existing manual edits in `CHANGELOG.md` and reconcile them with source evidence from the release range.
2. Add or update `## [X.Y.Z-Snapshot] - YYYY-MM-DD` at the top using the current date.
3. Include a concise release summary followed by all five headings required by the publisher: `### Added`, `### Changed`, `### Fixed`, `### Removed`, and `### Known Issues`.
4. Describe user-visible behavior, compatibility changes, migrations, removals, and known limitations. Do not list internal implementation noise or claim unverified fixes.
5. Write `- None.` under a required heading with no entries so the release script can validate a complete structure.
6. Check the rendered section and compare it back to the entire commit range before proceeding.

## Build and validate

1. Run targeted tests warranted by the release changes, including `bash scripts/tests/release-codeberg-test.sh` when release automation changed.
2. Run the release-grade build:

   ```bash
   ./gradlew compileKotlin test buildPlugin --no-daemon
   ```

3. Confirm the expected artifact exists at `build/distributions/BlenderPythonDevelopment-X.Y.Z-SNAPSHOT.zip` and report its SHA-256 from `shasum -a 256 <artifact>`.
4. Inspect `git diff --check`, the release diff, and `git status --short`. Do not continue past validation failures.

## Prepare the release commit and tag

Perform these steps only when the user asked to prepare or publish the release.

1. Stage only intended release files. Never stage unrelated pre-existing work.
2. Commit with the repository convention, normally `[Release] Prepare vX.Y.Z-Snapshot`.
3. Create an annotated `vX.Y.Z-Snapshot` tag at that release commit.
4. Run `scripts/release-codeberg.sh --check`. It requires a clean worktree, the expected local tag at `HEAD`, the changelog section, and the distribution ZIP.
5. If an existing local tag points elsewhere, stop and report it. Never move or delete a release tag without explicit approval.

## Publish to Codeberg

Perform this section only when the user explicitly asked to publish and authorized remote changes.

1. Verify the release commit and tag, then push the branch and exact tag to the intended Codeberg remote. Never infer remote authorization from a request to only prepare or build.
2. Run `scripts/release-codeberg.sh --skip-build` to reuse the artifact already validated from the tagged commit. The script checks the remote tag, creates or reuses the pre-release, uploads or verifies the named ZIP, and compares the downloaded asset SHA-256 with the local artifact.
3. If the script reports mismatched existing release metadata or an asset mismatch, stop. Do not delete or replace remote data without explicit approval.
4. Report the tag, release URL, artifact path, local and verified remote SHA-256, tests/builds run, and any skipped checks.

## Synchronize the portfolio

Perform this section after the Codeberg release and asset are successfully verified.

1. Open `/Users/Sakura/Documents/WebstormProjects/sakura-portfolio`, read its root `AGENTS.md`, and confirm its worktree state before editing.
2. Manually edit only `src/data/json/projects/addon-index.json`; the SolidStart UI populates itself from this data. Do not manually edit components, routes, styles, or generated site files.
3. Select the portfolio branch that matches the release channel, normally `dev` for a Snapshot. Copy the verified distribution ZIP without renaming it from the plugin repository's `build/distributions` into `public/lib/plugins/intellij/blend-charm-<branch>/`. Create the branch directory only if the corresponding `BlenderDevelopment.branches.<branch>` entry exists.
4. Compare the copied file's byte size and SHA-256 with the built and Codeberg-verified artifact. Stop if the destination already exists with different content; never overwrite a mismatched published artifact without explicit approval.
5. Locate `BlenderDevelopment.branches.<branch>.versions` and insert the new release first without changing older entries. Preserve the surrounding JSON structure and formatting.
6. Populate the entry from verified release data:
   - Set `version` and `label` to `X.Y.Z Snapshot`.
   - Set `fileName` to the published `BlenderPythonDevelopment-X.Y.Z-SNAPSHOT.zip` asset name.
   - Keep `sourceCode` as an empty string.
   - Set `changelog` to `BlenderDevelopment-X.Y.Z-SNAPSHOT`.
   - Add one build with the release date formatted as `MM/DD/YYYY`, artifact byte size divided by 1,000,000 and rounded to two decimal places with the `MB` label, `license` set to `GNU/GPL V3`, a concise summary derived from the release changelog, and `disabled` set to `false`.
7. Parse the file as JSON and verify the new entry against the published tag, copied asset name, artifact byte size, date, and changelog summary. Never guess release metadata.
8. Run the portfolio repository's `.githooks/pre-commit` and `pnpm build`. Treat files modified by the pre-commit hook as intentional generated updates that are safe to stage and commit with the release data after reviewing their diffs.
9. Stage `src/data/json/projects/addon-index.json`, the copied ZIP, and the reviewed pre-commit-generated changes, then commit them together using the portfolio repository's commit convention. Leave unrelated changes unstaged, and do not push the portfolio commit unless the user explicitly requests it.

## Completion boundary

- A build request ends after changelog reconciliation, validation, artifact creation, and checksum reporting.
- A preparation request ends after the release commit, annotated tag, and successful local `--check`; leave remote state untouched.
- A publishing request ends only after the branch and tag are present on Codeberg, the release script verifies the published asset, and the matching portfolio release entry and local artifact are validated and committed.
