---
name: gitlab-release-publishing
description: Prepare and publish Blender Python Development pre-releases through the repository's manual GitLab CI pipeline and synchronize published artifacts to the Sakura portfolio. Use for release preparation, changelog curation, release validation, GitLab pipeline publishing, or portfolio release-index updates.
---

# GitLab release publishing

## Preserve repository state

- Work from the repository root containing `.gitlab-ci.yml`, `build.gradle.kts`, and `CHANGELOG.md`.
- Inspect `git status --short --branch` before editing and preserve unrelated changes.
- Treat commits, tags, pushes, manual pipeline execution, and portfolio synchronization as separate authorized stages.
- Never access protected secret paths or print CI/CD variables and tokens.

## Prepare a release

1. Read the literal `version` in `build.gradle.kts`. Map `X.Y.Z-SNAPSHOT` to changelog version `X.Y.Z-Snapshot` and tag `vX.Y.Z-Snapshot`.
2. Establish the previous release tag and review the complete delta through `HEAD`.
3. Curate the matching `CHANGELOG.md` section from final user-visible behavior, not commit subjects.
4. Run `bash scripts/tests/prepare-gitlab-release-test.sh` and other targeted tests.
5. Run `./gradlew compileKotlin test buildPlugin --no-daemon`.
6. Run `scripts/prepare-gitlab-release.sh <temporary-output-directory>` and verify the generated tag, asset name, and release notes.
7. Check the artifact SHA-256, `git diff --check`, and worktree status.

## Publish through GitLab CI

Perform remote actions only when the user explicitly requests them.

1. Commit and push the prepared release changes only with explicit authorization.
2. In GitLab, select **Build > Pipelines > Run pipeline** for the intended commit or branch. The CI configuration rejects automatic push, merge-request, schedule, and tag pipelines.
3. Start `prepare_release` manually and verify that compilation, tests, plugin build, and metadata preparation succeed.
4. Review the artifact and release notes, then start `publish_release` manually. This job creates the expected tag and GitLab release and links the distribution ZIP.
5. Report the tag, release URL, artifact name, validation performed, and any skipped checks.

## Publish to JetBrains Marketplace

- Use `scripts/release-marketplace.sh --check` for local validation.
- Use `scripts/release-marketplace.sh` only when Marketplace publication is explicitly authorized and `PUBLISH_TOKEN` is already available.
- Keep Marketplace publication separate from the GitLab release pipeline.

## Synchronize the portfolio

After verifying the GitLab release, update the separate portfolio repository according to its own `AGENTS.md`. Copy the exact verified ZIP, update only the authoritative release-index data, validate hashes and metadata, run that repository's checks, and keep its commit separate. Never push without explicit authorization.

## Completion boundary

- Preparation ends after changelog reconciliation, successful validation/build, and metadata/checksum reporting.
- Publication ends after the manual GitLab release job succeeds and the release asset is verified.
- Portfolio synchronization ends after its data, artifact, validation, and separate commit are complete.
