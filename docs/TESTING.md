# Mobile testing strategy

Required suites:

- Shared pure-model/queue/idempotency/retry and current-schema creation tests.
- Android Share Target for URL/text/single/multiple content URI, permission expiry, process death, WorkManager, Keystore, app links, notifications.
- iOS Share Extension item-provider/file/App Group handoff, extension timeout/memory, app launch, background URLSession, Keychain, universal links.
- Hostile/oversized/unsupported files, low disk, hash mismatch, duplicate shares, offline/auth revoke/server retry/partial results.
- Staged-file retention/cleanup and no-content logs/notifications.
- Accessibility: screen readers, Dynamic Type/font scaling, contrast, focus, localization layout.
- Generated API/capability compatibility and workspace mobile -> Platform -> domain flow.

Use synthetic files/local servers and platform emulators/simulators plus selected real-device lifecycle tests. No personal provider data in fixtures.

## Capture queue gate

Common tests cover bounded URL/text/file-reference models, exact serialization, enqueue convergence
and conflict, owner isolation, per-source FIFO, lease expiry and stale tokens, equal-jitter backoff,
server retry hints, exhaustion, permanent failures, authoritative operation binding, resolution
conflict, and monotonic generated-contract projections.

`AndroidCaptureQueuePersistenceTest` and `IosCaptureQueuePersistenceTest` create the Room database
from an empty file, enqueue through `CaptureQueue`, close all handles, reopen the same path, and
compare payload, source sequence, local ID, and idempotency key. Android runs on an emulator with
backup disabled. The iOS Simulator verifies invocation of the native protection seam but does not
expose the written `NSFileProtectionKey`; physical-device attribute inspection remains unverified.

## Device identity gate

The current identity suite covers the Platform pairing outcome matrix, canonical HTTPS origins,
atomic refresh rotation, concurrent caller coalescing, one device-root recovery after uncertain or
refused refresh, persisted consumed-link recovery after process restart, revocation, and fail-closed
session-scoped capabilities in common tests.

Native secret-storage runtime evidence is intentionally app-hosted:

- `:shared:connectedDebugAndroidTest` exercises ciphertext-only preferences and the real Android
  Keystore on an emulator;
- the `RatatoskrTests` Xcode target exercises a device-only, non-synchronizing Keychain item on an
  iOS Simulator;
- the unhosted Kotlin/Native Simulator Keychain test is skipped because Security returns
  `errSecNotAvailable` without an application host. It is still compiled by the KMP test target;
  the hosted XCTest is the runtime assertion.

These tests use synthetic credentials and a mocked Platform transport. They do not prove a live
Platform deployment or physical-device behavior.

## Android Share Target and operation gate

`:androidApp:connectedDebugAndroidTest` runs the app-owned instrumentation suites on an API 35
emulator:

- `AndroidShareIntentParserTest` covers URL placement plus unsupported, ambiguous, malformed,
  oversized, and non-share input;
- `ShareStagingUiTest` covers preview, paired/unpaired confirmation, cancellation, and one-shot
  durable enqueue;
- `CaptureSubmissionWorkerTest` covers network constraints, persisted delay, Room close/reopen,
  stable work input, and revoked-session behavior without an OS retry storm;
- `OperationStatusUiTest` renders generated running/partial/failed/completed fixtures and actionable
  offline/reauth states;
- `CaptureStatusNotificationTest` covers generic content, immutable explicit intents, denied
  permission, and invalid operation identifiers;
- `AndroidShareSmokeTest` carries a synthetic URL through Activity intake, queue acceptance,
  terminal projection, database reopen, and operation detail with a deterministic Platform fixture.

CI uploads the HTML/XML output as `android-share-test-reports`. This is emulator/fixture evidence,
not a live Platform, provider, physical-device, notification-delivery, or store-release claim.

## iOS Share Extension and operation gate

The shared iOS tests exercise stable handoff identity, Room close/reopen, bounded submission,
refresh rotation/revocation, and queue-derived wake time. Hosted XCTest adds:

- `ShareExtensionParserTests` and `AppGroupEnvelopeTests` for item-provider and atomic publish;
- `AppGroupInboxImporterTests` for one-at-a-time claims and restart recovery;
- `IosSubmissionSchedulerTests` for BackgroundTasks coalescing, early wake, expiration and repair;
- `IosSubmissionStatusFlowTests` for confirmation/offline/operation fixture and scene states;
- `IosKeychainCredentialStorageTests` for explicit-group isolation and device-only policy;
- `IosShareSmokeTests` for parser, handoff, real Room queue reopen, stable identity and terminal fixture.

`ios_share_entitlements_test.sh` checks exact configured groups and the reviewed background task.
The build gate also inspects Xcode's effective simulator `.xcent` files for the app and embedded
extension. CI retains `ios-share-test-results`. These are synthetic simulator results only, not
live Platform, physical-device budget/background delivery, release-signing, file-upload, provider,
or App Store evidence.

## Library, reader, and routing gate

The generated contract/transport suite verifies the exact recent and read-state resources,
bounded query/body encoding, redirect refusal, authorization, and unavailable/uncertain outcomes.
Common Android/JVM and iOS Simulator suites cover capability-gated list order, pessimistic
read-state replacement, fixture favorite/note/collection/tag semantics, provenance/warnings,
social Saved-authority limits, AI archive completeness, and the exact custom-scheme route table.

API 35 `LibraryUiTest` instrumentation checks shared Compose list/read state, fixture labels and
mutations, inert hostile text, and loading/empty/offline/reauth/integration-pending states.
`LibraryDeepLinkIntentTest` and hosted `IosLibraryRoutingTests` check cold/warm native handoff and
that invalid external links preserve the current destination. All content is synthetic. This is
not live Platform full-content/curation, universal-link, provider, or physical-device evidence.

## GitHub catalog and action gate

The pinned GitHub schema/fixture checker and mutation suite reject upstream drift. Common tests
cover strict preview/action decoding, current non-stale service-capability projection,
authenticated `/v1/gh` transport, stable fixture browse/search with zero list calls, one-shot
track/star confirmation, partial component outcomes, revocation, invalid responses, and same-key
explicit uncertain retry. API 35 `GithubCatalogUiTest` verifies shared Compose browse/detail,
confirmation, and non-happy states. The iOS Simulator graph test proves the same graph is hosted by
the thin controller; hosted XCTest and unsigned Xcode builds cover shell linkage.

All inputs are synthetic. Fixture browse is not Platform catalog evidence, and no test claims a
live GitHub service, connected account, provider write, Vault backup, or physical-device behavior.

## Test-first

A change is planned before it is built, and the plan is a task list in which behaviour arrives in
pairs: one task adds a failing test, the next makes it pass. `openspec/config.yaml` carries that
rule, which is what puts it into every planning and implementation request rather than only into this
document.

The loop:

1. Write the test the scenario names. Run it. Confirm it fails, and read the failure — a test that
   fails because it does not compile has proved nothing about the behaviour.
2. Write the smallest change that makes it pass. Run it again.
3. Refactor only once it is green, adding no test and changing no behaviour.

Two checks stand behind this, and neither of them can see the order:

- `openspec validate --archived`, in `.github/workflows/openspec.yml`, fails when a change was
  archived with a task left unticked.
- A step in `.github/workflows/fleet.yml` fails when this repository holds a manifest and a `ci.yml`
  that never runs a test.

`ratatoskr-workspace/docs/QUALITY_GATES.md` records why the order itself is not checkable, rather
than leaving the gap to be discovered.
