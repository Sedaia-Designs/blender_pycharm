Understood—I’ll triage the entire SARIF report. I’ll group duplicates, exclude formatting-only and generated/external-file noise, then present the actionable findings with source locations and severity.

The report has 171 raw findings, but 74 are file-wide formatter notices and many Python warnings are Blender API false positives (dynamic properties, required callback signatures, and naming conventions imposed by Blender). The useful subset is much smaller; I’m separating correctness/API-compatibility risks from cleanup opportunities.

Across the full Qodana report, these are the relevant findings after removing formatter noise, Blender API false positives, naming conventions, and purely cosmetic suggestions.

## API compatibility risks

- [`BlenderDebugAttachService.kt:215`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderDebugAttachService.kt:215) uses internal Python debugger APIs. The related findings at lines 222–233 are the same underlying issue. These APIs may break between PyCharm versions.
- [`BlenderToolWindowContent.kt:36`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/toolwindow/BlenderToolWindowContent.kt:36) casts `Project` to internal `ComponentManagerEx` and obtains its internal coroutine scope. This is fragile across IntelliJ Platform updates.
- [`BlenderToolWindowView.kt:145`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/toolwindow/BlenderToolWindowView.kt:145) calls an experimental Kotlin UI DSL overload.
- [`BlenderNewProjectGenerator.kt:129`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/wizard/BlenderNewProjectGenerator.kt:129) constructs experimental `MessageError`.
- [`BlenderStubInstallationService.kt:132`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/stubs/BlenderStubInstallationService.kt:132) and [line 169](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/stubs/BlenderStubInstallationService.kt:169) depend on an experimental error type through `Result.Failure.error`.

## Dead or unused code

These symbols have no repository references and should either be removed or intentionally documented as future/API surface:

- [`SettingsInstallationScanService.scanInstallations()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/services/SettingsInstallationScanService.kt:18)
- Resolved, UsedImplicitly annotation added, [`BlenderLauncher.startProcess()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderLauncher.kt:106)
- [`BlenderInstallationScanner.logNoInstallsSummary()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderInstallationScanner.kt:115)
- Resolved, Added @Suppres as it's future functionality [`InstallBlender.updateVersion()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/InstallBlender.kt:328)
- [`BlenderEditorServerService.findSetupPayload()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt:100)
- [`findActiveSessionPayload()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt:110)
- [`getActiveSessionPayloads()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt:127)
- Both [`ExternalProcessBuilder.launchAndCaptureOutputAsync()` overloads](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/process/ExternalProcessBuilder.kt:118)
- [`ScriptDirectoriesTable.getDirectories()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/components/ScriptDirectoriesTable.kt:179)
- [`ScrapeBlenderVersionLists.getAvailableVersions()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/services/ScrapeBlenderVersionLists.kt:35)
- [`PluginResources.importResource()`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/util/PluginResources.kt:68)
- [`BlenderToolWindowController.detectedInstallations`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/toolwindow/BlenderToolWindowController.kt:35)
- [`PluginLogger.project`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/logging/PluginLogger.kt:48)
- Unused computed properties in [`BlenderVersions.kt:53`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/BlenderVersions.kt:53): `pyMajorMinor`, `blFallback`, and `pyFallback`.

I would verify `InstallType.USER` and `InstallType.PYCHARM` before removing them because enum values can be consumed through configuration or serialization without direct code references.

## Concrete cleanup defects

- [`ScriptDirectoriesTable.kt:33`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/components/ScriptDirectoriesTable.kt:33) contains an unused import.
- [`load_addons.py:102`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/python/load_addons.py:102) repeats the loop condition inside the loop; the inner `if` is redundant.
- [`load_addons.py:108`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/python/load_addons.py:108) declares `Optional[str]`, but one execution path reaches the end implicitly. An explicit `return None` would clarify the contract.
- [`communication.py:94`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/python/communication.py:94) initializes `last_exception` twice; the assignment inside the loop at line 97 is redundant.
- [`BlenderStubDependencyFileUpdater.kt:17`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/stubs/BlenderStubDependencyFileUpdater.kt:17) unnecessarily escapes `]` inside the regex character class.
- Broken KDoc links:
    - [`SystemInfo.kt:62`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/util/SystemInfo.kt:62): `osName` and `osArch`
    - [`BlenderManifest.kt:28`](/Users/Sakura/Documents/IdeaProjects/blender_pycharm/src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/BlenderManifest.kt:28): `filePath`

Excluded from the actionable list: 74 generic formatting notices, Blender `StringProperty` annotation errors, required unused Blender callback parameters, Blender naming conventions, template import warnings, property names that mirror manifest fields, and minor syntax-style suggestions.
