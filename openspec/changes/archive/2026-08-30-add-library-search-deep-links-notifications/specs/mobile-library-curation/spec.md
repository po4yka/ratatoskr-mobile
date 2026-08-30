## ADDED Requirements

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
