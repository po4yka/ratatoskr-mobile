# ADR-0002: Android Share Target lifecycle and operation status

- Status: Accepted
- Date: 2026-08-29

## Context

Ratatoskr must accept an explicit URL shared from another Android application, make the capture
durable before the transient share surface finishes, submit it when connectivity permits, and show
the authoritative Platform operation result. The Android Activity, intent grants, WorkManager,
notifications, and process lifecycle have platform semantics that common code cannot own. At the
same time, the product has selected shared Compose presentation and UDF state inside thin native
shells.

The pinned Platform public API currently accepts URL captures. It does not expose note, tag, plain
text, or file-upload fields, so the client must not invent those capabilities.

## Options considered

1. Native Android Activity and native Android UI for the whole flow.
2. Native Android lifecycle adapters with shared Compose staging/status UI and shared domain rules.
3. A shared abstraction that owns the Android Share Target and background lifecycle.
4. Submit directly from the transient share Activity without a durable local handoff.

Option 2 preserves Android platform authority while keeping identical product presentation and
state rules in the shared module. Option 1 would duplicate presentation and operation behavior.
Option 3 would hide lifecycle and security constraints. Option 4 loses captures across offline and
process-death boundaries.

## Decision

The manifest exports only `MainActivity` for `ACTION_SEND` with `text/plain`. The native Android
Activity parses each hostile intent, accepts exactly one bounded `http` or `https` URL, preserves
the original shared text separately for preview, and rejects unsupported or ambiguous input. This
item does not claim `ACTION_SEND_MULTIPLE`, file, content-URI, note, or tag support.

The Android Activity remains a thin shell. It hands accepted intake to shared Compose staging and
UDF state. Confirmation writes the URL capture and stable idempotency key through `CaptureQueue`
before scheduling any network work. A missing paired owner leaves confirmation disabled rather
than creating unowned data. The control also remains disabled until the current session has a
fresh `content.submit` capability and reacts when capability discovery recovers. Recomposition and
repeated taps cannot submit twice.

A native WorkManager adapter schedules content-free unique work under a connected-network
constraint and derives all work from durable queue state. The worker performs a bounded drain,
uses the persisted idempotency key against the Platform public API, stores the returned operation
UUID, applies bounded retry timing, and treats authentication revocation as a graceful pause rather
than an OS retry storm. The worker never treats upload acceptance as terminal success.

Shared operation list/detail stores consume the generated public operation contracts. Foreground
detail uses bounded polling only while visible, stops at terminal state, and caps consecutive
failures. The native Activity lifecycle explicitly suspends the active store outside `RESUMED`,
even when its Compose tree remains allocated. Background refresh is coalesced through WorkManager.
Duplicate or out-of-order snapshots cannot regress the persisted projection.

The native notification adapter posts generic status text only. Its immutable explicit deep link
carries one validated operation UUID and the authenticated repository re-fetches that operation;
no capture URL, title, note, credential, or result body enters the notification or deep link.

## Boundary consequences

- Android owns the manifest, `Intent` parsing, Activity lifecycle, WorkManager scheduling,
  notification permission/channel/PendingIntent behavior, and process startup.
- Shared KMP owns shared Compose staging and status presentation, UDF state, generated Platform
  models, capture submission coordination, queue transitions, polling policy, and monotonic
  operation projection.
- Room `CaptureQueue` remains the local business truth. WorkManager is only a delivery mechanism.
- iOS share lifecycle and Share Extension behavior remain unchanged and belong to plan item 5.
- File staging/upload remains out of scope until plan item 8.

## Security and privacy consequences

- Only explicit `ACTION_SEND` input is consumed; no clipboard, history, or passive observation is
  introduced.
- Shared text is bounded and never written to logs, notification content, work input, or analytics.
- Bearer credentials remain behind Android Keystore-backed session storage and are attached only
  to canonical HTTPS Platform requests.
- Redirects are disabled. Transport, authentication, validation, policy, throttling, and server
  failures remain distinct safe classes.
- The exported entrypoint has the smallest intent filter and does not accept arbitrary internal
  deep-link actions from other applications.

## Compatibility and schema impact

This uses the existing first-version generated capture and operation contracts and the existing one
current Room schema. No API version, migration, compatibility route, or sibling-repository path is
added. Optional note/tags and files remain absent until the pinned contract exposes them and their
own plan items are implemented.

## Validation

- Parser instrumentation covers valid URL placement, unsupported plain text, multiple URLs,
  malformed schemes, oversized payloads, and non-share actions.
- Shared and Compose tests cover staging state, one-shot confirmation, queue submission,
  reactive `content.submit` availability, authenticated rotation/revocation, generated operation
  fixtures, stale offline context, and actionable status states.
- WorkManager instrumentation covers connected constraints, durable timing, Room close/reopen, and
  revocation without retry storms.
- Notification instrumentation covers content minimization, immutable explicit intents, denied
  permission, and invalid operation identifiers.
- A deterministic API 35 emulator smoke runs URL share through durable queue acceptance, terminal
  projection, database reopen, and operation detail. CI retains its synthetic test report as the
  `android-share-test-reports` artifact.

## Follow-up

Plan item 5 adds the iOS Share Extension. Plan item 8 adds bounded file staging/upload and cleanup.
A real Platform workspace profile and physical-device lifecycle run remain separate acceptance
boundaries and are not implied by the deterministic emulator fixture.
