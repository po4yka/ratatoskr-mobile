## Purpose

Give mobile users a contract-backed shared Compose view of their recent Platform operations and one operation's current progress without turning polling or cached client state into authoritative truth.

## ADDED Requirements

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
