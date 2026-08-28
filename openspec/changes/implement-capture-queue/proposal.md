## Why

Ratatoskr Mobile cannot make explicit capture durable until the shared layer has a persisted queue whose identity, ordering, retry, and server-resolution rules survive process death. This plan item establishes that core before either native share surface or network submission worker depends on it.

## What Changes

- Add shared KMP capture models for URL, text note, and app-owned staged-file references, plus conservative operation-status projections.
- Add a transactional offline queue with stable local IDs and idempotency keys, per-source FIFO eligibility, restart-safe claims, bounded exponential backoff with deterministic test seams, and explicit terminal states.
- Define conflict handling for repeated local enqueue and for server acceptance of a previously submitted idempotency key, including a fail-closed state if one key is ever associated with different operation IDs.
- Persist the queue in Room KMP from one current schema definition with native database construction for Android and iOS; no migrations or compatibility schema are introduced.
- Extend the shared Android and iOS test/build gates so current-schema creation and queue behavior run on both configured KMP targets.
- Record ADR-0004 for durable local queue storage, update architecture, data-model, testing, and development documentation, and mark implementation-plan item 3 complete only after the full gate is observed.

## Capabilities

### New Capabilities

- `capture-queue`: Shared capture models, durable queue transitions, restart recovery, idempotency, ordering, retry policy, server-resolution conflicts, and operation projections.

### Modified Capabilities

- `mobile-project-bootstrap`: Product CI and the documented gate must execute the new shared current-schema and capture-queue tests on Android and iOS targets.

## Impact

- Affects the shared KMP capture, queue, operation, and persistence packages; Room/KSP build configuration; Android/iOS database-driver factories; shared tests; CI; and repository documentation.
- Uses the existing pinned Platform OpenAPI models for operation status and acceptance boundaries without changing any public Platform contract or sibling repository.
- Adds local, user-content-bearing storage. Payloads remain minimized and scoped to the configured Ratatoskr instance/account; native share intake, staging, submission transport, UI, and background schedulers remain out of scope.
