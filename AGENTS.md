# Ratatoskr Mobile Agent Instructions

## Scope

These instructions apply to the `ratatoskr-mobile` repository and its Android/iOS capture and client surfaces.

This repository owns mobile UX, local client state, explicit share ingestion, offline delivery, secure device authentication, and presentation of Ratatoskr operations and library data.

## Repository mission

The mobile client should provide:

- Android Share Target/`ACTION_SEND` capture;
- iOS Share Extension capture;
- explicit article, social, GitHub repository, text, and file intake;
- durable offline capture queue;
- registered-device authentication;
- operation progress and actionable errors;
- library/search/detail UI through Platform APIs;
- local notes, capture options, and collection selection;
- privacy-preserving notifications and local storage.

It is a client of the Ratatoskr control plane. It must not duplicate service-owned extraction, provider synchronization, analysis, or backup logic.

## Current phase

The repository is in architecture bootstrap. Do not assume Android/iOS projects, KMP modules, Share Extensions, navigation, persistence, generated API clients, or CI commands exist unless they are present in the checkout.

When creating initial implementation:

- establish platform-native share/lifecycle behavior first;
- make the queue and device auth durable before adding rich library features;
- keep shared KMP code narrow and intentional;
- do not force platform-specific UI/lifecycle/security code into shared abstractions.

### Development status

Ratatoskr is in development. No database holds data that has to survive a schema change. While this
status holds, these rules are binding, and they override anything else in this repository that
plans otherwise, including the rest of this file:

- **One version only.** The API, the database, and the contracts keep their first version. Do not
  add a `v2` or a later major version, and do not add version negotiation, deprecation windows, or
  parallel-major routing.
- **No database migrations.** Do not add a migration file, and do not add migration tooling. A
  schema change edits the current schema definition in place, and a test database is created from
  that definition.
- **The product is `Ratatoskr`.** It is not "Ratatoskr Next". Do not write that name in code,
  documentation, identifiers, comments, or commit messages.

Only the repository owner changes this status. Ask before you write anything these rules forbid.

## Sources of truth

Use this order:

1. active task/changeset and accepted ADRs;
2. `README.md`;
3. Platform/public contracts and generated clients from `ratatoskr-contracts`;
4. Android/iOS platform requirements;
5. repository tests;
6. implementation details.

Do not hard-code assumptions about backend capabilities that are absent from the capability/API contract.

## Hard boundaries

### Mobile owns

- Android/iOS UI and navigation;
- share-target/extension request parsing;
- local capture draft and queue state;
- registered-device credential storage/use;
- local preferences and privacy settings;
- operation progress projection and notifications;
- local caching of authorized API data;
- client-side notes/collection choices before submission;
- platform-specific accessibility, background, and lifecycle behavior.

### Mobile does not own

- provider OAuth tokens for GitHub/X/Instagram/Threads;
- ChatGPT/Claude archive parsing;
- URL extraction or Chromium rendering;
- LLM analysis/embeddings/search ranking;
- Git mirroring/backups;
- authoritative native bookmark/Saved state;
- Telegram interaction state;
- direct access to internal services, PostgreSQL, NATS, or BlobStore.

All domain commands and reads go through `ratatoskr-platform` public APIs.

## Platform architecture rules

The app may be native Android/iOS with selectively shared KMP code.

Shared code is appropriate for behavior that is genuinely identical, such as:

- public API contracts/client adapters;
- capture/operation domain models;
- idempotency and queue transition rules;
- URL/source classification hints;
- common validation and serialization;
- selected repository interfaces.

Keep native:

- Android/iOS UI and navigation unless an ADR explicitly chooses shared UI;
- Share Target/Share Extension lifecycle;
- secure credential storage;
- background scheduling and notifications;
- file/URI/security-scoped resource access;
- platform permissions and settings integration;
- accessibility and system presentation.

Do not introduce KMP abstractions solely to make source files look symmetrical. Prefer explicit platform adapters.

## Explicit capture invariant

A share or capture is created only from explicit user action.

Supported inputs may include:

- one or more `http`/`https` URLs;
- selected/shared text;
- PDFs or supported files;
- forwarded/shared social permalinks;
- GitHub repository URLs;
- optional user note and collection/tag selection.

Do not:

- monitor clipboard continuously;
- scan notifications or browser history;
- upload unrelated attachments from a share payload;
- capture screen content in the background;
- infer user intent from passive app usage;
- silently send a shared item before the user-visible extension/target behavior completes according to product design.

## Share input safety

Treat every shared item as untrusted.

- Accept only documented MIME types and URL schemes.
- Bound text length, file count, file size, and total payload size.
- Resolve Android `content://` and iOS item-provider/security-scoped resources only for the duration and purpose required.
- Copy queued file bytes into an app-owned protected staging area when persistence is needed.
- Do not retain access to arbitrary external locations longer than necessary.
- Validate MIME with content evidence where practical.
- Sanitize filenames for display and never use them directly as storage paths.
- Do not execute, render active HTML, or open macros/scripts from shared files.
- Clean only app-owned temporary/staging files after durable upload/result according to policy.

A shared file reference that cannot be read must produce a visible partial/error state, not silent omission.

## Capture draft and confirmation

Before submission, the client may let the user review:

- detected source type;
- URL/file/text items;
- optional note;
- local collection/tag choices;
- requested processing mode;
- privacy/attachment impact.

Rules:

- preserve the original shared data separately from local display normalization;
- make destructive/external writes explicit;
- do not claim canonical provider identity until backend resolution;
- show when selected text or a file will be uploaded;
- do not include hidden metadata beyond the contract;
- use backend capabilities to decide available modes.

## Article capture

For ordinary URLs/files:

- send the source reference/file through Platform;
- let Extractor own safe fetch/parse/Document IR;
- let Knowledge own summaries and indexing;
- persist operation ID and show progress;
- do not perform a competing full article scraper in the app;
- optional local preview metadata is advisory only;
- offline URL capture may queue the URL without fetching it locally.

## Social capture

For X, Instagram, and Threads:

- send the explicit permalink and capture provenance;
- mark acquisition as mobile Share Target/Share Extension;
- do not read provider cookies or hidden session APIs;
- do not represent Instagram/Threads capture as authoritative native Saved state;
- X authoritative bookmark state comes from the X connector, not mobile page/app state;
- keep user note/collection choices separate from provider content;
- show unavailable/private results truthfully.

## GitHub repository capture

Recognize supported GitHub repository URLs and offer modes exposed by Platform capabilities:

```text
metadata
track
star
```

Rules:

- `metadata` requests local catalog metadata;
- `track` requests desired backup without provider star;
- `star` is an external GitHub write and requires connected account, scope, explicit confirmation, idempotency, and audit in backend services;
- do not collect/store GitHub tokens in the mobile client;
- do not execute Git locally as part of capture;
- present list filing, backup enrollment, and analysis as separate partial outcomes;
- retry only incomplete backend steps according to operation contracts.

## Offline queue

Every capture receives a stable local ID/idempotency key before submission.

Queue requirements:

- persist across process death, reboot, app update, Share Extension termination, and background suspension;
- use explicit states such as draft, queued, uploading, accepted, awaiting result, completed, partial, failed, cancelled;
- store only required payload, protected according to sensitivity;
- bound item count, bytes, age, retries, and concurrency;
- use backoff with jitter and server retry hints;
- distinguish auth, connectivity, size, policy, validation, and server failures;
- do not retry permanent failures automatically;
- resolve uncertain request outcomes through idempotency/operation lookup;
- permit user retry/cancel/delete while preserving backend truth;
- clean staged files only when no queued/accepted operation still needs them.

Android WorkManager/background APIs and iOS background execution are delivery mechanisms, not sources of business truth. The durable queue is authoritative locally.

## Share Extension and main-app coordination

### iOS

- Share Extension execution time and memory are constrained.
- Persist the capture atomically to an App Group/shared container.
- Do not run long uploads/extraction inside the extension when the main app/background transfer should own them.
- Coordinate access to shared queue/files safely.
- Use security-scoped resources and `NSItemProvider` asynchronously and defensively.
- Complete/cancel the extension request accurately.

### Android

- Parse `ACTION_SEND`/`ACTION_SEND_MULTIPLE` defensively.
- Respect URI grants and persist/copy content only when allowed.
- Avoid long work on the Activity/UI thread.
- Enqueue durable work before finishing the transient share surface.
- Use WorkManager or reviewed platform scheduling for guaranteed/retriable delivery.
- Do not depend on the share Activity remaining alive.

## Device authentication

Mobile authenticates as a registered device/user client.

- Store secrets in Android Keystore-backed storage and iOS Keychain.
- Scope credentials to a specific Ratatoskr instance and minimum client permissions.
- Support revoke, logout, rotation, and re-registration.
- Validate HTTPS/TLS; never silently allow insecure fallback.
- Never store tokens in plain preferences, logs, analytics, crash reports, URLs, or screenshots.
- Keep provider OAuth completion, when initiated through Platform, isolated from provider token storage; tokens are transferred to the owning backend service.
- Apply biometric access only as an optional local gate, not as a substitute for server authorization.

## API client and contracts

- Prefer generated/typed clients from public OpenAPI/contracts.
- Validate runtime responses at trust boundaries when generated typing is insufficient.
- Keep transport errors separate from domain/operation failures.
- Include request, correlation, device, and idempotency metadata according to contract.
- Handle backward-compatible unknown enum values safely.
- Use capability discovery and minimum client-version metadata.
- Do not call internal service endpoints or rely on their deployment addresses.
- Do not use committed relative path dependencies on sibling repositories.

## Operation progress

Persist `operation_id` after acceptance and project backend updates idempotently.

- Handle duplicate and out-of-order updates.
- Use sequence/version/timestamp rules from the contract.
- Never regress a terminal state.
- Distinguish completed, partial, failed, cancelled, and reauth-required states.
- Show per-step outcomes for multi-step GitHub/provider workflows.
- Avoid storing full sensitive result bodies when a reference/API fetch is sufficient.
- Support deep links from notifications to the operation/result.
- Do not declare success based only on upload completion.

## Library and search

- Fetch authorized projections only through Platform.
- Cache data with explicit staleness and invalidation behavior.
- Apply account/instance separation locally.
- Never bypass backend authorization with locally remembered object IDs.
- Treat snippets, titles, Markdown, embed HTML, and user content as untrusted for rendering.
- Sanitize rich content and use safe link opening.
- Do not implement a competing local semantic index unless approved by a separate product/ADR.
- Preserve source type, provenance, completeness, and availability in UI.

## Local storage and privacy

- Minimize retained message/article bodies and attachments.
- Encrypt or use platform data protection for sensitive local caches/queue where required.
- Exclude sensitive data from cloud backup when product policy requires local-only handling.
- Provide clear queue/cache/logout deletion semantics.
- Do not delete server archives as an incidental local cache clear.
- Avoid screenshots/app switcher previews on highly sensitive screens where configured.
- Notifications are generic on the lock screen by default.
- Analytics must not contain URLs, titles, notes, message content, filenames, provider usernames, or archive data.

## UI, accessibility, and state

- Model durable domain state separately from transient view state.
- Use unidirectional data flow and explicit one-shot effects.
- Do not trigger submissions repeatedly from recomposition/view refresh.
- Ensure loading/empty/error/partial/offline states are first-class.
- Support Dynamic Type/font scaling, screen readers, sufficient touch targets, contrast, reduced motion, and localization-safe layouts.
- Use platform conventions for destructive confirmation, permission rationale, background state, and navigation.
- Avoid presenting planned/unavailable capabilities as working.

## Performance and battery

- Do not poll aggressively in the foreground/background.
- Prefer server progress streams or bounded adaptive polling according to platform lifecycle.
- Batch queue work without starving user-triggered items.
- Stream files rather than loading them fully into memory.
- Bound image/media decoding and cache size.
- Avoid waking the device for non-urgent stale operations.
- Measure startup, share-to-queued latency, upload memory, and background retry behavior.

## Security

- Validate deep links/universal/app links and never execute arbitrary commands from them.
- Use opaque server-side intent IDs instead of embedding sensitive payloads in links.
- Protect exported Android components and iOS URL handlers.
- Validate all IPC/App Group/shared-container data.
- Do not use WebViews for provider login or render untrusted HTML without a reviewed sandbox.
- Prevent tapjacking/overlay-sensitive external-write confirmation where platform controls allow.
- Redact backend/provider errors.
- Do not include secrets or private content in diagnostics.
- Keep dependencies and SDKs minimal and pinned.

## Observability

Local diagnostics should cover:

- share intake type/count/size without content;
- queue state, age, retries, and staged bytes;
- auth/connection state without tokens;
- upload/operation latency and failure class;
- background worker/extension handoff;
- capability/client-version mismatches;
- notification/deep-link delivery;
- app/build/version and configured instance in non-sensitive form.

Remote telemetry must be explicit and privacy-preserving.

## Testing expectations

When implementation exists, include applicable tests for:

- shared URL/text/file parsing and limits;
- Android URI grants, process death, WorkManager retry, and multiple shares;
- iOS `NSItemProvider`, App Group queue, extension termination, and security-scoped resources;
- queue/idempotency state-machine recovery;
- staged file lifecycle and path safety;
- social saved-authority semantics;
- GitHub mode and external-write confirmation;
- Keystore/Keychain abstractions and revoke/logout;
- TLS/endpoint/profile handling;
- API unknown variants/capabilities;
- duplicate/out-of-order operation updates;
- offline/partial/error UI;
- deep-link validation;
- accessibility and screenshot/UI regression where useful;
- macrobenchmark/performance and memory for large shares where applicable;
- integration tests through workspace profiles.

Use synthetic content. Never commit device tokens, personal URLs, notes, archives, or private files.

## Cross-repository change rules

Use a workspace changeset when changing:

- Platform capture/device/operation/library APIs;
- social/GitHub capture contracts;
- file/upload/BlobStore references;
- capabilities/minimum client version;
- Telegram/web/browser-extension shared product semantics;
- deep links or external-write confirmation;
- generated API clients.

List backend/client compatibility, rollout, rollback, old-app behavior, migration, privacy, and store-release impact.

## Git and PR workflow

- State affected platforms and surfaces: Android app/share target, iOS app/share extension, shared KMP, queue, auth, API, library, notifications.
- Avoid mixing Android/iOS platform refactors with unrelated contract changes.
- Include platform-specific tests for lifecycle/background/file behavior.
- Document permissions, entitlements, deep links, local storage, and privacy impact.
- Do not add provider credentials, extraction, LLM, or Git backup logic.
- Do not commit signing/provisioning secrets, device tokens, personal captures, private screenshots, or production-only config.
- Do not force shared UI/KMP without an ADR and demonstrated benefit.
- Update README/ADRs when platform architecture or boundaries change.

## Completion criteria

A task is complete only when:

- responsibility belongs to the mobile client;
- capture remains explicit and shared input is validated/bounded;
- queue/idempotency survives lifecycle termination and uncertain network outcomes;
- staged files and permissions are handled safely;
- device secrets remain in Keystore/Keychain and TLS is verified;
- provider tokens/domain work remain in backend services;
- social and GitHub semantics/confirmations are truthful;
- operation progress handles duplicates, ordering, partial success, offline, and reauth;
- local storage, notifications, analytics, accessibility, performance, and battery constraints are addressed;
- Android/iOS/shared tests and workspace integration pass;
- contracts and cross-repository rollout are documented.
