# Mobile interfaces

## Android

The implemented Share Target exports the thin Android Activity for `ACTION_SEND text/plain`. The
native parser accepts exactly one bounded `http`/`https` URL, preserves original text for shared
Compose preview, and refuses plain text, multiple URLs, hostile schemes, oversized input, and other
actions. `ACTION_SEND_MULTIPLE`, content URIs, persistable grants, and files are not implemented.

Confirmation persists through shared `CaptureQueue` before the native WorkManager adapter receives
a content-free unique-work request. Confirmation and worker drain fail closed unless the current
paired session has a fresh `content.submit` capability. The worker consumes authorization from
Keystore-backed storage and derives payload, idempotency, retry time, and operation binding from the
queue. The Activity is not a source of background truth.

Generic notifications contain no capture content. Their immutable explicit intent contains only a
validated operation UUID and routes into shared operation detail; the authenticated Platform read
decides whether that operation is visible to the current device session.

The exported Activity also accepts only `ratatoskr://library/...` links matched by the shared exact
route table. Native intake forwards the original opaque route string; it does not decode provider
content or credentials.

## iOS

The Share Extension owns bounded `NSExtensionItem` parsing and atomic App Group handoff. The main
SwiftUI shell forwards `ratatoskr` custom-scheme URLs into the same shared route table. Universal
links are not configured. Keychain and App Group access groups remain native entitlements.

## Shared/Platform

Generated API models currently back device pair/refresh/revoke, capabilities, URL capture submit,
operation list/detail, recent library search, and read-state replacement. The shared submission adapter sends the persisted idempotency key and
one generated `SubmitCapture` body, follows no redirects, permits one serialized session
refresh/recovery, and stores `202 Accepted` operation identity without declaring success.

Operation detail polls at a bounded interval only while visible, stops at terminal state, handles
duplicate/out-of-order snapshots monotonically, and exposes offline/reauth/not-owned states without
unsafe server details. Favorite, note, collection/tag, full reader, social-reader, and AI-archive
reader endpoints are absent from the public contract. Their shared UI is therefore an explicitly
unsynchronized, reset-on-restart contract fixture that performs no Platform call.

## Rules

Inbound content is type/size/count/time bounded; future file access must be copied before source
permission expires. KMP owns Compose/UDF presentation, pure models, queue policy, and public API
adapters, while native adapters own lifecycle, secure storage, WorkManager, and notifications. Deep
links are allowlisted and do not carry secrets. Errors distinguish invalid input, offline,
auth/revoked, policy, throttling, server, not-owned, partial, and terminal states.
