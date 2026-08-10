# Bounded Blender Installation Scan Plan

Scope: bound the complete installation scan to one overall deadline so runtime cannot grow as `10 seconds × unresponsive candidates`. Sequential probing and the existing per-process timeout remain in place. Parallel probing and broader EEL migration are out of scope.

## Work plan

- [ ] Define the overall deadline contract in `SettingsInstallationScanService`, initially using a conservative duration of approximately 30 seconds.
  - Document whether `null` or `Duration.ZERO` disables the deadline.
  - Reject negative durations.
  - Treat deadline expiration separately from disposal, interruption, and explicit caller cancellation.
- [ ] Add a monotonic deadline using `System.nanoTime()` when a scan request begins.
  - Compose the scanner cancellation callback from caller cancellation, project disposal, thread interruption, and deadline expiration.
  - Reuse the scanner checkpoints and process runner's 200 ms polling instead of scheduling a separate timer.
- [ ] Preserve distinct termination outcomes.
  - Continue using `CancellationException` for disposal, interruption, and explicit cancellation.
  - Represent overall deadline expiration with a dedicated internal failure or result.
  - Ensure the deadline outcome returns through `BlenderSettingsOperations` so the Controller always leaves `SCANNING`.
- [ ] Preserve cache integrity when the deadline expires.
  - Retain the final active-state check before `setDetectedBlenderInstalls()`.
  - Verify that a partial, deadline-expired scan does not replace the previous authoritative cache.
  - Document the transactional rule: a complete scan replaces the cache; an interrupted scan preserves it.
- [ ] Coordinate the overall deadline with per-probe limits.
  - Retain the ten-second `blender --version` timeout as a local safety bound.
  - Verify that the composed cancellation callback terminates an active probe shortly after the overall deadline rather than waiting for the full probe timeout.
  - Check the deadline before directory traversal, candidate processing, version probing, configured-root scanning, and the final cache write.
- [ ] Consolidate user feedback.
  - Add a localized overall-scan-timeout message if the outcome is user-visible.
  - Ensure only one layer owns the final notification.
  - Do not emit the normal scan-completed notification after deadline expiration.
  - Log elapsed duration and that the previous cache was preserved.
- [ ] Introduce a narrow deterministic test seam.
  - Inject or extract the monotonic time source and configurable overall duration.
  - Keep the seam internal and avoid an interface used only for mocking.
- [ ] Add `SettingsInstallationScanService` deadline tests.
  - Verify completion before the deadline.
  - Verify deadline expiration requests cancellation.
  - Verify explicit cancellation remains distinguishable from deadline expiration.
  - Verify project disposal still cancels the scan.
  - Verify disabled and negative deadline behavior.
- [ ] Add scanner integration tests.
  - Verify deadline cancellation stops traversal before later candidates are probed.
  - Verify an active version process receives cancellation through `shouldCancel`.
  - Verify deadline expiration preserves the detected-installation cache.
  - Verify a completed scan still commits its complete results.
- [ ] Validate the Settings UI state flow.
  - Verify `BlenderSettingsOperations` delivers deadline failure on the EDT.
  - Verify `BlenderVersionManagementController` clears `SCANNING` after deadline expiration.
  - Verify disposal suppresses completion and user feedback.
  - Verify no normal success notification is emitted for an expired scan.
- [ ] Run validation.
  - Run `git diff --check`.
  - Run focused service, scanner, process-runner, operations, and Controller tests.
  - Run `./gradlew compileKotlin --no-daemon`.
  - Run the broader relevant test suite after focused validation passes.
- [ ] Update public scanning or troubleshooting documentation if the deadline and cache-preservation behavior are user-visible.
- [ ] Commit Changes.

## Risks and decision points

- A 30-second overall deadline prevents candidate-count multiplication but still needs a product decision balancing responsiveness against slow disks or remote environments.
- Treating deadline expiration as ordinary cancellation would suppress feedback and obscure why the scan ended; the flow should preserve a distinct reason.
- A scan may discover valid installations before its deadline expires. Committing those partial results would improve freshness but could incorrectly remove installations that were never reached, so preserving the previous cache is the safer default.
- The overall deadline can expire during a subprocess shutdown grace period, so observed wall-clock completion may exceed the configured duration slightly.
- Parallel probing could reduce wall-clock time further, but it increases process concurrency and lifecycle complexity and is intentionally deferred.

## Source references

- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/services/SettingsInstallationScanService.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderInstallationScanner.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/process/ExternalProcessBuilder.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/BlenderSettingsOperations.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/ui/settings/versions/BlenderVersionManagementController.kt`
