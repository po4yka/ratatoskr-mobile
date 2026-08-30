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

The product CI SHALL lint Kotlin and Swift sources, verify generated contract drift including the mutation test, run shared device-identity, generated-contract, capture-model, retry, operation-projection, capture-queue, Android-share staging, authenticated submission, operation-status fixture, generated library-contract, recent-library/read-state, fixture-curation, safe-reader, and content-routing tests, create and reopen the current local database schema on Android and iOS test targets, execute Android Share Target/deep-link/WorkManager instrumentation and applicable native secure-storage tests, build the Android application, build the unsigned iOS Simulator application, record deterministic Android emulator and iOS Simulator smoke artifacts, and validate OpenSpec artifacts.

#### Scenario: Clean change passes CI
- **WHEN** product CI runs on the committed mobile library and content-routing tree
- **THEN** all lint, contract, shared behavior, library/curation/routing, current-schema persistence, native secure-storage, Android Share Target/submission/status instrumentation and smoke, Android-build, iOS shared/shell smoke and build, and OpenSpec jobs complete successfully

#### Scenario: Shared tests cannot be omitted
- **WHEN** the CI workflow definition is checked against the documented product gate
- **THEN** the check fails if shared device-identity, capture-queue, Android-share staging, authenticated submission, operation-status fixture, generated library-contract, recent-library/read-state, fixture-curation, safe-reader, content-routing, current-schema persistence, Android Share Target/deep-link/WorkManager instrumentation, emulator/simulator smoke, or applicable native secure-storage commands are absent from either source of truth

### Requirement: iOS share and App Group behavior are part of the product gate

The repository SHALL provide a buildable iOS application with an embedded Share Extension and SHALL run deterministic checks for extension parsing, atomic App Group handoff, entitlement scope, shared queue submission, operation status, and a simulator-hosted share smoke in the documented local gate and hosted CI.

#### Scenario: iOS share gate runs from a clean checkout
- **WHEN** the documented iOS product gate runs with the pinned toolchain and a synthetic simulator profile
- **THEN** shared iOS tests, Swift lint, application and extension builds, parser and handoff XCTest, Keychain policy, submission and status fixtures, and the simulator smoke all pass without signing secrets or private captures

#### Scenario: Simulator smoke evidence is retained honestly
- **WHEN** hosted CI exercises the synthetic Share Extension handoff through the main-app queue and fixture operation flow
- **THEN** it retains the XCTest result or report as simulator evidence and does not label it live Platform, physical-device, background-execution, provider, signing, or App Store proof

### Requirement: Product CI enforces file-transfer and erasure behavior

The documented local gate and hosted product CI SHALL verify pinned/generated blob-transfer drift
including mutation, shared resumption/finalization/retention/scheduling/erasure behavior, Android
protected file staging and WorkManager constraints on an emulator, iOS App Group file handoff and
background/Keychain/container erasure on a simulator, and both application builds. Fixture receiver
evidence SHALL be labelled separately from live Platform, physical-device, guaranteed background,
signing, provider, and store evidence.

#### Scenario: Clean file-transfer change passes CI
- **WHEN** CI runs from a clean checkout with synthetic files and the pinned contract receiver harness
- **THEN** contract drift, shared tests, Android instrumentation/build, iOS simulator tests/build, gate parity, and strict OpenSpec validation all complete successfully

#### Scenario: Required file test is removed from one gate
- **WHEN** a documented or hosted gate omits contract mutation, resume-after-interruption, cleanup bounds, scheduling decisions, complete erasure, or native file staging coverage
- **THEN** gate-parity validation fails and identifies the missing command or marker

#### Scenario: Evidence boundary remains explicit
- **WHEN** the deterministic receiver harness and simulators pass while Platform exposes no public receipt binding
- **THEN** retained evidence describes contract-fixture and simulator coverage and continues to mark live Platform upload integration pending
