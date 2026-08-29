# Mobile GitHub Catalog Actions Specification

## Purpose

Defines the mobile behavior for capability-gated GitHub catalog discovery, repository detail, explicitly confirmed tracking and provider writes, and truthful action outcomes.

## Requirements

### Requirement: Catalog discovery is bounded, capability-gated, and truthful

The client SHALL expose GitHub catalog browsing and bounded case-insensitive search only while the device is paired and the current Platform capability document contains a non-stale, structurally valid GitHub service document advertising repository preview. Until Platform publishes a public list/search contract, every browse/search row SHALL come from a deterministic contract fixture, SHALL be labelled as unsynchronized and integration-pending, and SHALL issue no catalog-list network request.

#### Scenario: Current GitHub capability exposes fixture catalog search
- **WHEN** the paired device has a current valid GitHub service capability and the user submits a bounded query matching a fixture repository name or description
- **THEN** the catalog shows only matching contract-fixture rows in stable order and visibly states that browse/search is not synchronized with Platform

#### Scenario: Empty query browses the bounded fixture catalog
- **WHEN** the paired device has a current valid GitHub service capability and the user clears the search query
- **THEN** the complete bounded fixture catalog is shown in deterministic order without a Platform list request

#### Scenario: GitHub capability is absent or stale
- **WHEN** the device is unpaired, the GitHub service entry is missing, stale, malformed, or does not advertise repository preview
- **THEN** the GitHub surface shows pairing-required or capability-unavailable state and exposes no repository actions

#### Scenario: Search input exceeds its bound
- **WHEN** the user enters an over-limit search query
- **THEN** the client rejects the query without truncating it, sending a request, or changing the last accepted catalog results

### Requirement: Repository detail consumes strict preview contracts through Platform

For a selected canonical GitHub repository URL, the paired client SHALL request preview only through the authenticated Platform `/v1/gh` public boundary, SHALL follow no redirect, SHALL recover authorization at most once using the existing device session flow, and SHALL validate every contract field and advertised action before presentation. Unknown members, invalid stable identity, invalid aliases or URLs, duplicate or unknown actions, unsafe display strings, and capability/preview action mismatches SHALL fail closed.

#### Scenario: Valid repository preview renders metadata
- **WHEN** Platform returns the contract-fixed preview for a selected canonical repository under a current matching capability document
- **THEN** detail presents the stable `owner/name`, canonical URL, optional description and language, non-negative star count, opaque connected-account reference when present, and only the intersection of advertised actions

#### Scenario: Preview response drifts from the pinned contract
- **WHEN** Platform returns an unknown member, invalid target, unsafe display value, unknown action, duplicate action, or an action absent from the current capability document
- **THEN** the client exposes a safe invalid-response state and submits no action

#### Scenario: Preview authentication is revoked
- **WHEN** preview receives unauthorized and device-session recovery also reports revocation
- **THEN** stored authorization and GitHub capability state are cleared and the surface transitions gracefully to re-pairing required

### Requirement: Track and star require fresh one-shot confirmation

Selecting `track` or `star` SHALL create only a pending confirmation and SHALL NOT perform a network action. The `track` prompt SHALL name the repository and state that Ratatoskr will request desired backup tracking without claiming a completed backup or a GitHub write. The `star` prompt SHALL name the repository, the opaque connected-account reference, the provider-star write, metadata update, and desired-backup request. Confirmation SHALL be accepted once only while target, account, preview actions, and current capability actions still match; cancellation, replay, replacement, or stale context SHALL perform no action.

#### Scenario: Track selection waits for explicit consent
- **WHEN** the user selects available `track` on a repository detail
- **THEN** the client shows the exact Ratatoskr tracking effects and sends no action until the user presses the confirmation control

#### Scenario: Star selection discloses the external write
- **WHEN** the user selects available `star` with a connected-account reference
- **THEN** the client shows the exact repository, opaque acting-account reference, external GitHub star, metadata, and desired-backup effects and sends no action until explicit confirmation

#### Scenario: Confirmation is cancelled or replayed
- **WHEN** the user cancels a pending confirmation or tries to confirm the same pending selection more than once
- **THEN** no action is sent for cancellation or replay and the consumed selection cannot be reused

#### Scenario: Confirmation context changes
- **WHEN** the repository target, connected account, preview actions, or current capability actions differ from the pending confirmation
- **THEN** the pending confirmation is invalidated and the user must review a fresh detail before any action can be submitted

### Requirement: Confirmed actions are idempotent and contain no provider credential

The client SHALL create one bounded opaque mobile confirmation-evidence reference and one bounded idempotency key for the confirmed logical action, SHALL preserve that identity across an uncertain retry, and SHALL send the exact stable preview target and account reference required by the selected mode. The request SHALL contain no GitHub token, provider error body, mutable policy, or hidden content.

#### Scenario: Confirmed star sends the contract request once
- **WHEN** the user confirms an eligible star selection
- **THEN** Platform receives one action request containing `star`, the exact stable target, opaque account reference, fresh confirmation evidence, and idempotency key, with no provider credential

#### Scenario: Confirmed request outcome is unknown
- **WHEN** transport ends after the confirmed request may have reached Platform but before a valid result is observed
- **THEN** the client presents outcome-unknown and any explicit retry reuses the same idempotency key rather than constructing a second logical action

### Requirement: Action outcomes preserve component truth

The client SHALL validate and render the aggregate plus independent metadata, provider-star, and desired-backup outcomes from the shared contract. It SHALL distinguish succeeded, already-applied, accepted, refused, failed, and skipped with their safe reason classes, SHALL reject an aggregate inconsistent with component facts, and SHALL never present accepted desired policy as completed backup.

#### Scenario: Star succeeds while desired backup fails
- **WHEN** the action result reports metadata succeeded, provider star succeeded, desired backup failed because its dependency is unavailable, and aggregate partial
- **THEN** the detail shows both successful components, the failed desired-backup component with a safe reason, and an overall partial result

#### Scenario: Desired policy is accepted
- **WHEN** the desired-backup component reports accepted
- **THEN** the client states that tracking policy was accepted for publication and does not claim that repository bytes were backed up

#### Scenario: Aggregate contradicts component facts
- **WHEN** a response labels the action succeeded while an attempted component is refused or failed
- **THEN** the client rejects the result as invalid and does not replace it with a fabricated success or partial outcome

### Requirement: Shared presentation remains inside thin native shells

Android and iOS SHALL host the same shared Compose GitHub catalog, detail, confirmation, loading, empty, unavailable, re-pairing, failure, outcome-unknown, and component-result states. The new feature SHALL add no native GitHub credential store, direct provider request, Android permission, iOS entitlement, or platform-specific feature UI.

#### Scenario: Android and iOS render a partial action fixture
- **WHEN** either thin application shell hosts the shared GitHub detail with the contract-fixed partial result
- **THEN** the same three component facts and partial aggregate are available to the user without a direct GitHub connection
