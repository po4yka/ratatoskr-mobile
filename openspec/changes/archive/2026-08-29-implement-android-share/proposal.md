## Why

Ratatoskr can already persist explicit captures, but Android users still cannot share a URL or text from another app, confirm it, submit it safely after connectivity returns, or follow the resulting Platform operation. Plan item 4 closes that first end-to-end mobile capture path while preserving the native Android lifecycle boundary and the shared queue as local business truth.

## What Changes

- Register a native Android `ACTION_SEND` `text/plain` Share Target and defensively parse one bounded HTTP(S) URL or bounded text payload without accepting files or passive input.
- Add shared Compose staging state and presentation for the original shared content and explicit confirmation; omit notes, tags, and collections because the pinned `SubmitCapture` contract supports only a URL and optional social provenance.
- Persist confirmed URL captures through the existing item-3 `CaptureQueue` before any network attempt and expose queued/offline/submitting/accepted/error state truthfully.
- Add an Android WorkManager submitter that uses the existing device-session authorization lifecycle, the stored idempotency key, and the pinned `POST /v1/captures` contract, then binds the authoritative operation ID or records a classified retry/permanent/auth outcome.
- Add typed Platform operation list/detail clients and shared status state/presentation backed by bounded lifecycle-aware polling of `GET /v1/operations` and `GET /v1/operations/{operation_id}`; do not invent a second progress contract or require an always-open SSE connection.
- Add privacy-preserving Android notifications for accepted/terminal work when permission is available, with a validated app deep link that opens only the corresponding status detail.
- Add unit, shared fixture, Android instrumentation, WorkManager, navigation/deep-link, and emulator smoke coverage, and extend the documented/hosted product gate.
- Record ADR-0002 for the Android Share Target lifecycle, staging, scheduling, and notification/deep-link boundary, and mark implementation-plan item 4 complete only after the full gate and emulator smoke are observed.

## Capabilities

### New Capabilities

- `android-share-capture`: Android `ACTION_SEND` intake, bounded staging and explicit confirmation, durable queue handoff, native background submission, offline/auth outcome transparency, and validated notification deep links.
- `mobile-operation-status`: Contract-backed operation list/detail projection and lifecycle-aware bounded polling for shared app presentation.

### Modified Capabilities

- `capture-queue`: Connect claimed URL records to authenticated Platform submission and operation refresh while preserving persisted idempotency, failure classification, monotonic projection, and source ordering.
- `mobile-project-bootstrap`: Product CI and the documented gate must compile and exercise the Android Share Target, WorkManager submission, Compose/status fixture, deep-link, and emulator smoke suites in addition to the existing shared and iOS gates.

## Impact

- Affects the Android manifest and native entrypoint, Android lifecycle/background/notification adapters, shared Compose navigation and feature state, shared Platform client adapters, the existing queue integration seam, Android resources/tests, CI, ADRs, and developer documentation.
- Adds AndroidX WorkManager and its test support as the reviewed OS scheduler; it adds no provider SDK, scraper, file-access dependency, or server-side behavior.
- Consumes the pinned public Platform `SubmitCapture`, `CaptureAccepted`, `OperationList`, `OperationSummary`, and `OperationSnapshot` types and the workspace `operation-progress` specification without changing a public API or sibling repository.
- Android app/share target, shared KMP, queue, auth, API, operation status, notifications, and deep-link surfaces are in scope. iOS, file/content-URI uploads, Share Extension work, collection/tag wire changes, provider routing, and live Platform acceptance are not.
