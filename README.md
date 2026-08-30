# Ratatoskr Mobile

`ratatoskr-mobile` is the Android and iOS client repository for Ratatoskr. It provides system Share targets, a durable offline capture queue, operation progress, personal archive browsing, search, and account/device management against the public Ratatoskr Edge API.

> **Status:** architecture bootstrap. Buildable Android and iOS application shells host a shared
> Compose Multiplatform pairing surface. Generated public Platform types back the implemented
> device-pairing, rotating-session, revocation, and capability-discovery client, plus shared
> capture models, durable queue persistence, and operation projection. Android URL/file sharing has
> bounded native `ACTION_SEND` intake, shared Compose staging/status UI, WorkManager wakeups, and
> privacy-safe operation notifications. iOS URL/text/file sharing has a minimal native Share
> Extension, atomic protected App Group handoff, shared Compose confirmation/status, and Room queue
> submission. Live recent analyses/read-state plus an explicitly unsynchronized contract-fixture
> reader/curation preview are available in shared Compose. Capability-gated GitHub fixture
> browse/search, live Platform preview, and explicitly confirmed metadata/track/star actions are
> also available. File uploads are not implemented yet.

The bootstrap deployment floors are Android 8.0/API 26 and iOS 18.5. The iOS floor matches the
prebuilt Compose 1.11.1 runtime linked by the shared framework.

> [!IMPORTANT]
> **Ratatoskr is in development.** No database holds data that has to survive a schema change.
> While this status holds, these two rules replace what the documents below plan:
>
> - the API and the database keep their first version. There is no `v2` and no later major
>   version.
> - the database has no migrations. One schema definition exists, and a schema change edits it in
>   place.
>
> Only the repository owner changes this status.

## Role in Ratatoskr

Mobile is both a fast capture surface and a full client. It enables a user to preserve content directly from other apps without copying URLs into a browser or Telegram bot.

Primary use cases:

- share an article, PDF, image, or link to Ratatoskr;
- capture X, Instagram, or Threads content with explicit provenance;
- add or track a GitHub repository;
- attach a note, tags, or local collection;
- queue captures while offline;
- inspect processing progress and warnings;
- search articles, repositories, social sources, ChatGPT, and Claude archives;
- open reader and detail views;
- inspect provider connection and backup freshness;
- manage the registered mobile device and notification preferences.

The application communicates only with `ratatoskr-platform`. It never connects directly to PostgreSQL, NATS, BlobStore, or internal service endpoints.

## Repository organization

The bootstrap keeps Android, iOS, and shared client contracts aligned:

```text
ratatoskr-mobile/
├── androidApp/          # thin Android application shell
├── iosApp/              # thin SwiftUI/UIKit shell and Xcode project
├── shared/              # shared Compose root and generated Kotlin models
├── build-logic/         # Gradle convention plugins
├── contracts/           # pinned Platform OpenAPI and generator lock
├── gradle/              # version catalog and wrapper
└── tooling/
```

ADR-0001 chooses shared Compose presentation, public API models/client adapters, and capture-queue
rules inside KMP. Thin native shells retain application/share lifecycle, secure storage, background
scheduling, file access, notifications, permissions, and platform accessibility integration.
ADR-0002 applies that boundary to Android URL sharing and operation status; ADR-0003 applies it to
the iOS Share Extension, App Group, BackgroundTasks, and scene lifecycle.

## Capture contract

A capture is an explicit user action represented by a stable local ID and idempotency key:

```json
{
  "capture_id": "018f...",
  "platform": "instagram",
  "canonical_url": "https://www.instagram.com/reel/...",
  "captured_at": "2026-08-17T10:30:00+04:00",
  "source": "ios_share_extension",
  "note": "Save for visual reference",
  "collection_ids": ["..."]
}
```

The app may provide lightweight client metadata, but server-side services remain authoritative for URL classification, extraction, provider resolution, and analysis.

## Android Share Target

The Android app currently registers one supported share intent:

```text
ACTION_SEND text/plain
```

The implemented Share Target:

1. accepts exactly one bounded `http`/`https` URL and rejects unsupported or ambiguous payloads;
2. preserves the original shared text for preview without treating it as server content;
3. presents shared Compose confirmation and persists the capture before scheduling network work;
4. submits from durable state through connected WorkManager work with the stable idempotency key;
5. persists the Platform operation ID and shows bounded-polling list/detail status;
6. routes generic notifications to a validated operation UUID without private content.

Notes, tags, files, PDFs, `content://` values, and `ACTION_SEND_MULTIPLE` remain unsupported in this
slice because their contract and staging lifecycle belong to later plan items.

## iOS Share Extension

The implemented iOS Share Extension receives one bounded URL or text representation and writes a
compact capture envelope into the shared app container.

The extension:

- extracts URLs and text through supported item providers;
- avoid long network work inside extension time limits;
- save the request durably before completing;
- leaves notes, collections, files, and uploads to later contract-backed items;
- make unavailable or unsupported item representations explicit;
- never depend on provider cookies from the source app.

The main app reconciles queued extension items and submits them to Platform.

## Offline capture queue

Offline-first capture is a core invariant. The local queue stores:

```text
capture ID
idempotency key
source application/type
URL or local file reference
note and local organization choices
created time
state
retry count
last error classification
server operation ID, when accepted
```

Implemented shared states:

```text
queued
in_flight
retry_wait
auth_required
accepted
tracking
completed
permanent_failure
resolution_conflict
cancelled
```

Rules:

- a capture is persisted before submission;
- retries reuse the same idempotency key;
- the queue is bounded and inspectable;
- user content is never silently discarded;
- cancellation is explicit;
- temporary file copies have clear retention;
- one explicitly shared PDF, JPEG, PNG, or plain-text file is streamed into protected app-owned
  staging with bounded retention; resumable delivery remains visibly integration-pending until the
  public Platform receipt binding exists;
- app restarts and device reboots do not duplicate accepted operations.

## Source-specific flows

### Articles and files

The app submits the URL or attachment. `ratatoskr-extractor` owns retrieval and Document IR. Knowledge analysis is optional and asynchronous.

### X

A shared X URL is an explicit local capture. The authoritative native bookmark mirror, when connected, remains owned by `ratatoskr-x`.

### Instagram and Threads

A shared public post or reel records:

```text
saved_authority = ExplicitUserCapture
```

It does not claim native Saved membership. Provider resolution occurs in the respective service.

### GitHub

A GitHub repository capture supports:

| Mode | Catalog | GitHub write | Vault backup |
|---|---:|---:|---:|
| `metadata` | Yes | No | No |
| `track` | Yes | No | Desired policy request |
| `star` | Yes | Yes | Policy-dependent |

Browse/search rows are deterministic contract fixtures, visibly unsynchronized, and reset on
restart because Platform has no public catalog list/search contract. Opening a row obtains the
authoritative live preview through paired Platform `/v1/gh`. `track` and `star` require fresh
one-shot confirmation; `star` names the repository, opaque acting account, external write,
metadata update, and desired-backup request. Results preserve metadata, provider-star, and
desired-backup facts separately. An accepted desired policy is not a completed Vault backup. The
mobile app never stores a GitHub provider token or calls GitHub directly.

## Operation progress

After Edge accepts a capture, the app subscribes to or polls the public operation projection. User-visible phases may include:

```text
Accepted
Resolving source
Downloading
Extracting
Analysing
Backing up
Completed
Completed with warnings
Failed — retry available
```

The operation contract, not a client-side timer, determines completion. Partial success remains visible; for example, a GitHub repository may be starred while list filing or backup enrollment has a warning.

## Library and readers

The paired shared Compose client lists recent analyses through Platform `GET /v1/library/search`
and replaces read state through the generated owner-scoped resource. Platform order is preserved;
the client does not synthesize recency. Missing capabilities, offline, re-pairing, empty, mutation,
and uncertain-outcome states remain visible.

The public contract does not yet expose full analysis content, favorite, note, collection, tag,
social-reader, or AI-archive-reader resources. Ratatoskr therefore exposes those interactions only
inside a clearly labelled contract-fixture preview. Fixture state resets when the process restarts
and never sends a Platform request. Article blocks render as inert Compose text with provenance and
warnings; Instagram/Threads fixtures do not claim native Saved authority, and AI archive fixtures
retain supplied import-completeness facts.

Native shells accept only the strict custom-scheme table documented in OpenSpec. Route payloads are
opaque canonical UUIDs; query strings, fragments, credentials, percent encoding, traversal, unknown
providers, and extra segments are rejected before repository access. Search beyond the blank-query
recent page, universal links, and durable local library caching remain future work.

## Authentication and device identity

The app registers as a Ratatoskr device through Platform identity. Implemented properties:

- a user-approved one-time pairing code submitted only to a canonical HTTPS Platform origin;
- short-lived access tokens;
- serialized single-use refresh rotation and one bounded device-root recovery;
- Android Keystore-backed encrypted storage and device-only, non-synchronizing iOS Keychain
  storage;
- graceful local credential/capability clearing when another client revokes the device;
- session-scoped, fail-closed capability discovery exposed to shared application state.

Device-list management, certificate UI, and an optional biometric local gate remain future work.

Provider OAuth flows may be initiated in a system browser, but resulting provider tokens remain in the provider-owning server service.

## Local data and privacy

Local persistence contains only what the client needs for reliable UX:

- capture queue;
- operation projections;
- cached API pages;
- user preferences;
- registered-device credentials;
- temporary shared files;
- optional downloaded reader content under an explicit offline policy.

Security requirements:

1. Tokens use platform secure storage.
2. Sensitive databases and files use OS data protection/encryption facilities.
3. Logs exclude notes, conversation text, private URLs, tokens, and attachment bodies.
4. Shared temporary files have bounded retention.
5. Screenshots/app-switcher previews can be protected on sensitive screens.
6. Provider cookies and passwords are never requested.
7. Account removal clears device credentials and queued private data predictably.
8. Offline cache is distinguishable from authoritative server state.

## Notifications

Push or local notifications may report:

- capture completed;
- operation completed with warnings;
- permanent failure requiring attention;
- Git backup degraded or restore verification failed;
- provider reauthorization required;
- ChatGPT/Claude export backup is stale;
- queued captures waiting for connectivity.

Notification payloads should contain opaque operation identifiers and minimal text; sensitive content is fetched after authenticated app open.

## API and compatibility

The client consumes generated public OpenAPI types and the Platform capabilities endpoint. It does not assume every optional service is installed.

A capability set may include:

```text
content.submit
github service: repository_preview + repository_actions
vault.snapshots
social.x
social.instagram
social.threads
archive.chatgpt
archive.claude
telegram.integration
```

Unsupported features are hidden or shown as unavailable with an explanation rather than failing at runtime.

## Testing strategy

Current coverage includes:

- pairing transport outcome and HTTPS-origin tests;
- atomic refresh, concurrent caller coalescing, device-root recovery, revocation, and session-scoped
  capability tests on Android/JVM and iOS Simulator targets;
- Android Emulator Keystore and app-hosted iOS Simulator Keychain round trips;
- shared generated-contract round trips on Android/JVM and iOS Simulator targets;
- bounded capture-model, queue idempotency/FIFO/lease/retry/resolution, and monotonic operation
  projection tests on Android/JVM and iOS Simulator targets;
- current-schema Room close/reopen tests on Android Emulator and iOS Simulator;
- Android Share Target parsing/staging, durable WorkManager submission, operation list/detail,
  notification/deep-link, and deterministic API 35 emulator smoke tests;
- generated library transport, capability/UDF, fixture curation, inert reader, exact route-table,
  Android shared Compose/deep-link, and iOS shared/hosted routing tests;
- pinned GitHub interaction contracts, strict codec/transport/capability/action stores, API 35
  shared Compose instrumentation, and iOS shared graph linkage;
- deterministic generation and digest drift checks, including mutated, stale, missing, and orphaned
  generated output;
- shell/ADR/CI parity tests;
- Kotlin and Swift formatting/lint;
- Android debug assembly, shared iOS framework linking, and unsigned iOS Simulator application
  builds.

Later feature coverage will add:

- physical-device Android share/background lifecycle and live Platform scenarios;
- large attachment and expired URI fixtures;
- operation-progress contract tests;
- screenshot/accessibility tests;
- baseline profiles and launch/performance tests;
- workspace end-to-end tests against isolated Compose profiles.

## Non-goals

- Direct database, NATS, or BlobStore access.
- Provider token ownership.
- Hidden scraping from installed social apps.
- Performing article extraction or LLM analysis on the client by default.
- Replacing authoritative server archives with mobile caches.
- Treating a Share action as consent for unrelated provider writes.
- Building a complex multi-device sync protocol before the capture and browsing use cases require it.

## Initial milestones

1. Decide and document the KMP/native module boundary.
2. Establish registered-device authentication and generated API client.
3. Implement the local capture queue and idempotent submission.
4. Add Android URL Share Target.
5. Add iOS URL Share Extension.
6. Add notes, collections, retry, and operation progress.
7. Add file/PDF sharing with safe temporary-file handling.
8. Add GitHub mode selection and social URL recognition.
9. Add library, reader, and search surfaces.
10. Add notifications, accessibility, performance, and full workspace E2E coverage.

## Workspace integration

The planned workspace harness will pin Mobile with compatible Platform, Contracts, Extractor,
GitHub, social, AI-archive, and Knowledge commits. The pin and integration profile do not exist yet.
Mobile will remain independently buildable; planned system tests will use generated API contracts
and an isolated workspace deployment.

## Project status

Plan items 1 through 8 are implemented at repository level: Android/iOS shells, the shared/native ADR,
pinned generated Platform models, device pairing/session behavior, native secure storage,
capability discovery, bounded capture/queue models, Room KMP persistence, Android URL share intake,
offline submission, iOS Share Extension handoff, operation status, generic notifications, live
recent/read-state library access, strict content routing, fixture readers/curation,
capability-gated GitHub fixture discovery and confirmed Platform actions, protected file staging,
resumable contract behavior, bounded retention, native scheduling, replay-safe local erasure, and
lint/test/build CI exist. Evidence
covers deterministic transport and queue tests, Android Emulator Keystore/Room/share smoke,
app-hosted iOS Simulator Keychain, shared tests, and application builds; it does not prove live
Platform pairing/submission/GitHub actions or full reader/curation, a physical-device run,
signing/provisioning, provider integration, Vault completion, universal links, physical-device
queue protection/background delivery, or a live public file receipt binding.
