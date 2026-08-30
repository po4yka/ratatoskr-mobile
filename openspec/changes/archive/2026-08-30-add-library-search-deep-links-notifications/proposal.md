## Why

Ratatoskr Mobile can browse recent analyses and open a small custom-scheme route set, but it does not yet expose the public full-text Knowledge search, verified-web-link route families, or a truthful cross-platform notification preference surface. The existing shared Compose screens also need a focused accessibility and privacy pass before release-oriented integration begins.

## What Changes

- Add capability-gated full-text library search through the pinned public `GET /v1/library/search` contract, with bounded query input, deterministic paging, stale-request suppression, explicit empty/offline/reauth states, and fixture-only behavior only where the public contract is absent.
- Extend the shared exact route table to accept canonical HTTPS app/universal links for analyses, collections, and GitHub repositories while preserving strict opaque-ID/path validation and the existing custom-scheme routes. Native shells declare only build-configured link hosts; live domain association remains release/deployment evidence rather than a repository claim.
- Add an explicit notification preference and permission-state surface. A Platform completion subscription is enabled only when a pinned public capability and subscribe path exist; the current contract therefore presents an integration-pending no-op and sends no push token or guessed request. Existing local Android operation notifications remain privacy-safe.
- Apply shared Compose and native-shell accessibility improvements for screen-reader names/state, headings, focus order, 48 dp Android and 44 pt iOS-equivalent targets, scalable text, contrast, reduced-motion-safe behavior, and English/Russian string readiness.
- Add a privacy-safe diagnostic boundary and review gates that prevent search text, URLs, titles, notes, filenames, identifiers, tokens, and response bodies from entering logs, crash metadata, notification payloads, or deep links.
- Commit an evidence-based accessibility/privacy checklist and update architecture, interfaces, testing, and implementation-plan status without claiming physical-device, live-domain, push-provider, or live-Platform proof.

## Capabilities

### New Capabilities

- `mobile-accessibility-privacy`: Defines accessible shared/native presentation and content-free diagnostics, notifications, links, and crash-reporting behavior.

### Modified Capabilities

- `mobile-library-curation`: Adds full-text search, paging, request-race, capability, and truthful fixture/live authority behavior to the existing library contract.
- `mobile-content-routing`: Adds canonical HTTPS analysis, collection, and repository routes plus strict native app/universal-link intake.
- `mobile-operation-status`: Adds explicit notification permission/preference state and capability-gated Platform completion subscription behavior.

## Impact

- Shared KMP: library transport/repository/store models, search Compose UI, route parsing/navigation, notification policy models, localized strings, accessibility semantics, and privacy-safe diagnostics.
- Android: verified-link manifest configuration, intent intake, notification permission adapter, Compose instrumentation/accessibility assertions, and privacy checks.
- iOS: Associated Domains configuration, `NSUserActivity`/URL intake, notification permission adapter, hosted XCTest/simulator evidence, and native accessibility/privacy checks.
- Contracts: consumes the already pinned `library-search-read-state` Platform OpenAPI surface; no Platform schema is invented. Completion subscription remains unavailable until a future public Platform contract and capability are pinned.
- Documentation/CI: accessibility/privacy checklist, architecture/interface/testing/plan updates, OpenSpec validation, shared/native tests, app builds, and deterministic emulator/simulator smoke. No production dependency, database migration, provider credential, or server-side association file is added.
