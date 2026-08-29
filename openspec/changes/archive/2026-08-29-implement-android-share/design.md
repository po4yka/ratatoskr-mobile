## Context

See `proposal.md` for motivation and the four delta specs for observable behavior. ADR-0001 assigns shared Compose presentation, navigation/state, Platform clients, and queue rules to KMP while native Android owns intent/lifecycle parsing, background scheduling, notifications, and platform accessibility. ADR-0004 makes `CaptureQueue` the durable local truth and already defines stable idempotency, claims, retry state, acceptance binding, and monotonic operation projection. ADR-0005 makes `DeviceSessionManager` the only credential/refresh/revocation coordinator.

The pinned Platform contract supports URL submission only: `POST /v1/captures` accepts `SubmitCapture(url, social?)`, requires `Idempotency-Key`, and returns `CaptureAccepted(operation_id, status)`. Notes, tags, collections, files, and arbitrary text have no submission field. Progress is available from `GET /v1/operations`, `GET /v1/operations/{operation_id}`, and an SSE endpoint; the workspace `operation-progress` spec makes Platform's full snapshot authoritative. This change therefore delivers URL/article parity and handles plain text truthfully without inventing a wire shape.

## Goals / Non-Goals

**Goals:**

- Make external Android URL sharing reach a durable queue record in one explicit, reversible user flow.
- Keep native Android code thin: translate OS inputs/events into typed shared state, then let shared Compose/UDF own staging and status presentation.
- Make at-least-once WorkManager invocation converge through the existing queue key and Platform acceptance contract.
- Use generated public contract types and one authorization lifecycle for submission, list, detail, polling, and revocation.
- Produce deterministic fixture and emulator evidence without claiming a live Platform deployment.

**Non-Goals:**

- Plain-text, note, tag, collection, social-mode, or file submission absent from the pinned public contract.
- iOS Share Extension/App Group work, Android content URI/file access, resumable upload, staged-file cleanup, or release notification permission onboarding.
- Always-on SSE/background polling, aggressive connectivity monitoring, cancellation UI, rich result rendering, library/search, or a new local operation cache.
- Public Platform contract changes, live backend acceptance, physical-device evidence, signing, or store publication.

## Decisions

### 1. One native Activity routes explicit intents into shared navigation

`MainActivity` remains the thin application shell and gains a `text/plain` `ACTION_SEND` intent filter plus explicit internal status-detail intent handling. A native parser produces a small sealed intake result containing original bounded text and either one validated URL, unsupported plain text, or a safe rejection. `onCreate` and `onNewIntent` feed the same shared application coordinator; the Compose root selects pairing, staging, status list, or status detail through Navigation 3 state.

The parser accepts a trimmed whole-value URL or exactly one absolute HTTP(S) URL delimited inside bounded shared text. It does not normalize away the original text, follow redirects, read `EXTRA_STREAM`, inspect the clipboard, or guess among multiple links. A pure-text share reaches preview so the user understands what arrived, but confirm remains disabled because the pinned submission API cannot represent it.

Alternatives considered:

- A second `ShareActivity` would isolate exported intent handling, but duplicates composition/session/database bootstrapping and complicates handoff before any file permission lifetime exists.
- Parsing in common code would make unit tests easy but would move Android `Intent`, MIME, `ClipData`, and exported-component trust rules across the accepted native boundary.
- Treating arbitrary shared text as a URL or note would fabricate server capability or silently change user input.

### 2. Shared staging owns UDF state but queue commit is the confirmation boundary

The staging feature exposes immutable `SharedTextPreview`, validation/result state, and actions for confirm, cancel, retry/open-status. Its ViewModel builds a `CaptureRequest` using the paired origin/user scope and `AndroidShareTarget`, calls `CaptureQueue.enqueue`, and only after success invokes a native `SubmissionScheduler` port with no content-bearing input. Recomposition cannot enqueue because confirmation is a serialized action and completed state is idempotent.

No note/tag/collection controls appear. The original Android text is transient presentation state and is not added to the URL request because Platform accepts only the URL. If the activity dies before confirmation nothing was promised; after confirmation the queue survives and the UI reports the persisted local ID/state.

Alternative considered: scheduling first and letting a worker create the record risks losing the only explicit input and violates the durable queue boundary.

### 3. A small Android application container owns long-lived adapters

An `Application`-scoped manual container constructs native secure storage, `DeviceSessionManager`, the private Room queue, Ktor transport adapters, and the WorkManager scheduler. The Activity and worker retrieve the same dependency graph without putting tokens or URLs in `WorkRequest` input, tags, names, logs, or notifications. Tests replace only system-boundary ports (HTTP engine, clock, scheduler, notification sink) and exercise feature coordinators through public actions/state.

Room remains one current schema; this change adds public queue queries for owner-scoped records needing submission/operation refresh but adds no migration. Process recreation rebuilds the container from Keystore/preferences and the existing private database.

Alternative considered: introducing Koin is allowed by the selected stack but adds a production dependency and runtime graph for a bootstrap graph that manual construction can express directly.

### 4. WorkManager is a wake-up mechanism, not queue truth

AndroidX WorkManager runs one unique connected drain chain. Each invocation restores the active device session/capabilities, asks the queue for eligible work in that exact owner scope, submits a bounded number of URL claims, refreshes a bounded number of accepted/tracking operations, and exits. It never receives payloads or secrets through `Data`; the queue and secure store are reopened after process death.

After enqueue, retry classification, acceptance, or a non-terminal refresh, the scheduler computes the next wake-up from persisted queue state and installs connected work with a bounded initial delay. WorkManager's own retry counter does not replace queue attempt count/backoff. On revocation, the identity manager clears authorization, claimed work becomes `AuthRequired`, and the worker succeeds without a retry storm. App start and user re-pairing reconcile unfinished work so a lost OS schedule is recoverable.

Alternatives considered:

- A foreground service is disproportionate for bounded URL JSON and imposes visible ongoing-service policy.
- WorkManager `Result.retry()` as the only backoff source loses the item-3 durable schedule and cannot express per-record eligibility.
- Direct submission from the staging ViewModel makes activity lifetime and current connectivity correctness boundaries.

### 5. Shared Platform adapters separate transport failures from queue outcomes

Dedicated Ktor adapters expose `submitCapture`, `listOperations`, and `readOperation` rather than a generic fetcher. They serialize generated `SubmitCapture` and decode generated acceptance/list/snapshot types with the pinned JSON policy. An authorized executor supplies the current access token, retries one request only after serialized refresh/recovery on `401`, and returns re-pairing when recovery proves revocation.

Submission maps unreachable/timeout to connectivity, `429` and bounded retry hints to rate limiting, `504` and same-request `409` to retryable server uncertainty, `400` to permanent validation, and invalid success bodies to a safe server failure. A successful acceptance must carry the documented `accepted` marker and a valid operation identifier before `recordAccepted`. The original idempotency key and URL are always reused.

Operation list/detail errors stay presentation failures; when a detail belongs to a local queue record, valid snapshots also pass through `CaptureQueue.applySnapshot` so process restarts retain monotonic terminal truth.

Alternative considered: one catch-all network error would make permanent failures retry forever and hide revocation.

### 6. Bounded polling matches SSE-observable behavior for this item

The status repository pages `GET /v1/operations` newest-first and reads detail by validated UUID. A detail coordinator polls non-terminal operations on a modest adaptive interval while its route and app lifecycle are resumed, cancels immediately when not visible, stops on terminal/re-pairing, and caps consecutive failures before requiring explicit retry. Each response is reduced by the existing monotonic projector before display; list fixture order remains Platform order.

SSE is not used in item 4 because a robust mobile event stream needs lifecycle reconnection, last-event identity persistence, and battery measurements not yet present. The public polling endpoint provides equivalent observable progress without pretending the stream is durable. The status repository keeps no second authoritative database beyond local queue projections.

### 7. Notification navigation is explicit, minimal, and fail closed

The native notification adapter creates a low-information capture-status channel and posts generic accepted/terminal text only when Android permission permits. Its immutable explicit `PendingIntent` targets `MainActivity` and carries an internal action plus operation UUID, not a browsable public custom-scheme payload. The Activity validates the action and UUID before passing a route to shared navigation; detail then performs an authorized fetch, so a guessed ID cannot reveal ownership.

Instrumentation verifies invalid routes trigger no fetch and notification content contains no capture URL/title/text. Permission denial is a no-op for notification delivery and never changes queue truth.

Alternative considered: an exported custom URI handler is unnecessary for same-app notifications and increases attack surface; universal/app links remain plan item 9.

### 8. Emulator smoke uses synthetic intent and fixture transport evidence

Android instrumentation launches the exported activity with synthetic `ACTION_SEND text/plain`, confirms the staging button, reopens the private queue, and records a screenshot/log-free test artifact showing the queued state. A local deterministic fixture transport drives acceptance plus running/terminal snapshots for status list/detail rendering and notification navigation. CI runs this on the emulator alongside focused unit/WorkManager tests and publishes the test report/screenshot artifact.

This proves the application, manifest, UI, database, scheduler seam, and fixture contract on an emulator. It does not claim live Platform, physical-device notification policy, OEM background behavior, or provider acceptance.

## Risks / Trade-offs

- [Android share payload formats vary between source apps] → cover whole URL, title-plus-URL, whitespace, missing, multiple, non-HTTP(S), oversized, and unsupported MIME/action cases; reject ambiguity visibly.
- [Accepted operations can block later source-lane work until terminal] → refresh accepted lane heads in the same bounded worker and foreground detail path; retain ADR-0004 ordering rather than silently weakening it.
- [A worker can die at every network/database boundary] → claim leases, persisted idempotency, acceptance convergence, owner-scoped refresh queries, and startup reconciliation make each boundary restartable.
- [Polling consumes battery or rate allowance] → poll only resumed visible detail plus bounded background refresh, use adaptive intervals/failure caps, and stop at terminal/auth boundaries.
- [Notification permission/OEM scheduling is nondeterministic] → keep notifications optional presentation, queue/status authoritative, and separate emulator fixture evidence from physical-device claims.
- [Plain text appears accepted by the OS but cannot be sent] → preview it with a precise contract-unavailable state and create no misleading queue/server success; item 6 or a future public contract change can add positive submission.
- [A new scheduler dependency expands Android maintenance surface] → pin WorkManager in the version catalog, use only stable public APIs, and cover scheduling/restart behavior through AndroidX test support and emulator instrumentation.

## Migration Plan

1. Add pinned WorkManager runtime/test wiring and behavior-free native share/scheduler/notification interfaces so red tests compile at public seams.
2. Implement intent parsing, staging, queue confirmation, and offline state in vertical red-green slices.
3. Add typed capture/operation adapters, authorized execution, WorkManager drain/reconciliation, and queue refresh queries in vertical red-green slices.
4. Add shared operation list/detail navigation, lifecycle-aware polling, Android notification routing, and fixture-driven instrumented smoke.
5. Add ADR-0002, documentation/gate parity, run the full local gate, sync specs, archive, integrate, push, and verify hosted CI for the exact commit.

There is no database migration or server rollout. Rollback is the previous application commit; development installs recreate the current local schema, and Platform sees at most idempotent requests under already-persisted keys.
