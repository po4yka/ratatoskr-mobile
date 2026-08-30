# Mobile Library Curation Specification

## Purpose

Lets a paired mobile user browse recent analyses, read safely projected content, and organize fixture-backed notes, favorites, collections, and tags without confusing fixture state with synchronized Platform state.

## Requirements

### Requirement: Recent library uses the public owner-scoped contract

The client SHALL request a bounded blank-query library page only while the current device session has a fresh `library.search` capability, SHALL preserve the returned order and opaque analysis identifiers, and SHALL show loading, empty, offline, and re-pairing states without exposing another owner or inventing missing fields.

#### Scenario: Recent analyses load in Platform order
- **WHEN** an authorized generated-contract response contains read and unread analyses in recency order
- **THEN** the library list renders those analyses in the same order with their supplied titles and effective read states

#### Scenario: Library capability is unavailable
- **WHEN** the current session has no fresh `library.search` capability
- **THEN** the library surface explains that recent analyses are unavailable and sends no library request

#### Scenario: Recent library request loses authorization
- **WHEN** an authorized library request cannot recover the paired device session
- **THEN** the library surface requests re-pairing without displaying stale results as current

### Requirement: Read-state replacement is authoritative and retry safe

The client SHALL offer read and unread replacement only while `library.read_state` is fresh, SHALL send the generated exact replacement resource for the selected opaque analysis identifier, and SHALL display success only from the authoritative response. A failed or uncertain request SHALL retain the last authoritative state and expose retry rather than guessing the result.

#### Scenario: Marking an unread analysis read succeeds
- **WHEN** Platform returns authoritative `read` after the user marks one unread analysis read
- **THEN** that row displays read and unrelated favorite, note, collection, and tag presentation is unchanged

#### Scenario: Mark-read outcome is uncertain
- **WHEN** the replacement request may have reached Platform but no authoritative response is available
- **THEN** the row keeps its prior effective state and offers a retry without claiming success

### Requirement: Fixture-backed user content is useful and unmistakable

Until public favorite, note, collection, and tag contracts exist, the client SHALL provide deterministic contract-fixed fixture records through an injectable repository. Favorite toggles and bounded note edits SHALL update that fixture projection, while every affected surface SHALL visibly state that the data is a fixture preview and is not synchronized to Platform.

#### Scenario: Favorite fixture state changes independently
- **WHEN** the user toggles favorite on a fixture analysis whose read state is unread
- **THEN** the fixture reports the new favorite value while its read state remains unread and the surface retains the integration-pending notice

#### Scenario: A note is edited within fixture bounds
- **WHEN** the user saves a note within the documented character limit for a fixture analysis
- **THEN** reopening that analysis from the same fixture repository returns the exact note and labels it unsynchronized fixture content

#### Scenario: An oversized note is refused
- **WHEN** the user attempts to save a note beyond the fixture contract limit
- **THEN** the note remains unchanged and the reader displays a safe validation error

### Requirement: Analysis reader renders supplied evidence as inert content

The reader SHALL display the supplied title, analysis summary, key points, text blocks, provenance source and acquisition label, warnings, tags, read state, and favorite state. It SHALL render supplied text as inert text, SHALL never infer provenance or canonical provider identity, and SHALL expose unavailable or partial fixture fields explicitly.

#### Scenario: Article analysis includes provenance and warnings
- **WHEN** a fixture article supplies analysis content, an explicit source address, an acquisition label, and extraction warnings
- **THEN** the reader displays each supplied field and warning without executing markup or deriving additional authority

#### Scenario: Analysis detail is unavailable
- **WHEN** a live library summary has no contract-backed or matching fixture detail
- **THEN** the reader explains that full content integration is pending while preserving the summary identity and read-state actions

### Requirement: Collections and tags edit fixture membership without collateral changes

The client SHALL browse deterministic fixture collections and tags with item counts, open their member lists, and add or remove one fixture analysis membership idempotently. A membership mutation SHALL change only the named relation, SHALL preserve read, favorite, note, and unrelated membership state, and SHALL expose failure without a false success state.

#### Scenario: Collection membership is added idempotently
- **WHEN** the user adds the same fixture analysis to one collection twice
- **THEN** the collection contains one membership, its count increases once, and unrelated item state is unchanged

#### Scenario: Tag membership is removed
- **WHEN** the user removes one tag from a fixture analysis that has multiple tags
- **THEN** only that tag relation and its count change while the other tags, collections, note, favorite, and read state remain unchanged

#### Scenario: Membership mutation fails
- **WHEN** the fixture repository refuses a collection or tag membership mutation
- **THEN** the visible membership rolls back to the last confirmed projection and a safe retryable error is shown

### Requirement: Live and fixture evidence remain separate

The application SHALL consume generated types for the published library page and read-state resource, SHALL make no undeclared Platform request for fixture-backed fields, and SHALL document and test which behaviors are live-contract, fixture-contract, local emulator/simulator, and unverified production evidence.

#### Scenario: Fixture curation performs no network mutation
- **WHEN** a user toggles fixture favorite, edits a fixture note, or changes fixture collection or tag membership
- **THEN** no Platform transport call is issued and the UI continues to state that server synchronization is pending

### Requirement: Full-text search uses the public owner-scoped contract

The client SHALL expose full-text library search only while the paired session has a fresh `library.search` capability. It SHALL trim the submitted query, accept 1 through 512 Unicode scalar values, request the pinned `GET /v1/library/search` contract without a tenant selector, preserve Platform result ordering and supplied read state, snippet, and score, and distinguish live Platform results from fixture-only content.

#### Scenario: Valid search renders ranked live results

- **WHEN** a paired user submits `durable queue` and Platform returns a bounded page of matching analyses
- **THEN** the search surface shows those items in Platform order with their supplied snippets and read states and identifies them as live Platform results

#### Scenario: Invalid query sends no request

- **WHEN** a user submits a blank query or a query longer than 512 Unicode scalar values
- **THEN** the search surface shows bounded validation guidance and sends no Platform request

#### Scenario: Search capability is unavailable

- **WHEN** the current session has no fresh `library.search` capability
- **THEN** the search surface explains that search is unavailable and does not substitute fixture matches or send a Platform request

### Requirement: Search paging and request races remain deterministic

The client SHALL request pages with a limit from 1 through 100 and a non-negative offset, SHALL advance only when the authoritative page reports more results, SHALL append each accepted page exactly once, and SHALL ignore a response for a query or page that is no longer current. A refresh or changed query SHALL replace rather than mix result sets.

#### Scenario: Next page appends once

- **WHEN** the current result page reports more results and the user requests the next page twice before the first request completes
- **THEN** the client sends one next-page request and appends the accepted items once in Platform order

#### Scenario: Older query completes after newer query

- **WHEN** a request for `alpha` completes after the user has submitted and received results for `beta`
- **THEN** only the `beta` results remain visible and the stale `alpha` response changes no state

#### Scenario: Search becomes unauthorized

- **WHEN** a search request cannot recover the paired device session
- **THEN** the search surface requests re-pairing and does not display the failed response or prior results as current

### Requirement: Search exposes first-class non-success states

The search surface SHALL expose idle, loading, empty, offline/retryable, unavailable/permanent, and re-pairing states. It SHALL retain the submitted query for an explicit retry but SHALL NOT claim cached or fixture content is an authoritative response.

#### Scenario: Search has no matches

- **WHEN** Platform accepts a valid query and returns an empty first page
- **THEN** the surface states that no matches were found for the current query without showing recent or fixture items as matches

#### Scenario: Search is temporarily offline

- **WHEN** a valid search fails with a retryable transport or dependency outcome
- **THEN** the surface keeps the query, offers an explicit retry, and does not silently issue repeated requests
