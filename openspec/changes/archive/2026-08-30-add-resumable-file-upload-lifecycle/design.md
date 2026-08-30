## Context

See `proposal.md` for motivation and the delta specs for observable behavior. The current shared
queue already persists `CapturePayload.StagedFile`, but only as an opaque identifier/size/type; it
has no byte store, transfer journal, receipt, cleanup ledger, or erase lifecycle. Android parses
only `ACTION_SEND text/plain` and schedules one connected WorkManager request. iOS moves JSON
envelopes through the App Group and uses `BGAppRefreshTask` as an opportunistic queue wake-up.
`DeviceSessionManager` currently clears only credentials/capabilities after proven revocation.

The fleet source of truth is split deliberately:

- `ratatoskr-contracts` contains transport-neutral digest-first blob-transfer schemas and fixtures;
- the workspace `blob-references` spec says the receiving service owns stored bytes and Platform is
  not a blob service;
- the pinned Platform OpenAPI contains no public mobile upload-session HTTP binding or capability.

The design therefore implements real local staging, persistence, resumable protocol behavior, and
native constraints against the canonical schemas and a deterministic in-process receiver. The
shipping composition supplies an unavailable transport and labels file delivery integration
pending. It will not infer a route from internal service code.

## Goals / Non-Goals

**Goals:**

- make one explicitly shared supported file safe and durable before external access ends;
- keep transfer identity/status/checkpoints in the same owner-scoped Room truth as the capture;
- resume from receiver truth after interruption without whole-file buffering or duplicate effects;
- bound staged disk and show useful content-free usage/retention state in shared Compose;
- map one deterministic shared work policy into real Android/iOS scheduler constraints;
- turn proven revocation and explicit confirmed clear-data into a crash-recoverable complete wipe;
- pin contract-fixture evidence and keep the absent live Platform integration unmistakable.

**Non-Goals:**

- inventing or calling an internal receiving-service endpoint;
- changing the fleet blob-transfer or BlobRef contracts in this repository;
- running upload/finalize inside the iOS Share Extension;
- multi-file shares, videos, archives, active documents, HTML, or arbitrary MIME passthrough;
- provider extraction, analysis, remote blob deletion, server archive deletion, or local previews
  that parse active file content;
- database migrations, version negotiation, background-delivery guarantees, physical-device power
  or data-protection proof, release signing, or store publication.

## Decisions

### 1. Pin the transport-neutral contract; do not invent the missing HTTP binding

Add `contracts/blob-transfer/ratatoskr-contracts.lock.json` covering the six canonical schemas and
their valid fixtures at one producer revision. A narrow Gradle generator reads that reviewed JSON
Schema subset and emits Kotlin serialization models under
`com.ratatoskr.mobile.transfer.generated`; the committed output is compared byte-for-byte by
`tooling/contracts/check-blob-transfer.sh`. The mutation test changes both schema content and a
generated output in temporary copies and must fail each independently.

`BlobReceiptTransport` is a shared behavioral seam with `open`, `status`, `putChunk`, and `finalize`.
Common tests use a deterministic contract-fixture receiver that enforces chunk arithmetic,
idempotent replay, conflicts, expiry, completion verification, and locally classified safe failures.
The producer currently publishes no transfer-failure JSON Schema, so mobile does not invent one.
Production composition returns
`IntegrationPending` before any request because neither the current capability document nor OpenAPI
defines an authorized route. A later cross-repository change must pin that binding before adding a
Ktor implementation.

Alternatives rejected:

- reuse `/v1/ai-archives/{provider}/content`: it is domain-specific and not a file-capture receipt;
- route to a known internal receiver address: it breaks the Platform-only boundary and deployment
  independence;
- copy Rust source or depend on a sibling checkout: it is not a published mobile contract and would
  make clean CI non-reproducible;
- hand-maintain Kotlin wire DTOs: it weakens drift evidence.

### 2. Keep byte authority native and transfer rules shared

Shared KMP owns `StagedArtifact`, `UploadDeclaration`, `UploadCheckpoint`, `UploadProjection`,
`StorageUsage`, retention/scheduling decisions, and `ResumableUploadCoordinator`. Native
`StagedArtifactStore` adapters own grants, security-scoped access, protected directories, atomic
copy/move, streaming SHA-256, content evidence, chunk reads, deletion, and inventory. The public
seam accepts opaque artifact IDs and bounded byte ranges; shared code never receives an external
URI/path and never loads an entire file.

Android stages from the current `content://` grant into `noBackupFilesDir/ratatoskr-staging`, using
an opaque UUID filename, `.partial` then fsync/rename, `MessageDigest`, and bounded reads. The
manifest exposes only reviewed MIME types and continues to reject `ACTION_SEND_MULTIPLE`.

iOS loads exactly one supported `NSItemProvider` representation. The extension streams it to a
protected opaque artifact next to its atomic envelope in the App Group. The main app revalidates
the envelope/path/digest and copies it transactionally into Application Support staging before
queue commit; restart reconciliation uses the handoff ID to converge if either copy or commit was
uncertain. Security-scoped access ends immediately after extension staging. The extension never
opens Room, Keychain, or the transport.

Alternatives rejected:

- persist external grants/bookmarks: their authority/lifetime is not reliable enough for an offline
  queue and keeps access broader than needed;
- implement filesystem access in common code: it would hide platform protection and lifecycle
  failures behind false symmetry;
- compute the digest after enqueue: the queue could then claim durability for incomplete or changed
  bytes.

### 3. Extend the one current Room schema with artifact and transfer aggregates

Edit `QueueDatabase` version 1 in place and add:

- `staged_artifact`: opaque ID, sanitized display name, size/type/digest, created/terminal/expiry
  times, reference/retention state, and last verified file generation;
- `upload_transfer`: one-to-one capture local ID, declaration, opaque receiver token and expiry,
  transfer state, attempts/eligibility/lease, verified receipt JSON, and safe failure class.

Foreign keys/unique indices enforce one artifact per staged-file record and one transfer per capture.
No path, token, filename, or user content appears in scheduler metadata or diagnostics. The queue
and transfer transaction APIs commit identity, checkpoint, receipt, and Platform binding in the
order required by the specs. Database builders still create only this current schema; tests delete
and recreate rather than migrate.

The coordinator obtains a finite owner-scoped lease, verifies current file metadata/digest, and:

1. opens a session when no unexpired token exists;
2. always asks `status` before sending after restart/uncertainty;
3. derives exact chunk count/length from declaration and sends missing indices in order;
4. checkpoints each acknowledged receiver projection transactionally;
5. re-queries status on uncertain chunk/finalize outcomes;
6. finalizes only with no missing indices and verifies the returned BlobRef facts;
7. retains receipt and bytes until Platform acceptance is durable;
8. starts a new receiver session on expiry without changing capture identity.

One active transfer per owner is the initial concurrency limit. This makes power/memory behavior
predictable and preserves per-source queue ordering. The queue's existing retry/backoff remains the
source for connectivity/server timing; protocol conflicts, integrity, size, policy, and local-file
failures are permanent until explicit user action.

### 4. Retention uses admission control plus reference-safe cleanup

`ArtifactRetentionPolicy` is a pure shared decision over a native inventory plus durable references.
Defaults are 100 MiB per file, 512 MiB/64 published artifacts total, 24 hours for incomplete temp
copies, and seven days for terminal failed artifacts. Queued, leased, uncertain, receipt-pending, or
otherwise referenced artifacts are never automatic eviction candidates. Accepted/cancelled files
become immediately reclaimable once the transaction proves no receipt/capture needs their bytes.

Cleanup operates only on opaque names inside reviewed roots, never follows symlinks, and reconciles
ledger/file disagreements conservatively:

- referenced ledger + missing bytes becomes visible local-integrity failure;
- unreferenced published bytes become orphan candidates after the safety window;
- deleted bytes + stale unreferenced ledger converges by removing the ledger row;
- deletion failure stays visible/retryable and continues counting against usage.

Shared Compose adds a compact local-storage section showing counts/bytes by category, limit, oldest
visible expiry, cleanup action, and a destructive clear-data entry. It never shows raw paths or
diagnostic content. Admission may run cleanup first, then refuses the new item if the hard bound
still cannot be met; it never silently evicts unfinished content.

Alternatives rejected:

- LRU eviction across all staged files: it can destroy the only copy of unfinished user content;
- an unbounded queue with best-effort periodic cleanup: it permits disk exhaustion;
- keep every uploaded local file until manual deletion: it defeats bounded-by-default retention.

### 5. One shared scheduling projection maps to native policy

`TransferSchedulingPolicy` accepts durable eligibility, size, integration availability, network,
battery/Low Power Mode, storage, and foreground/background context and returns `RunNow`, `Defer`
with reason/next time, or `IntegrationPending`. It does not increment attempts; only a claimed
transport attempt can do that.

Android uses distinct unique WorkManager work for capture/status and file transfer. File work uses
`CONNECTED`, `requiresBatteryNotLow`, and `requiresStorageNotLow`, passes only the opaque owner/work
key, and re-reads Room on start. Cancellation/duplicate work converges through the lease and erase
generation.

iOS replaces the file wake-up path with `BGProcessingTaskRequest` configured for network;
`requiresExternalPower` is true above 32 MiB. Low Power Mode defers background drain before lease
claim. The existing foreground activation repair remains, and task expiration cancels the
coroutine/native stream promptly. The scheduler is still only a wake-up mechanism: durable Room
eligibility and leases are authoritative.

Alternatives rejected:

- encode chunks/session state into WorkManager/BGTask payloads: scheduler state is lossy and leaks
  sensitive transfer metadata;
- require unmetered network by default: that policy was not requested or exposed by product
  settings;
- claim iOS guaranteed delivery: BackgroundTasks timing remains operating-system controlled.

### 6. Erasure is a native-rooted, replayable barrier shared with identity

Add `LocalDataErasureCoordinator` at application composition and inject its `onProvenRevocation`
callback into `DeviceSessionManager`. A non-sensitive marker in the native protected application
root contains only a random erase generation and reason. It is atomically written before the app
publishes erasing state. Startup checks it before opening Keychain-backed sessions, Room, App Group
inbox, repositories, or schedulers.

For either proven revocation or confirmed clear-data, the coordinator:

1. publishes an erase generation that makes stale callbacks fail closed;
2. cancels native work, background tasks, active transfers, and notifications;
3. clears authorization/capabilities in memory and deletes Keychain/Keystore credential storage;
4. closes Room, then deletes the database, WAL, SHM, transfer journals, staging/temp roots, App Group
   inbox/processing/rejected/artifacts, app caches, DataStore/UserDefaults and other local feature
   preferences;
5. inventories every registered participant and retries any residue;
6. removes the erase marker last and publishes the empty unpaired state.

Every participant is idempotent and declares the exact app-owned roots/keys it owns. A worker must
compare its captured erase generation before any callback writes. Failure leaves the marker and an
erasing/error state; next launch resumes before other work. Remote revocation is already server
proof and therefore starts automatically. User clear-data uses a one-shot confirmation model that
includes queued/artifact counts and bytes; cancel is a no-op. Local sign-out keeps its existing
credential-only semantics.

Alternatives rejected:

- delete only credentials on revoke: it leaves sensitive account content accessible to a revoked
  installation and contradicts this item;
- best-effort independent clears without a marker: process death can expose a half-wiped signed-out
  app and stale callbacks can recreate data;
- ask Platform to delete server content: clear-data is explicitly local and mobile does not own
  remote archives/blobs.

### 7. Test vertical slices at public seams

The confirmed TDD seams are:

- `ResumableUploadCoordinator` + `BlobReceiptTransport` for interruption/status/finalization;
- `ArtifactRetentionPolicy` + `StagedArtifactStore` inventory for bounds/cleanup/usage;
- `TransferSchedulingPolicy` plus native WorkManager/BGTask boundary mapping for constraints;
- `LocalDataErasureCoordinator` plus registered participant inventories for clear completeness;
- Android intent/staging and iOS parser/App Group handoff for native file authority.

Each behavior task is a red/green pair. Common tests use synthetic fixed bytes and contract fixtures;
Android instrumentation uses a synthetic `content://` provider; iOS XCTest uses a synthetic
`NSItemProvider` and temporary App Group-shaped container. Erasure tests seed every registered
resource, interrupt midway, restart, and assert public stores/surfaces are empty rather than merely
checking delete calls. Emulator/simulator smoke remains distinct from physical-device or live
service proof.

## Risks / Trade-offs

- [No public upload HTTP binding] -> ship honest staging/protocol/harness behavior with production
  `IntegrationPending`; require a later workspace contract/pin before Ktor transport is enabled.
- [Remote revocation now destroys unsent captures] -> this is the explicit requested security
  policy; publish erase intent first, stop work, and make the resulting empty state unmistakable.
- [Cross-store wipe cannot be one filesystem transaction] -> use a durable marker, generation
  fencing, idempotent participants, post-wipe inventory, and marker-last completion.
- [Digesting/copying 100 MiB costs time and energy] -> stream with fixed buffers off UI threads,
  enforce one active transfer, expose progress, and apply battery/background constraints.
- [MIME evidence is imperfect] -> accept a narrow allowlist, validate magic bytes where stable,
  refuse disagreement, never execute or render active content, and let the owning receiver perform
  authoritative validation.
- [App Group/app-container copy temporarily doubles bytes] -> reserve capacity before copying,
  account for both locations during handoff, and delete the source only after durable convergence.
- [Automatic seven-day failed-file expiry surprises users] -> expose category/expiry/usage in the
  storage surface and never apply it to queued, uncertain, or receipt-pending work.
- [Schema changes invalidate developer data] -> development status explicitly requires editing
  version 1 in place; test stores are recreated and no migration is added.

## Migration Plan

1. Pin/generate the canonical transfer schemas and make mutation/drift tests green.
2. Edit Room version 1 in place, then land shared transfer, retention, scheduling, and erasure
   vertical slices with reopen/restart tests.
3. Add native protected staging and native constraint/erasure participants with emulator/simulator
   tests; default shipping transport remains `IntegrationPending`.
4. Expose file staging, usage, cleanup, and clear-data confirmation in shared Compose and wire thin
   shells.
5. Update ADRs, README/DEVELOPMENT, CI/gate parity, run the full gate, sync/archive OpenSpec, commit,
   fast-forward `main`, push, and verify exact-SHA hosted checks before cleanup.

Rollback removes the new client capability and recreates the single development schema. It cannot
restore bytes intentionally erased by revoke/clear-data or undo an already issued receiver receipt.
No server rollback or database migration exists in this repository.

## Open Questions

- Which public Platform capability name and authenticated receipt route will bind a stored BlobRef
  to a file capture? This is deliberately deferred to a cross-repository contract change because no
  current source of truth defines it; answering it later changes only the production transport
  adapter and availability wiring, not the local protocol, persistence, retention, scheduling, or
  erasure requirements implemented here.
