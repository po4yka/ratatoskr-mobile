## Context

See `proposal.md` for motivation. Items 1–8 left a shared Compose root in thin Android/iOS shells, a generated `LibraryPage` contract, a blank-query recent-library adapter, an exact custom-scheme route table, Android local operation notifications, and native clear-data cancellation. The pinned Platform OpenAPI already supports authenticated `q`/`limit`/`offset` library search but exposes no mobile completion-subscription route or capability. No production universal-link host or deployed association documents are recorded in this repository.

The design must preserve the shared/native ADR: KMP owns identical search, routing, notification policy, localization, and presentation state; native code owns intent/user-activity intake, permissions, entitlements, system notifications, and platform settings. It must also preserve the first-version/no-migration development rules and avoid turning fixture or simulator evidence into live service proof.

## Goals / Non-Goals

**Goals:**

- Reuse the pinned public search contract for real paired-session search rather than add a second search API or local semantic index.
- Make query paging and concurrent response handling deterministic in common code.
- Give Android and iOS one strict parser for custom-scheme and canonical HTTPS destinations.
- Represent notification support, user preference, and native permission as separate truth-bearing state.
- Improve accessibility through reusable shared presentation primitives and native intake/settings adapters.
- Make diagnostics structurally incapable of accepting private payload fields and add an enforceable production-source privacy check.

**Non-Goals:**

- A local full-text or embedding index, durable result cache, or offline search.
- New Platform, Knowledge, notification-provider, Apple association, or Android association endpoints.
- Push-token registration while the public Platform contract lacks a subscribe path.
- Proving live domain verification, APNs/FCM delivery, physical-device assistive technology, signing, or store review.
- Implementing live collection detail contracts that Platform does not publish; collection links remain truthful fixture/integration-pending destinations.
- Reworking the complete visual design or adding animation.

## Decisions

### 1. Extend the existing library transport instead of adding a generic search service

`PlatformLibraryApi` will gain one bounded search operation that uses the generated `LibraryPage` and the existing authorized request executor. Blank-query recency remains the current library-list behavior; the new search store accepts only nonblank trimmed queries and passes `q`, `limit`, and `offset` to the same public endpoint. This keeps `library.search` as the sole capability and preserves the workspace `library-search-read-state` contract.

A separate generic `/v1/search` adapter was rejected because it is absent from the pinned OpenAPI. A local index was rejected because it would duplicate Knowledge authority and retain more user content.

### 2. Use a generation-keyed UDF search store

The common search store will own query text, submitted normalized query, items, offset, `hasMore`, load state, and a monotonically increasing request generation. Submit, retry, and next-page effects capture the generation and expected offset; only a response matching both may mutate state. A single in-flight next-page flag coalesces repeated requests. Changing or clearing the submitted query invalidates older effects.

This is preferred over relying only on coroutine cancellation because transport cancellation can race with an already available response and is harder to prove consistently across Kotlin/JVM and Kotlin/Native. No persistent cache is added: an offline state retains the query for retry, not stale authoritative results.

### 3. Parse links from raw strings with an explicit routing configuration

The shared route table will accept a `ContentLinkConfiguration` containing an exact set of ASCII lowercase HTTPS hosts. It will match whole raw strings before constructing navigation keys, so percent decoding, case folding, dot-segment normalization, credentials, ports, query, and fragment cannot change interpretation. Existing custom-scheme forms remain accepted. New keys cover analysis UUID, collection slug, and GitHub owner/repository identity.

Android forwards `Intent.dataString`; iOS forwards `webpageURL.absoluteString` from `NSUserActivityTypeBrowsingWeb` and existing `onOpenURL`. Both provide the build-configured host to the shared graph. Repository tests use `links.ratatoskr.test`; release profiles must replace it and separately deploy/verify association files. A dynamic per-instance host was rejected because Android/iOS verified associations are application-build trust declarations, not arbitrary server configuration.

### 4. Configure native association declarations without claiming deployment

Android will add an HTTPS `VIEW` filter with `autoVerify=true` and a manifest placeholder. iOS will add `com.apple.developer.associated-domains` with an `applinks:` build-setting substitution and handle browsing user activities. Shell/entitlement parity checks will require the same configured test host and route intake tests.

The repository will not commit a guessed production domain or association JSON. Consequently emulator/simulator dispatch proves parser and shell wiring only; the checklist and status docs will keep deployed verification open for item 10.

### 5. Model notification truth before native permission plumbing

Common code will model:

- contract availability: `IntegrationPending` or `Available`;
- user preference: disabled or enabled;
- native permission: not requested, denied, or authorized;
- effective state derived from all three.

The production graph selects `IntegrationPending` by inspecting the pinned contract integration definition, not by a runtime string capability that lacks a typed route. In that state the enable action is unavailable and native adapters are never asked for permission or a token. Tests use an injected contract-fixed `Available` port solely to exercise permission transitions and ensure the future seam remains fail-closed.

Android uses `POST_NOTIFICATIONS` state and an Activity-result request only after an explicit enable action. iOS uses `UNUserNotificationCenter` authorization/settings through a native adapter under the same rule. Existing local Android accepted/terminal notifications keep working from queue outcomes when permission already exists; no local polling is relabeled as subscription.

### 6. Keep subscription transport absent until a public contract exists

There will be no production subscribe HTTP call, token store, or invented capability name in this change. The shared `CompletionSubscriptionAvailability` boundary can report only integration-pending in the production graph. When Platform later publishes a route, that cross-repository change must pin generated types and add transport tests before an available implementation can be wired.

This explicit no-op is preferable to a fixture URL or internal service call because it satisfies the requested truthful behavior without creating a security-sensitive compatibility promise.

### 7. Consolidate accessible Compose primitives and localized strings

The change will introduce a small shared presentation vocabulary for headings, body/status text, text inputs, buttons/toggles, result rows, and state announcements. Primitives will set semantic role/name/state, merge decorative descendants, maintain logical source/focus order, and enforce a 48 dp minimum target. Existing search-adjacent library, operation, GitHub confirmation, storage-clear, and primary navigation actions will adopt them where needed to eliminate unlabeled or underspecified interactive nodes.

New strings will live in a typed English/Russian catalog selected from a native-provided language tag with English fallback. This avoids a new dependency and makes common tests able to enumerate every key in both locales. Existing unrelated copy is not translated wholesale, but any touched state/action string moves behind the catalog to prevent a mixed inaccessible surface.

Contrast will use named palette constants whose ratios are unit-tested. The UI will not add essential animation; text uses scalable `sp`, avoids fixed-height text containers, and exposes loading/error/partial state as text and live-region semantics where supported.

### 8. Make diagnostics content-free by type

The diagnostic port will accept only closed `MobileDiagnosticEvent` and `MobileDiagnosticOutcome` enums. Production Kermit output will format those constants only, with no throwable, free-form message, metadata map, identifier, or payload parameter. Search, link, notification, and operation integrations may record occurrence/outcome classes but cannot pass content through the API.

A deterministic privacy gate will scan production Kotlin/Swift sources for direct `println`/`print`/`NSLog`/Android `Log`/raw crash or breadcrumb APIs outside the single approved adapter. Behavior tests will feed canary private strings through the affected flows and assert the recording sink receives only enums. No crash-reporting SDK is added.

### 9. Evidence is a product-facing checklist plus executable gates

`docs/ACCESSIBILITY_PRIVACY_CHECKLIST.md` will name the surfaces, automated test or inspection used, Android emulator/iOS simulator configuration, result, and residual proof boundary. It is required by acceptance and will link to executable tests rather than replace them. CI will run the shared search/routing/notification/locale/contrast/privacy tests, Android instrumentation, hosted iOS XCTest, shell/entitlement checks, application builds, and existing full repository gate.

## Risks / Trade-offs

- **[No deployed universal-link domain]** → Use a build-configured synthetic test host, validate both shells, and state that signed-app association remains unverified until item 10 supplies and observes server documents.
- **[No Platform completion-subscribe contract]** → Ship an explicit integration-pending state with zero token or HTTP behavior; require a future pinned cross-repository contract before enabling it.
- **[Search responses can arrive out of order across native runtimes]** → Gate every mutation by request generation, normalized query, and expected offset in addition to cancellation.
- **[Accessibility automation cannot prove real assistive-technology experience]** → Combine semantic/contrast/target tests with emulator/simulator inspection and leave TalkBack/VoiceOver physical-device acceptance open.
- **[Refactoring shared primitives can affect many screens]** → Change only interactive and state-reporting elements, capture key state instrumentation fixtures, and keep screen ownership/navigation unchanged.
- **[Static privacy scanning can create false positives or miss aliases]** → Keep the forbidden API list narrow and supplement it with typed-boundary canary tests and final diff review.
- **[In-memory search loses results on restart]** → Accept this deliberately; retaining the query/results would add a privacy and invalidation policy beyond item 9. Retry remains explicit.

## Migration Plan

1. Land the additive shared models, native configuration, tests, and documentation with no database schema change.
2. Default development/CI builds to the synthetic link host and notification integration-pending state.
3. Validate custom-scheme compatibility, exact HTTPS routing, application builds, emulator/simulator dispatch, and full gates.
4. Roll back by reverting the additive routes/search/settings wiring; existing recent library, custom-scheme links, operation polling, queue, and local Android notifications remain compatible because no stored schema or remote registration changes.
5. In a later release change, provide a real link host plus deployed association files and a signed-app verification run. A later Platform contract change may independently replace notification integration-pending with a typed subscription implementation.

## Open Questions

- Which production hostname and web deployment will own Android and Apple association documents? This is intentionally deferred to item 10 release configuration; the parser and build-setting seam do not depend on the answer.
- Which public Platform capability and request/response schema will represent mobile completion subscriptions? Until that cross-repository decision is published and pinned, production remains integration pending.
