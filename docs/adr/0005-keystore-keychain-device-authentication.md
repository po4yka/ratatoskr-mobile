# ADR-0005: Keystore/Keychain device authentication

Status: Accepted

Date: 2026-08-28

## Context

Platform identity issues a mobile installation a device root secret plus short-lived access and
single-use rotating refresh credentials. The common client must coordinate their lifecycle, while
the secret at rest and its operating-system policy remain platform authority. A refresh response can
be lost after Platform has already consumed the old link, and another primary session can revoke the
device at any time.

## Decision

Use one common `DeviceSessionManager` guarded by a coroutine mutex for pairing, restoration,
refresh, device-root recovery, capability discovery, sign-out, and revocation. It consumes only the
pinned public Platform contract at a canonical HTTPS origin; native Ktor clients disable redirects.
Before presenting a refresh link, the coordinator persists that it is no longer usable; restoration
of such a record goes directly to recovery. Each link is therefore presented at most once across
process death. Any non-success refresh receives at most one recovery login with the stored device
identifier and root secret, and returned identity must match the stored user and device before
replacement.

Persist one serialized origin-bound credential record through `SecureCredentialStorage`:

- Android encrypts it using AES-GCM and a non-exportable Android Keystore key, retaining only
  version, IV, and ciphertext in private backup-disabled preferences.
- iOS stores the opaque bytes as a generic-password Keychain item with
  `AfterFirstUnlockThisDeviceOnly` accessibility and synchronization disabled.

Capability documents are memory-only and scoped to the current device session. Unknown names are
preserved as data, but only a closed set of known names can enable application behavior. Missing or
stale capability state fails closed. Refusal of both refresh and device-root recovery clears the
credential record and capabilities and exposes re-pairing required without deleting unrelated local
data.

## Alternatives

- Plain preferences, DataStore, Room, or user defaults do not provide a native secret boundary.
- Separate stored fields permit partial refresh rotation to become observable after process death.
- Retrying an uncertain refresh can replay a consumed single-use link and revoke the session.
- Persisting capabilities risks reusing authorization across origin, user, or replacement sessions.
- Sharing Keychain items through synchronization moves a device credential beyond the paired
  installation.

## Consequences

The common state machine is deterministic and testable, while platform source sets remain explicit
about security policy. Android instrumentation and app-hosted iOS Simulator XCTest are required to
prove the native stores; common fakes are not native storage evidence. The device root secret remains
available for bounded recovery, so a future biometric local gate must wrap access without replacing
server authorization.

## Android/iOS lifecycle

The thin Android Activity and iOS SwiftUI/UIKit shell create platform-specific storage and Ktor
adapters and pass the coordinator to shared Compose. Share targets/extensions are out of scope and do
not receive credentials through this decision. Application restart restores the single record and
immediately discovers current-session capabilities.

## Security and privacy

Credential values never appear in logs, errors, UI state, analytics, URLs, backups, or test output.
Pairing input is cleared after submission. Storage failures expose only operation/status metadata.
Local sign-out does not claim server revocation, and revocation does not erase unrelated future queue
or cache data.

## Compatibility and schema impact

This consumes the existing first-version Platform identity and capability contract. It adds no API
version, database schema, migration, provider credential, or backend behavior.

## Validation

- generated-contract pairing outcome and HTTPS-origin tests;
- atomic rotation, concurrent caller, recovery, revocation, and capability tests on common targets;
- Android Emulator Keystore round-trip/replace/delete instrumentation;
- app-hosted iOS Simulator Keychain round-trip/replace/delete/policy XCTest;
- both application builds, contract drift/mutation checks, lint, and CI parity.
