# Ratatoskr Mobile

`ratatoskr-mobile` is the Android and iOS client repository for Ratatoskr. It provides system Share targets, a durable offline capture queue, operation progress, personal archive browsing, search, and account/device management against the public Ratatoskr Edge API.

> **Status:** architecture bootstrap. No Android app, iOS app, Share Extension, KMP module, API client, or local database is implemented yet.

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

## Proposed repository organization

One repository keeps Android, iOS, and shared client contracts aligned:

```text
ratatoskr-mobile/
├── shared/
│   ├── domain/
│   ├── api-client/
│   ├── capture-queue/
│   ├── authentication/
│   └── test-fixtures/
├── androidApp/
│   ├── app/
│   ├── share-target/
│   └── baseline-profile/
├── iosApp/
│   ├── App/
│   ├── ShareExtension/
│   └── Widgets/
├── design/
├── integration-tests/
└── tooling/
```

A Kotlin Multiplatform shared core with native Android and SwiftUI presentation is the likely starting point, but the exact split remains an ADR. Platform-native Share Extension behavior and lifecycle handling must not be forced through an abstraction that reduces reliability.

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

The Android app registers supported share intents, initially including:

```text
ACTION_SEND text/plain
ACTION_SEND application/pdf
ACTION_SEND selected image/file types
```

The Share Target should:

1. parse the incoming intent defensively;
2. create a local capture record immediately;
3. present a compact confirmation UI;
4. allow note, collection, tag, and processing-policy selection;
5. persist the item before network work;
6. enqueue upload through a lifecycle-safe background mechanism;
7. finish quickly without blocking the source app;
8. make retry and failure state visible in the main app.

Content URIs are copied or streamed only with the granted permission and are never assumed to remain readable after the share activity closes.

## iOS Share Extension

The iOS Share Extension receives supported `NSExtensionItem` representations and writes a compact capture request into the shared app container.

The extension should:

- extract URLs, text, and files through supported item providers;
- avoid long network work inside extension time limits;
- save the request durably before completing;
- provide a minimal note/collection UI;
- hand off larger or deferred uploads to the main application;
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

Planned states:

```text
draft
queued
preparing_upload
uploading
accepted
processing
completed
completed_with_warnings
retryable_failure
permanent_failure
cancelled
```

Rules:

- a capture is persisted before submission;
- retries reuse the same idempotency key;
- the queue is bounded and inspectable;
- user content is never silently discarded;
- cancellation is explicit;
- temporary file copies have clear retention;
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
| `track` | Yes | No | Yes |
| `star` | Yes | Yes | Policy-dependent |

`metadata` is the default. `star` requires a connected account, provider scope, explicit confirmation, and an audited server operation. The mobile app never stores the GitHub provider token.

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

## Library and search

The full client may expose:

- recent captures and operations;
- article reader;
- repository catalog and backup status;
- social-source timeline;
- ChatGPT and Claude projects/conversations;
- full-text and semantic search;
- tags and local collections;
- provider connection status;
- incomplete archive or backup warnings.

Search and result access are always scoped by the authenticated user and the public Platform API. The app does not cache provider secrets or bypass server-side authorization.

## Authentication and device identity

The app registers as a Ratatoskr device. Planned properties:

- secure initial pairing or account login;
- short-lived access tokens;
- refresh-token family with rotation;
- Keychain/Keystore-backed secret storage;
- device name and last-seen state;
- explicit revoke from another client;
- certificate/TLS visibility for self-hosted endpoints;
- optional biometric gate for local archive access.

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
github.catalog
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

Planned coverage:

- shared domain and API-client unit tests;
- queue state-machine and idempotency tests;
- Android Compose/UI and Share Target tests;
- iOS SwiftUI and Share Extension tests;
- offline/reconnect scenarios;
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

`ratatoskr-workspace` pins Mobile with compatible Platform, Contracts, Extractor, GitHub, social, AI-archive, and Knowledge commits. Mobile remains independently buildable; system tests use generated API contracts and an isolated workspace deployment.

## Project status

This README defines the intended Android/iOS client and capture architecture. No application, shared module, Share Target, Share Extension, queue, or API client exists yet.
