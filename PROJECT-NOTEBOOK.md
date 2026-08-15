# Project Notebook

Use this notebook to capture work that has not yet been scheduled or completed. Move user-visible changes to
`CHANGELOG.md` once they are implemented and ready for release.

## Issues

<!-- Bugs, regressions, confusing behavior, and technical problems. -->

- [x] Consolidate the duplicated installation-scanning actions currently exposed by **Blender Discovery** and
  **Blender Version Management** in the application settings.
- [x] Enforce the persisted **Perform post-install cleanup?** setting after a managed Blender installation completes.
- [ ] Make the download-cache size setting govern cache eviction or remove the setting if cache retention will not be
  configurable.
- [ ] Verify downloaded Blender archives against Blender's published checksums before extraction.
- [x] Give each managed installation an isolated staging directory, so concurrent installations and stale extracted files
  cannot interfere with one another.
- [ ] Add stronger automated or repeatable integration coverage for live Blender integration and platform-specific ZIP,
  TAR, and DMG extraction.

## Potential Features

<!-- Ideas worth exploring that are not yet committed roadmap items. -->

- [x] Add a Command Run Configuration that allows running `blender --command`.
  Please refer to [CLI_COMMANDS.md](docs/CLI_COMMANDS.md) for more information on the available commands.
- [ ] Add a dedicated Blender file selector for Run and Debug launches. A `.blend` path can already be entered through the
  generic Run Arguments field, but there is no file-specific control or validation.
- [ ] Support legacy PyCharm versions, such as 2024 and 2025. The Kotlin backend can receive a `pydev` protocol handshake,
  but the bundled runtime still starts `debugpy` and the plugin currently requires build 261 or newer.
- [ ] Reintroduce Blender configuration sandboxing so development runs can use a clean environment without user
  modifications or extensions.
- [ ] Publish SHA-256 checksums alongside downloadable plugin artifacts on the main website instead of requiring users to
  retrieve them from GitLab.

## Changes Needed

<!-- Refactors, maintenance, documentation, testing, and other required work. -->

### Add new Command Run Configuration

- Status: Completed
- Priority: High
- Area: [run](src/main/kotlin/com/sakurasedaia/blenderdevelopment/run)
- Related files:
  - [run](src/main/kotlin/com/sakurasedaia/blenderdevelopment/run)
  - [ProjectConfig.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/state/ProjectConfig.kt)
  - [PluginConfig.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/state/PluginConfig.kt)
  - [BlenderLauncher.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderLauncher.kt)
- Related issue:

#### Context

This feature will be a good quality-of-life feature alongside this plugin's dedicated bespoke Debug runner.

#### Desired Outcome

Users can create a dedicated run configuration that invokes Blender CLI commands through `blender --command` while reusing
the plugin's established Blender installation and process-launch infrastructure.

#### Notes

- Use [CLI_COMMANDS.md](docs/CLI_COMMANDS.md) as the command reference.
- Consider both Run and Debug behavior explicitly when defining the configuration lifecycle.

#### Completion Checklist

- [x] Implementation completed
- [x] Tests added or updated
- [x] Documentation updated
- [x] Changelog updated, if user-visible

### Consolidate application-level Blender settings

- Status: In progress
- Priority: High
- Area: Application settings
- Related files:
  - [BlenderSettingsContent.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsContent.kt)
  - [PluginConfig.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/state/PluginConfig.kt)
  - [ScrapeBlenderVersionLists.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/services/ScrapeBlenderVersionLists.kt)
- Related issue: Duplicated installation scanning and settings that do not match runtime behavior

#### Context

Blender discovery and version management previously exposed the same installation scan. They now share one **Blender
Versions** workflow, but the minimum Blender version remains user-configurable even though the planning direction is to make
it an internal compatibility boundary. In the current code it controls the oldest release included in online discovery.
Post-install cleanup is enforced after successful managed installations, while the older cache-size state is persisted
without a current UI control or eviction consumer.

#### Desired Outcome

Each settings section has a clear responsibility, installation scanning has one obvious entry point, the minimum compatible
Blender version is maintained internally, and every remaining managed-download control affects runtime behavior.

#### Notes

- The current minimum compatible Blender version is 4.2.
- The internal minimum should track either Blender's extension-system minimum or the oldest release supported by the plugin.
- Decide whether the cache-size control should be implemented or removed before reorganizing the pane around it.
- Audit evidence: the consolidated version-management controller owns the installation scan; managed installation consumes
  the cleanup value, while only settings/configuration code references the cache-size value.

#### Completion Checklist

- [x] Remove or reorganize the duplicated installation-scanning action
- [ ] Move the minimum compatible Blender version out of user-facing settings
- [x] Enforce post-install archive cleanup
- [ ] Implement cache eviction or remove the unsupported cache-size setting
- [x] Tests added or updated
- [ ] Documentation updated
- [x] Changelog updated, if user-visible

### Harden the managed-installation lifecycle

- Status: In progress
- Priority: High
- Area: Managed Blender installation
- Related files:
  - [InstallBlender.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/InstallBlender.kt)
  - [ArchiveUtil.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/util/ArchiveUtil.kt)
  - [InstallBlenderTest.kt](src/test/kotlin/com/sakurasedaia/blenderdevelopment/core/InstallBlenderTest.kt)
- Related issue: Archive verification, staging isolation, cleanup, and extraction coverage

#### Context

Managed Blender installation is the largest remaining area requiring hardening. Download cancellation, managed deletion,
isolated extraction, extraction-failure cleanup, and configured archive cleanup exist, but verification, cache eviction, and
platform-level integration coverage are not yet represented as one explicit workflow.

#### Desired Outcome

Managed installations follow an explicit end-to-end lifecycle that safely verifies downloads, isolates concurrent work,
cleans partial extraction state on failure, and enforces configured archive cleanup and cache retention.

#### Notes

- Evolve the existing services incrementally instead of introducing another broad state refactor.
- Check downloaded archives against Blender's published checksums before extraction.
- Use one isolated staging directory per installation.
- Cover live Blender integration and platform-specific ZIP, TAR, and DMG extraction with automated or repeatable tests.
- Audit evidence: `InstallBlender.extractBlender()` creates a unique `blender-extract-` directory and removes it in `finally`;
  the only SHA-256 implementation protects bundled runtime resources rather than downloaded Blender archives.
- Existing unit tests cover download result propagation, artifact moves, deletion, and configured post-install cleanup, but
  not real ZIP, TAR, or DMG extraction or a live Blender install.

#### Completion Checklist

- [ ] Add checksum verification before extraction
- [x] Isolate installation staging directories
- [x] Add extraction-failure cleanup behavior
- [x] Connect archive cleanup to its setting
- [ ] Connect cache eviction to its setting
- [ ] Add or strengthen integration coverage
- [ ] Documentation updated
- [x] Changelog updated, if user-visible

### Restore Blender configuration sandboxing

- Status: Idea
- Priority: Medium
- Area: Run and debug environment
- Related files:
  - [BlenderLauncher.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderLauncher.kt)
  - [ProjectConfig.kt](src/main/kotlin/com/sakurasedaia/blenderdevelopment/state/ProjectConfig.kt)
- Related issue:

#### Context

User modifications and installed Blender extensions can interfere with development and make run/debug behavior difficult to
reproduce.

#### Desired Outcome

Developers can launch Blender with an isolated configuration, so testing occurs in a clean, predictable environment.

#### Notes

- This restores a capability from an earlier stage of the project.
- Define which Blender configuration, scripts, extensions, and environment values are isolated before implementation.
- Audit evidence: the launcher forwards user-defined arguments and environment variables, but it does not construct or own an
  isolated Blender user-configuration directory.

#### Completion Checklist

- [ ] Sandboxing boundaries and lifecycle defined
- [ ] Run and Debug paths supported
- [ ] Tests added or updated
- [ ] Documentation updated
- [ ] Changelog updated, if user-visible

## Entry Template

Copy this template beneath the appropriate section when an item needs more context.

```markdown
### Short title

- Status: Idea | Investigating | Planned | In progress | Blocked | Completed
- Priority: Low | Medium | High
- Area:
- Related files:
- Related issue:

#### Context

What is happening, or what opportunity was identified?

#### Desired Outcome

What should be true when this item is complete?

#### Notes

- Relevant constraints, decisions, or follow-up questions.

#### Completion Checklist

- [ ] Implementation completed
- [ ] Tests added or updated
- [ ] Documentation updated
- [ ] Changelog updated, if user-visible
```

## Completed / Archived

<!-- Keep brief historical notes here or remove entries after they have been tracked elsewhere. -->

- Blender API linting-stub setup was removed from active work after the 2026-08-07 audit. Project creation now optionally
  resolves, installs, records, and writes a version-matched stub dependency, with focused resolver, installer, metadata, and
  wizard tests.
- A generic request for “Repository Root Level configuration” was removed during the 2026-08-07 audit because it did not
  define a behavior or failure that could be validated. Re-add it with a concrete consumer and desired outcome if needed.
- The broad “improve codebase stability and structure for 1.0” entry was removed during the 2026-08-07 audit because it was
  not independently testable. Concrete maintenance findings should be recorded as separate scoped entries.
