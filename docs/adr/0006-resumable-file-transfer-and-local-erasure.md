# ADR-0006: Resumable file transfer and replay-safe local erasure

- Status: Accepted
- Date: 2026-08-30

## Context

Ratatoskr must retain an explicitly shared file across process death, upload it without loading the
whole file, obey mobile background limits, and remove every account-scoped local copy after proven
device revocation or confirmed clear-data. The pinned Platform OpenAPI does not yet publish the
authorized receipt-session binding. The canonical `ratatoskr-contracts` transfer schemas describe
the digest-first receiver protocol, but they do not authorize the mobile app to call an internal
receiver directly.

## Decision

Shared KMP owns generated transfer DTOs, declaration/chunk arithmetic, resumable state, queue and
receipt transactions, retention/scheduling decisions, erasure coordination, usage UDF, and shared
Compose presentation. A receiver receipt is not Platform acceptance: staged bytes remain referenced
until the capture operation is durably bound.

Native code owns external file authority, protected staging, streamed hashing/copying, filesystem
inventory/deletion, schedulers, notifications, Keystore/Keychain, App Group coordination, and
startup erase barriers. Android copies one grant-bounded `content://` item to `noBackupFilesDir`.
The iOS extension only parses, publishes to its protected App Group, and completes; the main app
imports into private Application Support before queue commit.

Limits are 100 MiB per file and 512 MiB/64 artifacts. Temporary/orphan files become eligible after
24 hours and terminal failures after seven days. Referenced, queued, uploading, uncertain, or
receipt-pending bytes are never capacity eviction candidates; admission fails instead.

Android file work requires connected, battery-not-low, and storage-not-low constraints and carries
only an opaque queue key plus erase generation. iOS uses `BGProcessingTaskRequest`, network,
external power above 32 MiB, Low Power Mode deferral, checkpoint-preserving expiration, and
foreground repair. Durable Room state remains authoritative.

Proven revocation and confirmed clear-data atomically write an erase marker/generation, cancel work
and notifications, clear secure credentials, close and remove Room/WAL/SHM, remove only registered
app-owned staging/App Group/cache/preference roots, inventory residue, and delete the marker last.
Startup resumes a marker before opening queue or identity stores. Local sign-out stays
credential-only. A completed wipe leaves the current process fail-closed with its queue handle
closed and a restart-required message; only a fresh process may open empty stores and pair again.

Production file submission is `IntegrationPending` and sends no bytes until a workspace change pins
a public Platform capability and authorized receipt binding. Contract-fixture receiver tests prove
the protocol, not a live service.

## Consequences

- Shared behavior and UI stay aligned while OS authority and lifecycle failures remain explicit.
- Interrupted copy, upload, cleanup, and erasure converge from durable truth.
- Rollback cannot restore intentionally erased local data.
- Emulator/simulator evidence does not establish physical-device background delivery,
  data-protection-at-rest, live Platform integration, signing, or store publication.
