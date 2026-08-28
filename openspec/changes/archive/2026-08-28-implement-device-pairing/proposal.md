## Why

Ratatoskr Mobile cannot call authenticated Platform APIs until a user can pair an installation and the client can preserve, rotate, and revoke device credentials without exposing them outside OS secure storage. Capability discovery must begin with that authenticated session so app layers never present unavailable Platform behavior as usable.

## What Changes

- Add the new-device exchange defined by Platform's public pairing contract, including uniform handling of refused, expired, spent, superseded, and kind-mismatched pairing codes.
- Add a shared device-session manager that persists one origin-bound credential set through a native secure-storage interface, refreshes it with single-use rotation, recovers with the device root secret, and clears it when Platform reports revocation.
- Add Android Keystore and iOS Keychain implementations with platform round-trip tests and explicit backup/synchronization protections.
- Add authenticated Platform capability discovery with a session-scoped in-memory cache exposed as conservative app state.
- Add a shared Compose pairing/session surface inside the existing thin native shells; provider login, capture, and share-extension behavior remain absent.
- Extend CI and repository documentation so shared behavior, Android instrumentation compilation/execution, iOS Simulator Keychain coverage, and both application builds remain gated.

## Capabilities

### New Capabilities

- `device-identity`: Mobile pairing, origin-bound secure credentials, refresh rotation, device-session recovery, revocation, and capability discovery behavior.

### Modified Capabilities

- `mobile-project-bootstrap`: The previously empty shared application root now receives native secure-storage adapters and exposes paired/capability state while preserving thin Android/iOS shells.

## Impact

The shared KMP module gains the Platform identity API adapter, session/capability state, and shared Compose presentation. Android gains a Keystore-backed credential store and instrumentation coverage; iOS gains a non-synchronizing, device-only Keychain store and simulator coverage. The public Platform contract is consumed unchanged from the pinned generated types; no backend, database, share extension, capture queue, provider credential, API-major, or migration change is introduced.
