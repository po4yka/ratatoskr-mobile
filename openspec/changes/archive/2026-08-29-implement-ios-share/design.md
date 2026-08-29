## Context

See `proposal.md` for motivation and the three delta specs for observable behavior. ADR-0001 assigns Share Extension lifecycle, item-provider loading, App Group access, Keychain entitlements, background scheduling, and scene lifecycle to native iOS while shared Compose/KMP owns staging UDF, queue rules, device-session authorization, Platform adapters, and operation presentation. ADR-0004 makes the current Room KMP queue the local submission truth; ADR-0005 keeps the credential record device-only and non-synchronizing.

The existing iOS app is one SwiftUI shell around `MainViewController`, with no application graph beyond pairing. The Xcode project has app, hosted XCTest, and test-host targets but no extension, App Group, background task registration, or production entitlements. Shared staging currently hard-codes Android provenance, while the queue already supports `IosShareExtension` and optional caller-supplied idempotency keys. The pinned Platform contract accepts URL capture only; text-only shares cannot be submitted truthfully.

## Goals / Non-Goals

**Goals:**

- Keep extension execution to bounded item-provider load, validation, atomic App Group publish, and accurate completion.
- Make the handoff identifier the stable bridge to queue idempotency across every extension/app crash boundary.
- Reuse the shared Compose staging and operation surfaces with a thin Swift scene/lifecycle adapter.
- Let foreground activation and an iOS background wake-up call the same bounded shared submission/status coordinator.
- Produce deterministic simulator/fixture evidence and effective-entitlement inspection without claiming live service or physical-device behavior.

**Non-Goals:**

- Loading Compose, Room, Ktor, device identity, or operation polling inside the Share Extension.
- Submitting arbitrary text, notes, tags, collections, files, security-scoped resources, or provider-specific modes absent from the current public contract.
- Background URLSession file transfer, universal links, notification delivery, rich result presentation, live Platform acceptance, physical-device extension budgets, release signing, or App Store publication.
- Changing the public API, adding a database migration, or creating a second iOS queue in the App Group.

## Decisions

### 1. A small native Swift extension owns item-provider parsing

Add a dedicated Share Extension target with a minimal UIKit controller and pure Swift `ShareExtensionParser`. It loads only `UTType.url` and `UTType.plainText`, caps the combined UTF-8 input at 100,000 bytes to match Android intake, accepts one semantic URL or one text value, deduplicates equivalent representations from the same provider, and refuses distinct multiple inputs. URL validation requires an absolute `http` or `https` URL with a host; the original value is retained separately.

The parser receives a bounded loader protocol in tests so synthetic `NSItemProvider` success, delay, failure, type precedence, duplication, ambiguity, and cancellation can be observed through its public result. Production imposes a short extension-owned deadline and cancels outstanding loads when the extension context is cancelled. Safe user-visible errors contain no shared content.

Alternatives considered:

- Linking the KMP framework into the extension would reuse a few models but materially increases startup/memory cost and pulls application graph code toward a transient sandbox.
- Treating every plain-text value as a note would fabricate a Platform field.
- Selecting the first of multiple links would silently discard explicit user input.

### 2. One atomic JSON envelope is the entire extension-to-app contract

Use App Group `group.com.ratatoskr.mobile` with an app-owned `ShareInbox` directory. The envelope contains only schema `1`, UUID handoff ID, capture timestamp, kind (`url` or `text`), original text, and optional validated URL. No owner, instance, account, credential, capability, operation, or provider data crosses this boundary. JSON is encoded deterministically, bounded to 128 KiB, written with data protection suitable after first unlock to a UUID-named temporary file, synchronized, and renamed on the same volume to `<uuid>.json` as the publish point.

The extension completes only after publish succeeds. It cancels with a safe error when the container, protection, capacity, or write boundary fails. Temporary files are never valid envelopes.

Alternatives considered:

- UserDefaults suites do not provide an atomic claim boundary or bounded file integrity.
- Opening the Room database from two processes expands concurrency and schema ownership into the extension.
- Invoking the main app by URL with payload content leaks input into routing/logging and still does not guarantee durable handoff.

### 3. Main-app import uses rename claims and stable queue identity

Native `AppGroupInbox` enumerates only allowlisted UUID JSON names beneath the known inbox without following links. It rejects files larger than 128 KiB before decoding, requires the filename UUID to match the envelope, validates exact kind/field combinations and the same input bounds, then atomically renames one item into an app-owned processing directory. A claimed item remains there across process death.

The Swift scene container passes the validated immutable envelope to an exported iOS application graph. The shared staging store is generalized to accept `CaptureSource`, an optional idempotency key, a stable capture time, and completion callbacks instead of hard-coding Android. For iOS the key is a namespaced derivation of the handoff UUID and the time is the envelope timestamp. Confirmation enqueues that exact request; success deletes the claimed file, cancel deletes it without queueing, and a local failure retains it. Re-import after uncertain commit presents the same fingerprint/key so `CaptureQueue.enqueue` converges.

Malformed files move only within the app-owned area to a bounded rejected state with content-free diagnostics; unrelated App Group files are untouched. Import is serialized so multi-window scene activation cannot present or claim the same envelope twice.

Alternatives considered:

- Deleting immediately after decode loses the only durable copy before confirmation.
- Minting queue identity at confirmation duplicates a capture when enqueue succeeds just before termination.
- Copy-based claiming permits two scenes to process one envelope concurrently.

### 4. One iOS application graph supplies shared Compose staging, submission, and status

Add an `iosMain` application graph that constructs the protected private Room queue, Keychain-backed `DeviceSessionManager`, Ktor capture/operation adapters, authorized executor, `CaptureSubmissionCoordinator`, operation stores, and the shared Compose root. Swift owns scene phase and App Group import, then calls narrow graph methods for foreground reconciliation, handoff presentation, background drain, and polling visibility.

The graph drains a bounded number of eligible captures and accepted-operation refreshes for the current owner. It never receives tokens or URLs through OS task identifiers. Pairing/capability/revocation state remains the existing coordinator's responsibility. `RatatoskrApp` navigation stays shared: successful staging can expose status, the paired surface opens the list, and scene inactivity sets detail polling invisible.

Manual construction remains appropriate; Koin would add a production dependency without reducing this one bootstrap graph.

### 5. Foreground reconciliation is guaranteed locally; BackgroundTasks is opportunistic

The Swift app registers one reviewed `BGAppRefreshTask` identifier before application completion, and a native `IosSubmissionScheduler` requests the earliest queue-derived wake-up with bounded coalescing. Scene activation always imports the inbox, reconciles unfinished queue work, and repairs a missing OS request. A background launch restores the shared graph, drains bounded eligible work, schedules the next persisted time, and completes. Expiration cancels its coroutine; the durable claim lease makes later recovery safe.

Tests inject a scheduler/expiration boundary and assert queue decisions through public graph results. The simulator smoke exercises foreground reconciliation because simulator delivery of `BGTaskScheduler` is not deterministic; physical-device background delivery remains a later evidence boundary.

Alternatives considered:

- Networking in the extension violates the requested fast handoff and couples correctness to extension lifetime.
- Treating `BGTaskScheduler` registration as guaranteed delivery would make OS policy authoritative.
- Background URLSession is reserved for item-8 file transfers; a small JSON capture does not justify a second transfer truth.

### 6. App Group and Keychain capabilities are explicit per target

Create checked-in entitlements for the app, extension, hosted test app, and test bundle configuration. The app and extension carry exactly `group.com.ratatoskr.mobile` and `$(AppIdentifierPrefix)com.ratatoskr.mobile.shared`; no wildcard groups are accepted. `IosKeychainCredentialStorage` gains an explicit access-group input and includes it in every Security query so replace/load/delete target one group consistently. The main app supplies the reviewed shared group. The extension target has matching capability identity as requested but contains no credential-storage call site and its production flow never queries Security.

The existing `AfterFirstUnlockThisDeviceOnly` and synchronizable-false policy remains. XCTest covers round-trip/replace/delete with the explicit group, a structural test rejects missing/mismatched/wildcard entitlement values, and the simulator smoke inspects effective entitlements of the built `.app` and embedded `.appex`. Credentials never enter the App Group envelope or diagnostics.

Alternative considered: leaving access group implicit works for one target but cannot prove or control the requested app/extension capability relationship.

### 7. Simulator smoke records a synthetic lifecycle boundary, not live acceptance

Add a simulator-hosted test application path that gives the real extension parser synthetic URL/text item providers, publishes through the entitled App Group container, terminates the extension-side object graph, launches the main-app importer, confirms through shared staging, reopens Room, drives deterministic capture acceptance and running/terminal operation fixtures, and renders shared detail. Retain the `.xcresult` plus content-free test report in CI.

The scheme builds and embeds the actual `.appex`; separate tests inspect activation rules and effective entitlements. System share-sheet selection and physical extension memory/timeout behavior are not claimed unless a later real-device/UI automation gate observes them.

## Risks / Trade-offs

- [Item providers can advertise several representations or finish after cancellation] → cap loaders/deadline, deduplicate equivalent values, serialize terminal completion, and ignore late callbacks.
- [Atomic publish/claim can be interrupted at every rename boundary] → same-volume temp/published/processing directories plus stable identity make every state recoverable and independently testable.
- [App Group files are attacker-controlled from another entitled process] → strict filenames, no symlink following, pre-decode size cap, exact schema validation, and no paths from payload data.
- [A shared Keychain entitlement increases extension capability] → no extension credential call site, no credential data in handoff, narrow explicit group, effective-entitlement tests, and a future split requires an ADR/security review rather than silent drift.
- [BackgroundTasks is nondeterministic and quota controlled] → foreground reconciliation is mandatory, queue times remain truth, and evidence labels simulator scheduling as adapter behavior only.
- [Swift/ObjC KMP interop can expose unwieldy graphs] → export a small iOS facade carrying primitives and lifecycle results; keep models and transport types behind KMP.
- [Text share looks supported by the system but cannot reach Platform] → persist/present it truthfully with disabled confirmation and no misleading success.

## Migration Plan

1. Add red parser and App Group public-seam tests, then implement the extension target, activation rules, envelope, and atomic native inbox.
2. Generalize shared staging inputs under red common tests, then wire the iOS graph, stable handoff identity, queue commit, and cleanup callbacks.
3. Add red scheduler/submission/status lifecycle tests, then wire foreground reconciliation, BackgroundTasks, shared operation stores, and scene visibility.
4. Add entitlement/Keychain policy tests, configure checked-in target entitlements and explicit access group, and build/embed the extension.
5. Add and run the synthetic simulator smoke, update ADR/developer/testing/plan/CI evidence, and run the full local gate.
6. Rollback removes the new extension/background registration and app import entry while leaving unimported App Group envelopes intact for a corrected build; no public API or database rollback exists or is needed.
