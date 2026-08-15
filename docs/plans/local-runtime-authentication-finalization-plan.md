# Local Runtime Authentication Finalization Plan

Scope: close only the remaining local-runtime authentication and HTTP-boundary findings recorded in the V1 release-readiness audit. Other release blockers and high-priority findings are out of scope.

## Work plan

- [x] Define the protocol contract and limits.
  - Establish one JSON media-type policy, a maximum request-body size, supported message types, and required fields.
  - Define status mapping: `401` for missing or invalid credentials, `413` for oversized bodies, `415` for unsupported content types, `400` for malformed JSON or schema violations, and a non-success response for unknown message types.
  - Specify valid setup values: session identifier, ports in `1..65535`, a nonblank scripts folder, a recognized debug protocol, and path mappings containing nonblank `src` and `load` strings.
  - Decide whether unknown fields remain forward-compatible or are rejected.
- [ ] Extract lightweight Python HMAC support.
  - Move or reuse key decoding and exact-body HMAC-SHA-256 signing in a module that imports only the Python standard library.
  - Keep `communication.py` and early bootstrap reporting on the same signature header and algorithm.
  - Fail safely when the launch key or identifier is unavailable without exposing either value in logs.
- [x] Sign bootstrap and dependency failure reports.
  - Serialize the payload once, sign those exact UTF-8 bytes, and transmit the same bytes from `src/main/python/__init__.py`.
  - Include the signature on both `bootstrapFailure` and `dependencyFailure` messages.
  - Preserve reporting during failures that occur before Flask, Requests, or other managed dependencies load.
- [x] Refactor the IDE request pipeline in `BlenderEditorServerService`.
  - Reject non-POST methods and invalid content types before consuming the body.
  - Read request bytes through a bounded stream and stop as soon as the configured limit is exceeded.
  - Parse JSON only after transport checks pass.
  - Resolve the identifier and authenticate the exact received bytes before dispatching every supported message type.
  - Use typed protocol exceptions or results so authentication failures are not collapsed into generic HTTP `400` responses.
  - Mutate or clear session state only after authentication and schema validation succeed.
  - Zero removed authentication-key byte arrays rather than merely removing their map entries.
- [x] Add explicit payload validation and dispatch.
  - Reject missing, blank, malformed, or unknown `type` values.
  - Validate setup ports, scripts folder, path mappings, and debug protocol before creating `BlenderSetupPayload`.
  - Validate failure-payload identifiers, type, message, and optional details before notifying the user.
  - Ensure rejected setup and failure reports cannot register, refresh, or invalidate a session.
- [x] Expand Kotlin HTTP integration tests.
  - Update existing authentication expectations from `400` to the selected `401` or `403` contract.
  - Cover signed bootstrap and dependency failures, plus missing, invalid, cross-session, expired, and replayed credentials where applicable.
  - Cover wrong or missing content type, malformed JSON, unknown type, absent fields, invalid ports, blank paths, malformed mappings, unsupported protocols, boundary-size bodies, and oversized or chunked requests.
  - Assert rejected requests leave authentication and runtime-session state unchanged.
  - Assert a valid authenticated failure report clears the intended pending session and triggers the expected notification behavior.
- [x] Expand Python protocol tests.
  - Verify early failure reports sign the exact transmitted bytes.
  - Cover both failure types and payloads containing Unicode.
  - Verify missing or malformed keys fail safely.
  - Add a compatibility test proving setup, failure reporting, and regular commands use the same header and signing contract.
- [x] Validate the completed authentication gate.
  - Run focused Kotlin server integration tests.
  - Run the Python protocol suite.
  - Run `./gradlew compileKotlin --no-daemon`.
  - Run the full JVM test suite.
  - Run Plugin Verifier because the work is part of V1 release closure.
  - Perform a manual smoke test for successful startup and an intentionally induced dependency or bootstrap failure.
- [x] Update release evidence and documentation.
  - Mark the local-runtime authentication gate complete in `docs/reports/v1-release-readiness-audit.md` only after every check passes.
  - Record exact test totals and remaining limitations, especially that localhost HMAC does not provide confidentiality.
  - Add an Unreleased changelog entry describing the final security behavior.
  - Update public troubleshooting documentation only if users will observe changed failure responses or diagnostics.
- [ ] Commit Changes.
  - Review the final diff for secret leakage and unrelated edits.
  - Commit the completed authentication work using the repository's `[Type -> module] Description` format.
  - Keep `docs/Wiki/internal` uncommitted and do not push.

## Key design decision

Authenticate all inbound message types through one server pipeline. Bootstrap failure reporting must use a lightweight standard-library Python signer because it can execute before third-party runtime dependencies are available.

HMAC verification must operate on the exact received request bytes. Decoding and then re-encoding a body before verification could change its representation even when the JSON has the same meaning, breaking the integrity contract.

## Risks and decision points

- The request-size limit must accommodate legitimate path-mapping payloads without allowing unbounded allocation. Select the value from representative large projects and cover its exact boundary in tests.
- Looking up a session key requires an identifier from the payload, but no payload should be trusted before authentication. Parse only the bounded body to locate the identifier, authenticate the original bytes, and defer all semantic processing and state mutation until verification succeeds.
- `401` is appropriate when credentials are absent or cannot be validated. Use `403` only if the server can distinguish an authenticated caller that is not authorized for the requested operation.
- Failure reports are expected before optional dependencies load, so importing `communication.py` from the early bootstrap path could recreate the failure being reported.
- Clearing a session after a valid failure report is intentional, but clearing it after an invalid report would allow denial of service against an active launch.
- Strict rejection of unknown JSON fields improves schema control but can impede protocol evolution. This must be decided explicitly before tests codify the contract.

## Source references

- `docs/reports/v1-release-readiness-audit.md`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerService.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderAuthentication.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderPythonLauncher.kt`
- `src/main/kotlin/com/sakurasedaia/blenderdevelopment/lib/ErrorTypes.kt`
- `src/main/python/__init__.py`
- `src/main/python/communication.py`
- `src/main/python/environment.py`
- `src/test/kotlin/com/sakurasedaia/blenderdevelopment/core/BlenderEditorServerServiceTest.kt`
- `src/test/python/test_runtime_communication_logging.py`
