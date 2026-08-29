## Why

Ratatoskr Mobile can already submit captures and inspect operations, but a paired user cannot browse the resulting library, read analysis content, or organize it. Platform now publishes the bounded library recency/read-state contract, while favorite, note, collection, tag, full-reader, social-reader, and AI-archive-reader APIs remain absent, so this slice must consume the live surface where it exists and use an explicit contract-fixed fixture boundary everywhere else.

## What Changes

- Update the pinned Platform OpenAPI and generated Kotlin models to consume the published library list and read-state resource without hand-written wire types.
- Add a shared Compose recent-library surface with authoritative read-state replacement and fixture-backed favorite behavior, including loading, empty, offline, reauthentication, and integration-pending states.
- Add a safe shared reader for fixture-projected analysis content, supplied provenance and warnings, and editable fixture-backed notes.
- Add fixture-backed collection and tag browsing plus item membership editing through an injectable repository whose production replacement cannot silently invent undeclared Platform calls.
- Add an allowlisted, opaque-identifier routing table for article analyses, supported social sources, and AI-archive items; unsupported or unavailable contract families produce an explained state instead of opening the wrong reader.
- Keep fixture-backed fields visibly marked as integration pending and document the boundary between live Platform behavior and contract-fixed fixtures.

## Capabilities

### New Capabilities

- `mobile-library-curation`: Recent analyses, read/favorite state, safe analysis reading, notes, collections, tags, mutations, and the live-versus-fixture evidence boundary.
- `mobile-content-routing`: Validated deep-link routing for article, social-source, and AI-archive reader destinations with capability/contract-aware refusal.

### Modified Capabilities

- `mobile-project-bootstrap`: Extend the shared and platform CI gate to cover generated library contracts and library/routing behavior on Android and iOS.

## Impact

- Affects the pinned Platform OpenAPI provenance, generated shared Kotlin models, Ktor library adapter, capability mapping, shared UDF stores, shared Compose navigation/surfaces, Android and iOS application graphs, tests, CI descriptions, and mobile architecture/testing/interface documentation.
- Consumes the workspace `library-search-read-state` contract already published by Platform; it introduces no new cross-repository endpoint, capability name, database migration, provider token handling, or internal-service call.
- Favorite, note, collection, tag, full analysis content, social content, and AI-archive content remain deterministic fixture projections until their public Platform contracts are published. Their UI must say so and cannot be presented as synchronized server state.
