# Mobile Operation Status Specification

## Purpose

Give mobile users a contract-backed shared Compose view of their recent Platform operations and one operation's current progress without turning polling or cached client state into authoritative truth.

## Requirements

### Requirement: Operation list renders authorized Platform summaries

The client SHALL render a refreshable newest-first list from the generated public Platform operation-list contract, including operation kind, lifecycle status, accepted or changed time, and available stage or progress. It SHALL expose loading, empty, offline, authorization, and safe error states without rendering request payloads, raw credentials, or untrusted backend detail.

#### Scenario: Fixture operation page renders
- **WHEN** the client decodes a valid synthetic operation-list fixture containing running, partial, failed, and completed summaries
- **THEN** the list renders each documented lifecycle label and available bounded progress fields in Platform order

#### Scenario: Operation list is unavailable offline
- **WHEN** a refresh cannot reach Platform and no authoritative response is available
- **THEN** the surface retains any clearly marked stale presentation context and exposes an offline retry action without declaring a newer status

### Requirement: Operation detail renders the complete public snapshot safely

The client SHALL load one authorized generated `OperationSnapshot` by identifier and render its lifecycle, stage, progress, result count, warning count, and safe failure presentation. It SHALL distinguish succeeded, partially succeeded, failed, cancelled, and reauthentication-required outcomes and SHALL NOT infer completion from upload or acceptance alone.

#### Scenario: Fixture snapshots render distinct outcomes
- **WHEN** valid synthetic detail fixtures represent running, succeeded, partially succeeded, failed, and cancelled operations
- **THEN** the detail surface renders each outcome distinctly with contract-derived progress and counts and without exposing fixture payload bodies as trusted rich content

#### Scenario: Operation cannot be read
- **WHEN** Platform returns not-found-or-not-owned for the requested identifier
- **THEN** the detail surface shows one non-enumerating unavailable result and retains no unauthorized snapshot

### Requirement: Active detail polling is bounded and monotonic

While a non-terminal detail is visible in a resumed app, the client SHALL refresh it through the public operation progress endpoint using bounded adaptive polling, SHALL apply only non-regressing Platform snapshots, and SHALL stop automatic polling when the route leaves, the app is not resumed, the outcome is terminal, authorization requires repair, or the retry bound is reached. It MAY use the public event stream in a later implementation without changing this observable behavior.

#### Scenario: Visible running operation advances
- **WHEN** polling receives valid newer running and terminal fixtures with increasing Platform-observed change times
- **THEN** the detail advances monotonically to the terminal state and stops polling

#### Scenario: Older fixture arrives after newer status
- **WHEN** polling receives a duplicate or older snapshot after a newer projection
- **THEN** the visible and persisted operation status does not regress

#### Scenario: Detail leaves the foreground
- **WHEN** the user navigates away or the app leaves resumed state while an operation is non-terminal
- **THEN** foreground polling is cancelled and later resume performs a bounded refresh rather than maintaining an unbounded background loop

### Requirement: Notification availability and permission are explicit state

The client SHALL expose notification preference separately from native permission and Platform subscription availability. The observable states SHALL distinguish unsupported-by-current-contract, disabled-by-user, permission-not-requested, permission-denied, and enabled. Permission SHALL be requested only after an explicit user action while notification delivery is otherwise available.

#### Scenario: Current Platform has no subscribe path

- **WHEN** the pinned capability and API contract expose no completion-notification subscribe path
- **THEN** the notification surface states that server completion notifications are integration pending, requests no native permission, registers no token, and sends no guessed Platform request

#### Scenario: User explicitly enables an available notification path

- **WHEN** a contract-fixed available subscription fixture is active, permission is not determined, and the user selects Enable notifications
- **THEN** the client requests native permission once and reflects the authoritative native permission result

#### Scenario: Permission is denied

- **WHEN** native notification permission is denied
- **THEN** operation status continues normally, no delivery is claimed, and the surface offers platform-settings guidance without repeatedly prompting

### Requirement: Completion subscription is capability and contract gated

The client SHALL register a completion-notification subscription only when the current paired session exposes the exact pinned capability and public subscribe contract. It SHALL never invent an endpoint, send a device token to an internal service, or treat local polling as a remote subscription. Revocation, logout, clear-data, user disablement, or capability disappearance SHALL cancel local notification work and remove any locally stored opaque subscription handle.

#### Scenario: Capability disappears after enablement

- **WHEN** a previously available completion-subscription capability is absent from the refreshed current-session snapshot
- **THEN** the client stops subscription work, marks the feature unavailable, and does not reuse a stale handle or token

#### Scenario: Device session is revoked

- **WHEN** Platform proves device revocation while notifications are enabled
- **THEN** notification state is cleared together with the local session and no notification action can reopen private status under the revoked session

### Requirement: Delivered operation notifications disclose no user content

A local or remote completion notification SHALL contain only generic Ratatoskr outcome text and an immutable route carrying a validated opaque operation identifier. It SHALL NOT contain a search query, URL, title, note, filename, provider identity, result excerpt, credential, or raw error. Opening it SHALL perform an authenticated operation read and SHALL handle absent or not-owned state without enumeration.

#### Scenario: Completed notification is privacy safe

- **WHEN** a completed operation contains a private URL, title, note, filename, and backend error
- **THEN** the delivered notification and route contain none of those values and open only the authenticated operation-detail destination
