## ADDED Requirements

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
