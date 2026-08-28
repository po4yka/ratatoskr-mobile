## Context

See `proposal.md` for motivation. ADR-0001 already assigns identity models, API adapters, state, and shared Compose presentation to KMP while assigning Keystore/Keychain authority to native code. Platform's pinned OpenAPI and Platform ADR-0016 define one `mobile` device kind, a one-time pairing response, single-use refresh rotation, device-root recovery, uniform authorization refusal, and per-session capability discovery.

## Goals / Non-Goals

**Goals:**

- Keep one serial, testable device-session state machine in common code.
- Make native secure storage the only persistence path for device and session secrets.
- Consume the pinned Platform identity/capability wire shapes without local endpoint or capability invention.
- Give the shared Compose app an honest paired, refreshing, revoked, and capability-availability projection.

**Non-Goals:**

- Creating pairing codes or approving a second device from mobile; the new installation only presents a code approved by an existing primary session.
- Share extensions, capture queue, device-list management, provider OAuth, biometric gating, push registration, or server-side identity changes.
- Persisting capabilities across sessions or enabling features from stale capability evidence.

## Decisions

### One common session coordinator serializes every credential mutation

A shared coordinator owns pairing, restoration, refresh, device-root recovery, sign-out, revocation, and capability loading behind one coroutine `Mutex`. Before presenting a refresh link it persists that the link is no longer usable; restoration of such a record goes directly to device-root recovery. It publishes immutable `StateFlow` values only after secure-storage writes complete. Concurrent authorization callers therefore share one refresh result, and no caller or restarted process can race or replay a spent refresh link.

Alternatives considered: allowing each repository to refresh independently risks replaying a single-use link and revoking the entire session; an actor adds more machinery than the current single-process module needs, while the mutex preserves the same serialization contract.

### Pairing and transport are closed over the pinned public contract

The shared Ktor adapter sends `PairDevice(kind = "mobile")`, `RefreshSession`, and `OpenDeviceSession`, and reads `CapabilityDocument`. Redirects are disabled. A Platform origin is accepted only when it is a canonical HTTPS origin with no user info, query, fragment, or non-root path. HTTP status handling is explicit: pairing `400`, `401`, and `504` remain distinct safe outcomes; credential-bearing bodies are never included in exception descriptions.

Alternatives considered: a handwritten duplicate wire model would drift from generated contracts; accepting arbitrary base URLs or redirects could move credentials to a different authority.

### Uncertain refresh never replays the old link

An I/O failure can happen after Platform atomically rotated the link. The coordinator therefore does not retry that refresh token. It performs at most one recovery login with the device root secret, which creates a fresh session chain. Pairing has no equivalent recovery because an uncertain response does not reveal the device ID/root secret; it requires a new code and does not claim success.

Alternatives considered: retrying explicit `504` only is contractually allowed because Platform says nothing changed, but using one recovery policy for all non-success refresh results is safer and bounded while the root credential remains valid.

### Secure storage persists one opaque origin-bound record

The shared storage interface reads, replaces, and clears one serializable credential record. Android encrypts it with AES-GCM under a non-exportable Android Keystore key and stores only IV+ciphertext in private preferences. iOS stores the opaque bytes as one generic-password Keychain item with `AfterFirstUnlockThisDeviceOnly` accessibility and synchronization disabled. Neither platform stores tokens in DataStore, Room, defaults, logs, descriptions, or backup-enabled files.

Alternatives considered: AndroidX Security Crypto is deprecated in favor of direct platform APIs for this narrow case; storing fields separately creates observable partial rotation; KMP settings are not a secure secret store.

### Capability cache is in-memory and session-scoped

The latest `CapabilityDocument` is retained with freshness and session identity in common state. It is discarded before pairing/recovery state changes and marked stale after a discovery failure. Application layers may display stale context but `isAvailable` answers false unless the snapshot is fresh for the active session. Unknown names remain preserved as data but never turn on local behavior.

Alternatives considered: durable caching contradicts the contract's instruction to read capabilities on every session and increases cross-origin/user leakage risk.

### Native secure-storage evidence is platform-specific

Android instrumentation executes the real Keystore round trip on an emulator; host unit tests cover the common coordinator with an in-memory storage seam. The unhosted Kotlin/Native iOS Simulator runner compiles the Keychain adapter but Security returns `errSecNotAvailable`; an app-hosted XCTest target therefore executes the real Simulator Keychain round trip without a production signing profile. CI runs both boundaries and documents that neither is physical-device evidence.

## Risks / Trade-offs

- [A process dies after Platform rotates but before secure storage replacement] → The retained device root secret recovers a new session on next startup; the old refresh link is never replayed after an observed uncertain response.
- [Secure-storage replacement itself fails after a successful server rotation] → The coordinator publishes no new session and next startup uses device-root recovery; errors remain secret-free.
- [Android emulator acceleration is unavailable on a runner] → Keep instrumentation as a separately named gate and compile it everywhere; use the supported hosted KVM setup rather than downgrading to a host fake.
- [Keychain state leaks between test runs] → Tests use a unique service/account namespace and delete it before and after each round trip.
- [Capability state becomes stale immediately after a successful fetch] → Feature checks require current-session freshness and authorization failures invalidate the snapshot; Platform remains authoritative.

## Migration Plan

1. Land shared identity/session behavior and native stores without changing Platform or its pinned OpenAPI.
2. Wire both existing thin shells to platform factories and shared Compose pairing state.
3. Run common, Android emulator, iOS Simulator, app-build, contract-drift, lint, and OpenSpec gates.
4. Roll back by reverting this client change; no server data, schema, API version, or migration is altered.
