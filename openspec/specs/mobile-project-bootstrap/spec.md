# Mobile Project Bootstrap Specification

## Purpose

Provide a reproducible, contract-aligned build foundation on which Android and iOS Ratatoskr features can be implemented and continuously validated.

## Requirements

### Requirement: Android application shell builds shared content

The repository SHALL provide an Android application shell that compiles into a debug application, supplies an Android Keystore-backed credential adapter to shared Ratatoskr Compose content, and does not own duplicate pairing presentation or session state.

#### Scenario: Android debug build
- **WHEN** the documented Android build command runs in a clean checkout with the pinned toolchain
- **THEN** it exits successfully and produces a Ratatoskr debug application artifact that hosts shared pairing/session content with the native secure-storage adapter

### Requirement: iOS application shell builds shared content

The repository SHALL provide an iOS application shell that builds for the iOS Simulator, links the shared framework, supplies a device-only non-synchronizing Keychain credential adapter, and hosts shared Ratatoskr Compose content without duplicate pairing presentation or session state.

#### Scenario: iOS simulator build
- **WHEN** the documented unsigned simulator build command runs in a clean checkout with the pinned toolchain
- **THEN** it exits successfully and produces a Ratatoskr simulator application artifact linked to the shared framework and native secure-storage adapter

### Requirement: Shared module remains usable by both platform shells

The shared module SHALL expose the common Compose entry point, generated public Platform contract types, device-session and capability state through native adapter seams to Android and iOS, and its common tests SHALL execute on supported platform test targets.

#### Scenario: Shared contract smoke test
- **WHEN** the shared-module test suite deserializes a synthetic response represented in the pinned public Platform document
- **THEN** the generated Kotlin type preserves the documented field values on both configured test targets

#### Scenario: Shared Compose framework link
- **WHEN** the shared framework is linked for the iOS Simulator and the Android application is compiled
- **THEN** both consumers resolve the same shared Compose entry point and pairing/session state without a platform-specific duplicate

#### Scenario: Native credential adapters compile at the shared seam
- **WHEN** Android and iOS platform test sources compile against the shared secure-storage interface
- **THEN** both native implementations satisfy the same origin-bound credential round-trip contract

### Requirement: Platform contracts are pinned and reproducible

The repository SHALL record the exact Platform producer revision and SHA-256 digest for its committed public OpenAPI document, and SHALL derive committed Kotlin contract types deterministically from that document.

#### Scenario: Clean generated contract tree
- **WHEN** contract generation runs twice from the committed pinned document
- **THEN** both generated Kotlin trees are byte-for-byte identical to each other and to the committed generated tree

#### Scenario: Mutated pinned document is rejected
- **WHEN** the contract drift test changes the contents of a temporary copy of the pinned OpenAPI document without changing its recorded producer revision and digest
- **THEN** the drift check exits non-zero and identifies the pinned document mismatch

#### Scenario: Stale generated contract is rejected
- **WHEN** a temporary generated Kotlin contract file differs from the generator output for the pinned document
- **THEN** the drift check exits non-zero and identifies generated output drift

### Requirement: KMP ownership boundary is recorded

The repository SHALL contain an accepted ADR assigning shared Compose presentation, navigation/state, public API models/client, and capture-queue rules to KMP while assigning operating-system lifecycle, share integrations, secure storage, background scheduling, file access, notifications, and platform accessibility integration to native adapters.

#### Scenario: Architecture boundary validation
- **WHEN** the architecture documentation check inspects ADR-0001
- **THEN** every required shared and native responsibility is assigned exactly once and the rationale for shared Compose UI and native lifecycle ownership is present

### Requirement: Product CI enforces the bootstrap gate

The product CI SHALL lint Kotlin and Swift sources, verify generated contract drift including the mutation test, run shared device-identity, generated-contract, capture-model, retry, operation-projection, capture-queue, Android-share staging, authenticated submission, and operation-status fixture tests, create and reopen the current local database schema on Android and iOS test targets, execute Android Share Target/deep-link/WorkManager instrumentation and applicable native secure-storage tests, build the Android application, build the unsigned iOS Simulator application, record a deterministic Android emulator share-to-queued/status smoke artifact, and validate OpenSpec artifacts.

#### Scenario: Clean change passes CI
- **WHEN** product CI runs on the committed Android share-capture tree
- **THEN** all lint, contract, shared behavior, current-schema persistence, native secure-storage, Android Share Target/submission/status instrumentation and smoke, Android-build, iOS-build, and OpenSpec jobs complete successfully

#### Scenario: Shared tests cannot be omitted
- **WHEN** the CI workflow definition is checked against the documented product gate
- **THEN** the check fails if shared device-identity, capture-queue, Android-share staging, authenticated submission, operation-status fixture, current-schema persistence, Android Share Target/deep-link/WorkManager instrumentation, emulator smoke, or applicable native secure-storage commands are absent from either source of truth
