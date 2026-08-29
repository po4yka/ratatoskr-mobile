# ADR-0003: iOS Share Extension and App Group handoff

- Status: Accepted
- Date: 2026-08-29

## Context

An iOS share must survive the extension's short lifetime, remain user-confirmed, enter the existing
durable queue exactly once, and expose the same Platform operation truth as Android. The extension
sandbox, App Group, Keychain access groups, `BGTaskScheduler`, and scene phase are native iOS
authority; staging UDF, queue transitions, authorization, submission, and operation presentation
remain shared KMP responsibilities under ADR-0001.

The pinned public contract accepts URL captures only. Plain text may be preserved for preview but
cannot be submitted as a fabricated Platform field.

## Options considered

1. Load Compose, Room, Ktor, and device identity inside the Share Extension.
2. Submit directly from the extension and use the main app only for status.
3. Parse and atomically stage in a minimal native extension, then let the main app confirm and
   enqueue through the shared graph.

Option 3 keeps the extension fast and makes the Room queue the only local submission truth. The
other options couple correctness to an expiring sandbox or create a second network/queue owner.

## Decision

The native extension accepts one bounded URL or plain-text representation, deduplicates equivalent
representations, preserves the original text, and rejects ambiguous, unsupported, unreadable, or
oversized input. It publishes one schema-1 JSON envelope beneath App Group
`group.com.ratatoskr.mobile` using a UUID filename and same-volume atomic rename. The envelope
contains no identity, capability, operation, or credential fields.

The main app atomically renames one published envelope into its processing directory before
presentation. Shared Compose requires explicit confirmation. The handoff UUID becomes the stable
`ios-share-<uuid>` idempotency key and the envelope timestamp becomes the immutable capture time.
Commit or cancel removes only the claimed envelope; queue failure retains it for recovery.

One long-lived iOS application graph owns the private Room queue, Keychain-backed device session,
Ktor Platform adapters, bounded submission/operation refresh, and shared operation stores. Scene
activation always imports and reconciles. One reviewed `BGAppRefreshTask` identifier provides an
opportunistic wake-up; persisted queue eligibility and claim leases remain authoritative.

The app and extension have exactly one matching App Group and one matching Keychain access group.
The extension carries the requested signing capability but its parse-stage-complete sources never
call Security. Credentials retain the device-only, non-synchronizing policy from ADR-0005.

## Consequences

- The extension has no KMP framework or network dependency and completes only after durable publish.
- App Group files are a hostile IPC boundary and are size, schema, filename, path, and symlink checked.
- Foreground reconciliation is reliable local behavior; background delivery remains iOS-controlled
  and opportunistic.
- URL staging/submission and operation UI are shared Compose; native Swift owns extension, inbox,
  entitlements, scene phase, and background expiration.
- Plain-text-only shares are visible but non-submittable until the first-version Platform contract
  supports them.

## Compatibility and schema impact

This adds no API version, migration, or alternate queue. It uses the existing current Room schema
and pinned first-version capture/operation contracts.

## Validation

Hosted simulator tests cover parser and deadline behavior, atomic envelope integrity, single-claim
recovery, stable queue identity, foreground/background reconciliation, scheduler expiration,
explicit Keychain group isolation, shared status fixtures, and an end-to-end synthetic handoff with
Room close/reopen. CI retains `ios-share-test-results`; effective simulator entitlements are
inspected for the app and embedded `.appex`.

This is simulator/fixture evidence. It is not live Platform acceptance, deterministic OS background
delivery, physical-device extension budget, release signing, App Store, provider, or file-upload
proof.

## Follow-up

Plan item 8 owns security-scoped files, staged-file retention, and background upload. Plan item 10
adds physical-device lifecycle and release-signing evidence.
