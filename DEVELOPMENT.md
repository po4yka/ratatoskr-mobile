# Developing Ratatoskr Mobile

> Status: Android/iOS explicit URL/text/file staging and shared library preview surfaces implemented
> Last reviewed: 2026-08-30

The repository contains buildable Android and iOS application shells around one shared Compose
Multiplatform root. Device pairing, rotating device sessions, native secure credential storage,
and session-scoped Platform capability discovery are implemented. Shared URL, text-note, and
staged-file-reference models plus the durable Room capture queue are implemented. Android
`ACTION_SEND text/plain` URL intake, shared Compose staging/status UI, WorkManager submission,
generic notifications, and validated operation-detail routing are implemented. The iOS Share
Extension performs bounded native parsing and atomic App Group staging; the main app confirms
through shared Compose, submits from the Room queue, and exposes shared operation status. File
transfer protocol, protected file staging, resumable checkpoints, retention, constrained native
schedulers, complete local erasure, and shared storage UI are implemented. Production file delivery
is explicitly `IntegrationPending`: the pinned Platform OpenAPI has no public receipt binding, so
shipping code sends no file bytes.
The shared library now consumes Platform `library.search` and `library.read_state`; full reader,
favorite, note, collection, tag, social, and AI-archive data remains an explicitly unsynchronized
contract-fixture preview until public Platform contracts exist.

## Toolchain

- JDK 17 and the committed Gradle wrapper;
- Android SDK platform 36; the shell supports Android 8.0/API 26 and newer;
- Xcode 26 or newer with an arm64 iOS Simulator SDK and the bundled `swift format` command; Compose
  1.11.1 requires the Xcode 26 SDK to link its UIKit surface, while the shell supports iOS 18.5 and
  newer;
- OpenSpec 1.10.0 for proposal/spec validation.

Kotlin/Compose and library versions are pinned in `gradle/libs.versions.toml`. Gradle is capped at
four workers in `gradle.properties`. KSP generates the Room 3.0.1 KMP current-schema database for
Android and both configured iOS targets. Manual dependency injection is the bootstrap choice.

## Product gate

Run these commands from the repository root. `build-gate` is the machine-wide serialization guard
for every local Gradle or Xcode build on the Ratatoskr development Mac; hosted CI runs the same
underlying commands directly.

```bash
./tooling/tests/bootstrap_structure_test.sh
./tooling/tests/architecture_boundary_test.sh
./tooling/tests/gate_parity_test.sh
./tooling/tests/ios_share_entitlements_test.sh
./tooling/tests/platform_library_contract_test.sh
./tooling/tests/github_contract_fixture_test.sh
./tooling/tests/blob_transfer_contract_drift_test.sh
./tooling/tests/app_link_shell_test.sh
./tooling/tests/privacy_source_gate_test.sh

build-gate -- ./gradlew --no-daemon ktlintCheck
build-gate -- ./tooling/contracts/check.sh
build-gate -- ./tooling/tests/contract_drift_test.sh
build-gate -- ./gradlew --no-daemon :shared:testDebugUnitTest :androidApp:assembleDebug
build-gate -- ./gradlew --no-daemon :shared:connectedDebugAndroidTest
build-gate -- ./gradlew --no-daemon :androidApp:connectedDebugAndroidTest

swift format lint --recursive --strict iosApp
build-gate -- ./gradlew --no-daemon :shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
simulator_id="$(xcrun simctl list devices available --json | jq -er '[.devices[][] | select(.isAvailable and (.name | startswith("iPhone")))][0].udid')"
build-gate -- xcodebuild -quiet -project iosApp/Ratatoskr.xcodeproj -scheme Ratatoskr -sdk iphonesimulator -destination "platform=iOS Simulator,id=$simulator_id" -resultBundlePath build/ios-share-test-results.xcresult test
build-gate -- xcodebuild -project iosApp/Ratatoskr.xcodeproj -scheme Ratatoskr -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build

openspec validate --all --strict
openspec validate --archived
```

The Android app is a thin native Activity. The iOS app is a thin SwiftUI/UIKit shell whose Xcode
build phase embeds the shared Objective-C-compatible framework. Both host the shared pairing and
current-capability surface. Native KMP source sets supply the Keystore/Keychain adapters and Ktor
engines; the common coordinator owns pairing, serialized rotation/recovery, revocation, and UDF
state.

Android instrumentation runs the real AES-GCM/Android Keystore round trip. The app-hosted iOS
XCTest target runs the real device-only, non-synchronizing Keychain round trip. The unhosted
Kotlin/Native Simulator test is intentionally skipped because Security returns
`errSecNotAvailable` without an application host; it remains compile coverage, while the hosted
XCTest is the runtime gate.

Android app instrumentation covers `AndroidShareIntentParserTest`, `ShareStagingUiTest`,
`CaptureSubmissionWorkerTest`, `OperationStatusUiTest`, `CaptureStatusNotificationTest`, and the
end-to-end `AndroidShareSmokeTest`, plus `LibraryDeepLinkIntentTest`, `AndroidAppLinkIntentTest`,
`LibraryUiTest`, `LibrarySearchUiTest`, and `AccessibilityUiTest`. The item-9 smoke method is
`shell_wires_search_https_routes_notification_truth_russian_and_accessible_navigation`. The smoke
uses only synthetic data and a deterministic
operation fixture on an API 35 emulator; it proves activity/database reopen and UI wiring, not a
live Platform deployment or physical device. Hosted CI retains the report as the
`android-share-test-reports` artifact.

## GitHub catalog and action gate

`GithubContractCodecTest`, `PlatformGithubApiTest`, `GithubCatalogStoreTest`,
`GithubConfirmationStoreTest`, and `GithubActionOutcomeStoreTest` cover the strict pinned preview
and action contracts, authenticated Platform transport, fixture-only browse/search, one-shot
track/star confirmation, partial component truth, revocation, and same-key uncertain retry.
`GithubCatalogUiTest` renders the shared Compose catalog/detail/confirmation/result states on the
API 35 emulator. The iOS KMP test
`github_graph_shares_device_authorization_capabilities_and_fixture_browse_without_provider_credentials`
proves the same graph is hosted by the thin iOS controller. `github_contract_fixture_test.sh` and
the contract mutation gate pin the four reviewed schemas and their valid/invalid fixtures.

Browse/search evidence is deliberately unsynchronized and reset-on-restart. Only detail preview
and confirmed actions use the paired Platform `/v1/gh/repositories/preview` and
`/v1/gh/repositories/actions` boundaries. These gates do not prove a live GitHub service,
connected provider account, Vault completion, physical device, or provider write.

## iOS Share Extension and operation gate

The `Ratatoskr` Xcode scheme builds the app, embeds `RatatoskrShare.appex`, and runs both hosted test
targets. `ShareExtensionParserTests` and `AppGroupEnvelopeTests` cover hostile item-provider input,
deadlines, exact schema, and atomic publish. `AppGroupInboxImporterTests` covers serialized rename
claims, cancellation, retention, and restart recovery. `IosSubmissionSchedulerTests` exercises a
fake BackgroundTasks boundary; `IosSubmissionStatusFlowTests` covers confirmation, offline,
operation fixtures, scene polling, and reauthentication. `IosKeychainCredentialStorageTests`
performs explicit access-group round-trip/replacement/deletion and wrong-group isolation.

`IosShareSmokeTests` uses synthetic content to pass the real extension parser and envelope through
the App Group importer, close/reopen the real Room KMP queue with the same idempotency key, and
render a terminal fixture status. CI retains its content-free result as `ios-share-test-results`.
The smoke is simulator/fixture proof, not live Platform, guaranteed BackgroundTasks delivery,
physical-device extension budget, provider, release-signing, file-upload, or App Store proof.
`IosLibraryRoutingTests` proves cold/warm custom-scheme handoff and fail-closed invalid-link handling.
It also runs `testConfiguredUniversalLinksForwardRawAndResolveCanonicalDestinations`,
`testForeignOrAmbiguousUniversalLinksAreRejected`, and
`testBrowsingUserActivityUsesTheSharedRouteTable`. `IosNotificationPermissionTests` proves the
integration-pending, explicit available, denied, and revoke paths. The shell-level item-9 case is
`testShellWiresUniversalLinksNotificationTruthRussianAndPrivateCanaryAbsence`.

## Library and content-routing gate

`PlatformLibraryApiTest` and `LibraryListStoreTest` cover the generated live recent/read-state
boundary, capability gating, authorization, pessimistic mutations, and uncertain outcomes.
`FixtureUserContentRepositoryTest` and `LibraryReaderStoreTest` cover reset-on-restart favorite,
note, collection/tag membership, provenance, warnings, social authority, and AI archive
completeness without Platform calls. `ContentRouteTableTest` accepts only the documented
lowercase canonical UUID routes and rejects query, fragment, credentials, encoding ambiguity,
traversal, unknown providers, and extra segments.

`LibrarySearchStoreTest` covers ranked pagination, request fencing, validation, offline retry, and
repairing state. `CompletionNotificationStoreTest` covers truthful contract availability and
one-shot permission policy. `MobileStringsTest`, `AccessiblePaletteTest`, and
`MobileDiagnosticsTest` cover EN/RU state parity, WCAG contrast, and the content-free diagnostic
type boundary.

## Capture queue gate

`CaptureQueueIdempotencyTest`, the ordering/retry/resolution common suites, and capture-model and
operation-projection tests run through both shared test targets. `AndroidCaptureQueuePersistenceTest`
and `IosCaptureQueuePersistenceTest` create the one current Room schema from an empty file and prove
close/reopen preservation of payload, source sequence, local ID, and idempotency key. The iOS suite
also proves the native protection seam is invoked. iOS Simulator does not report
`NSFileProtectionKey` after a successful attribute write, so physical-device attribute inspection
is a release-check gap rather than claimed simulator evidence.

## File transfer, retention, scheduling, and erasure gate

`BlobTransferContractTest`, `ResumableUploadCoordinatorTest`, `ArtifactRetentionPolicyTest`,
`TransferSchedulingPolicyTest`, and `LocalDataErasureCoordinatorTest` cover generated semantics,
resume reconciliation, bounds, durable leases, and marker-last erasure.
`blob_transfer_contract_drift_test.sh` mutates a pinned schema and generated Kotlin output.

Android instrumentation runs `AndroidStagedArtifactStoreTest`, `FileUploadWorkerTest`,
`AndroidLocalDataErasureInstrumentedTest`, and `LocalStorageUiTest`. Hosted iOS XCTest runs
`IosFileUploadSchedulerTests`, `IosLocalDataErasureTests`, `IosLocalStorageSurfaceSmokeTests`, and the synthetic file case in
`IosShareSmokeTests`. This is emulator/simulator evidence, not live receipt, guaranteed background,
physical-device protection/battery, release-signing, or store proof.

## Generated Platform contracts

`contracts/platform-openapi.json` is an exact pinned Platform public OpenAPI document. Its producer
commit, document digest, generator version, configuration digest, and documented compatibility
normalizations live in `contracts/platform-openapi.lock.json`. The generator owns only
`shared/src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model`.

To update the pin, first obtain the exact reviewed Platform revision, then update the snapshot and
lock manifest before running:

```bash
build-gate -- ./gradlew --no-daemon generateContracts -PplatformOpenApi=/absolute/path/to/platform/openapi/openapi.json
build-gate -- ./tooling/contracts/check.sh
```

Never hand-edit the generated model tree. The checker verifies source/configuration provenance and
compares a fresh temporary generation with every committed generated file. Its mutation suite also
proves that a valid-JSON change to the pin fails closed.

## OpenSpec setup

Install the version used by the gate:

```bash
npm install --global @fission-ai/openspec@1.10.0
```

Cross-repository behavior lives in the workspace store, whose registration is per-machine state:

```bash
git clone git@github.com:po4yka/ratatoskr-workspace.git <path>
openspec store register <path> --id ratatoskr-workspace
openspec doctor
```

## Working rules

1. Start every non-trivial change with OpenSpec and pair each behavior test task with its passing
   implementation task.
2. Keep shared Compose presentation/domain behavior separate from native lifecycle, share,
   security, background, file-access, notification, and accessibility adapters per ADR-0001.
3. Treat every inbound URL, file, and text item as hostile and persist bounded queue state before
   network work once capture behavior exists.
4. Keep device secrets behind Keystore/Keychain adapters and provider credentials in backend
   services.
5. Add platform lifecycle, offline, failure, accessibility, privacy, and performance evidence with
   the features that introduce those behaviors.
