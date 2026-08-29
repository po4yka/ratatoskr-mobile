## Context

See `proposal.md` for motivation. The mobile pin is Platform commit `a117ca8`, while current published Platform `main` at `070b718` adds only the reviewed `library.search`/`library.read_state` capability names, two first-version routes, and six generated schemas. No public endpoint currently returns full analysis/Document IR content or owns favorite, note, collection, tag, social-reader, or AI-archive-reader mutations.

ADR-0001 assigns shared Compose, UDF state, generated clients, and common repositories to KMP while native shells own OS link intake and lifecycle. The current root uses Navigation 3 keys behind a small shared route state, Android and iOS already construct one long-lived authenticated application graph, and `DeviceAuthorizedRequestExecutor` provides serialized refresh/recovery for public API adapters.

## Goals / Non-Goals

**Goals:**

- Consume the published Platform recency/read-state surface with generated types and current-session capabilities.
- Deliver usable, testable reader and curation behavior from deterministic fixtures while keeping its non-authoritative status visible.
- Keep route parsing pure, strict, and shared; keep Android/iOS intake and lifecycle native.
- Make the fixture adapter replaceable by future generated Platform adapters without changing presentation contracts.

**Non-Goals:**

- Publishing or guessing missing Platform/Knowledge/social/AI-archive endpoints or capability names.
- Persisting fixture mutations across process death or presenting them as user data; a process restart resets the preview.
- Search, universal/app-link association, notification routing, reader preferences/progress, rich Markdown/HTML rendering, or offline caching, which remain plan item 9.
- Changing capture payloads, queue persistence, Share Target/Extension behavior, provider authority, or backend user-content storage.

## Decisions

### 1. Advance the pin to the exact additive Platform revision

Copy Platform `openapi/openapi.json` from clean published commit `070b718238c4e6e45a5b7fc08ebe719ed5374e33`, update the lock digest/revision, and regenerate the complete owned Kotlin model tree. Tests first reference `LibraryItem`, `LibraryPage`, `ReadState`, `ReadStateResource`, and `ReplaceReadState`; the initial RED is the expected missing generated symbols, followed immediately by generation and drift/mutation validation.

This is preferred to hand-written DTOs because the repository contract boundary already requires generation. Staying on the old pin would discard a live capability and overuse fixtures.

### 2. Separate live summary/read state from fixture curation

Use two explicit repositories:

- `LibraryRepository` lists blank-query recent summaries and replaces read state through generated Platform resources plus `DeviceAuthorizedRequestExecutor`.
- `FixtureUserContentRepository` owns a deterministic catalog of article, X, Instagram, Threads, ChatGPT, and Claude fixture records plus in-memory favorite, note, collection, and tag mutations.

The shared graph supplies both to shared UDF stores. The library surface labels live summaries separately from a “Contract fixture preview” entry. Opening an arbitrary live summary retains its title/identity and read-state action but renders an integration-pending detail because no public detail contract exists. Opening a fixture record enables its fixed reader and curation behavior. The client never joins a live owner item to a fixture merely because titles or URLs happen to match.

A single repository with optional network behavior was rejected because a caller could not tell whether a successful mutation was server-owned or preview-only. A local Room cache was rejected because it would make fixture state look durable/authoritative and require a schema change with no product benefit.

### 3. Model authority in state rather than in copy alone

Every presentation object carries `LivePlatform`, `ContractFixture`, or `Unavailable` authority. Actions are enabled from authority plus fresh capabilities, not from which fields happen to be non-null. Live list/read state uses `library.search` and `library.read_state`; fixture mutations never require or send a Platform request and always retain an integration-pending label.

Read-state replacement is pessimistic: the row keeps the last authoritative value while the request runs, then adopts only the returned value. An unavailable or uncertain response exposes retry. This avoids a false success after a request whose result was lost.

Fixture favorite/note/membership mutation is serialized under one common mutex and emits a complete immutable snapshot. Note input is bounded to 2,000 Unicode scalar values. Collection/tag membership uses idempotent set semantics and the repository failure seam returns the last confirmed snapshot, which lets presentation rollback without reconstructing state.

### 4. Render only a small inert content model

Contract fixtures use a local sealed content model of heading, paragraph, quote, and code-text blocks; all fields render through Compose text primitives. The model includes supplied provenance, warnings, analysis summary/key points, tags, and provider/import completeness fields but no raw HTML, WebView payload, script, provider cookie, or internal service reference.

This borrows the published Document IR principle of ordered typed blocks and explicit provenance without claiming that the local fixture is a generated Document IR wire contract. A Markdown/HTML renderer was rejected because it adds active-content and dependency risk outside this item.

### 5. Use one shared route table and thin native delivery

The common parser accepts exactly these canonical custom-scheme shapes:

```text
ratatoskr://library/analyses/<uuid>
ratatoskr://library/social/<x|instagram|threads>/<uuid>
ratatoskr://library/ai-archives/<chatgpt|claude>/<uuid>
```

It rejects non-lowercase/non-canonical UUID text, query/fragment/user-info, percent-encoded ambiguity, traversal, and extra segments before invoking a repository. It returns typed Navigation 3 keys for article, social, and AI-archive readers. Internal row clicks construct those same keys directly.

Android adds a narrow custom-scheme `ACTION_VIEW` filter and parses new intents; iOS declares only the Ratatoskr custom scheme and forwards `onOpenURL` into the long-lived shared controller. Universal/app links and associated-domain files stay in item 9. Native code does no provider mapping and passes no content, credentials, or URL query into shared state.

### 6. Keep stores lifecycle-safe and test through public seams

`LibraryListStore`, `LibraryReaderStore`, and `FixtureCurationStore` expose immutable `StateFlow` state and action dispatch. They cancel owned work when closed, reject duplicate submissions while a mutation is active, and do not trigger work from recomposition. The long-lived platform graph owns repositories; a visible route owns its store.

Tests use only the public generated API adapter, repository, store, route parser, and Compose surface seams agreed in the request. Ktor mock responses prove paths/bodies/error mapping, common coroutine tests prove state transitions and mutations, the same common tests execute on iOS Simulator, and Android instrumentation checks shared Compose semantics and native link handoff. Existing iOS XCTest and application builds ensure the Swift shell continues to link; no new long-running native service is introduced.

## Risks / Trade-offs

- [Fixture behavior can be mistaken for synchronized user data] → Separate it from live summaries, attach authority to state, show “Contract fixture preview / integration pending” on every fixture route, and make fixture mutations issue zero network calls.
- [The published list has no recency timestamp] → Preserve Platform ordering and do not synthesize or display a timestamp.
- [Read-state response loss leaves the actual server state unknown] → Keep the last confirmed value and require retry/reload; never optimistic-success the live resource.
- [A future full-detail contract differs from the fixture model] → Keep generated wire types outside the fixture repository interface and replace the adapter deliberately when the public contract lands.
- [Custom-scheme links can be invoked by other apps] → Carry opaque identifiers only, validate the exact route table, and rely on authenticated owner-scoped reads; invalid or absent items reveal no private detail.
- [Fixture state resets on restart] → State this in the UI/docs and do not add persistence that could be confused with backend truth.

## Migration Plan

1. Land the additive Platform pin and generated models; older Platform deployments simply omit capabilities and the client fails closed.
2. Land shared repositories, UDF/navigation, fixtures, Compose surfaces, and thin native link intake together so no shell references an unavailable shared API.
3. Run contract mutation/drift, common Android/JVM and iOS Simulator tests, Android instrumentation, both application builds, existing simulator smoke, and strict OpenSpec validation.
4. Rollback is a normal client-code revert. No database schema, persisted fixture state, server data, or new API version requires migration or cleanup.

When Platform later publishes favorite/note/collection/tag or reader contracts, replace the fixture adapter with generated authenticated adapters in a new cross-repository change. Do not silently reuse the fixture data or identifiers as server state.
