## Why

Ratatoskr has an Android share path and a durable cross-platform queue, but iOS users still cannot hand an explicit URL or text share through the extension sandbox into the main application, submit it after the extension exits, or follow the resulting Platform operation. Plan item 5 closes that lifecycle gap while keeping the Share Extension short-lived and the shared queue authoritative.

## What Changes

- Add a native iOS Share Extension that accepts bounded public URL and plain-text item-provider representations, preserves the original input, rejects ambiguous or unsupported payloads visibly, writes one versioned staging envelope atomically into an App Group container, and completes quickly without loading Compose, the queue database, device credentials, or network clients.
- Add a main-app App Group inbox importer that validates staged filenames and envelope contents, atomically claims each item, converts supported URL captures into the existing shared staging/queue flow, removes only successfully committed app-owned handoff files, and safely retries interrupted imports after process death.
- Route imported URL captures through the existing shared Compose confirmation, durable queue, device-session authorization, generated `POST /v1/captures` client, and operation list/detail surfaces; represent text-only input truthfully as preview-only because the pinned Platform contract has no text or note submission field.
- Add bounded native iOS background submission wake-up and foreground reconciliation without making the extension lifetime or scheduler state authoritative.
- Configure App Group and Keychain access-group entitlements consistently for the application, extension, and simulator test hosts. The extension does not read device credentials in this flow; shared Keychain membership exists only to keep target entitlement identity correct and is covered by cross-target policy tests.
- Add Swift/XCTest parser, App Group handoff, lifecycle, entitlement, main-app submission/status fixture, and simulator smoke coverage, then extend the documented and hosted full gate.
- Record the iOS lifecycle/security boundary in an ADR and mark implementation-plan item 5 complete only after the full gate and simulator smoke are observed.

## Capabilities

### New Capabilities

- `ios-share-capture`: iOS URL/text Share Extension intake, bounded App Group staging, crash-safe main-app import, explicit confirmation, native background wake-up, and sandbox/credential boundaries.

### Modified Capabilities

- `capture-queue`: Generalize durable native scheduling and restart recovery requirements to the iOS main-app importer and submission scheduler while preserving queue authority and idempotency.
- `mobile-project-bootstrap`: Product CI and the documented gate must build, test, and smoke the iOS application plus embedded Share Extension with App Group and Keychain entitlements.

## Impact

- Affects the Xcode project and scheme, app and extension entitlements/property lists, native Swift extension/import/scheduling adapters, the iOS-to-shared framework entry seam, shared staging orchestration, XCTest/simulator fixtures, CI, ADRs, and developer documentation.
- Uses Apple `UniformTypeIdentifiers`, `NSItemProvider`, coordinated App Group file operations, and reviewed background APIs already provided by the SDK; it adds no provider SDK, scraper, upload dependency, or public Platform contract.
- Consumes the existing pinned generated capture and operation contracts and the existing shared Compose, queue, device-session, submission, and status modules. No sibling repository, public API, database migration, file upload, or provider behavior changes.
- iOS app/Share Extension, App Group, shared KMP staging/queue/auth/API/status, background submission, Keychain entitlements, and simulator evidence are in scope. Android behavior, files, notes/tags/collections, live Platform acceptance, physical-device background policy, signing, and App Store publication are not.
