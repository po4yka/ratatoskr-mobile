## MODIFIED Requirements

### Requirement: Revocation terminates the local session gracefully

The client SHALL treat refusal of both session refresh and device-root recovery as proven device
revocation, atomically publish an erase-pending boundary, disable authenticated work, and complete
the coordinated deletion of credentials, capabilities, queue data, transfer state, staged files,
App Group handoff data, caches, preferences, notifications, and native schedules before exposing an
unpaired empty state. An explicit local sign out SHALL still clear only credential and session-scoped
capability state unless the user separately confirms the destructive clear-data action.

#### Scenario: Device revoked elsewhere
- **WHEN** an authenticated request is refused, refresh cannot restore authorization, and Platform also refuses the stored device root secret
- **THEN** the client cancels local work, completes the replay-safe full local wipe, and exposes re-pairing with no prior account content or credential recoverable

#### Scenario: Explicit local sign out
- **WHEN** the user signs out this installation locally without confirming clear data
- **THEN** the client deletes its secure credential record and clears session-scoped capability state without claiming Platform revocation or deleting unrelated queued user data
