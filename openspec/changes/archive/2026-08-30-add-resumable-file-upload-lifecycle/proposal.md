## Why

Ratatoskr can durably queue an opaque staged-file reference, but neither mobile platform can yet
turn an explicit shared file into a resumable transfer, bound its retained bytes, or erase every
local copy when the device is revoked. The fleet has canonical digest-first blob-transfer schemas,
while the pinned Platform OpenAPI still exposes no public mobile receipt route, so the client must
implement and verify the full local protocol now without pretending that live integration exists.

## What Changes

- Pin the relevant `ratatoskr-contracts` blob-transfer schemas and valid fixtures, generate their
  shared Kotlin models deterministically, and reject pinned/generated drift. The live Platform
  integration remains explicitly pending until its public capability and HTTP binding are pinned.
- Add one shared, durable resumable-upload coordinator that streams fixed-size chunks, checkpoints
  opaque session state, reconciles receiver status before resuming, reuses the capture identity,
  verifies the final receipt, and exposes truthful pending/partial/failure states.
- Extend Android and iOS explicit share intake to copy one supported file into an app-owned protected
  staging area under strict type, size, path, and lifetime bounds; external URI or security-scoped
  access never becomes durable authority.
- Add a retention ledger and cleanup policy that reports local staged/upload usage, removes only
  unreferenced or safely handed-off bytes, expires crash leftovers, and refuses new staging rather
  than silently evicting unfinished user content when the hard bound is reached.
- Add native background adapters whose scheduling decisions come from durable queue/upload state:
  Android WorkManager and iOS background execution require connectivity and honor battery/storage
  constraints, while foreground reconciliation repairs lost or duplicated schedules.
- **BREAKING**: a proven remote device revocation now cancels in-flight local work and erases the
  queue, staged bytes, transfer checkpoints, App Group inbox, caches/preferences, capabilities, and
  native credential record. A user-initiated clear-data command performs the same complete erasure
  only after explicit destructive confirmation.
- Add shared, Android-instrumented, and iOS-simulator tests plus gate-parity checks for interruption
  recovery, cleanup bounds, native constraint mapping, safe file staging, and complete erasure.

## Capabilities

### New Capabilities

- `mobile-file-transfer-lifecycle`: Explicit protected file staging, contract-aligned resumable
  transfer, bounded retention and usage, native scheduling constraints, and complete local erasure.

### Modified Capabilities

- `capture-queue`: Associate staged files with durable transfer checkpoints/receipts and replace
  auth-required retention after proven revocation with complete local erasure.
- `device-identity`: Replace credential-only remote-revocation cleanup with one coordinated wipe of
  every account-scoped local store and staged artifact.
- `android-share-capture`: Accept and protect one bounded supported file, schedule constrained
  resumable work, and cancel/wipe it on proven revocation.
- `ios-share-capture`: Stage one bounded supported file through the App Group without extension
  upload work, then schedule resumable main-app transfer and erase both containers on revocation.
- `mobile-project-bootstrap`: Pin/generate blob-transfer contracts and enforce shared/native
  file-transfer, retention, scheduling, erasure, and drift tests in the documented CI gate.

## Impact

- Affected surfaces: shared KMP contracts/domain/Room schema/Compose usage state; Android Share
  Target, protected staging, WorkManager, and instrumentation; iOS Share Extension/App Group,
  background scheduling, Keychain/container erasure, and simulator tests; contract tooling and CI.
- Contract source: the workspace `ratatoskr-contracts` blob-transfer declaration, session, chunk
  receipt, status, finalize, and completion schemas/fixtures. Transfer failures remain a local
  fail-closed classification until a public wire schema exists. The workspace `blob-references`
  spec still requires receiver-owned bytes and forbids a control-plane blob service.
- API compatibility: no new mobile-owned server API and no version negotiation. The pinned Platform
  OpenAPI currently has no public receipt-session binding, so fixture/harness proof is not live
  Platform upload proof and the production integration remains capability-gated and pending.
- Schema: the one current Room schema is edited in place; no migration or compatibility reader is
  introduced.
- Privacy/rollback: local staged bytes are protected, excluded from backup, never logged, and wiped
  on proven revocation/confirmed clear-data. Rolling back the client disables new transfers but
  cannot recover intentionally erased local data or undo a receipt already accepted by its owner.
- Dependencies: no new production dependency is planned; existing Kotlin serialization, Room,
  Ktor, WorkManager, platform hashing/file APIs, BackgroundTasks/URLSession, and generated-contract
  tooling are reused.
