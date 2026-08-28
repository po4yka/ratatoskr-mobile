## MODIFIED Requirements

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

### Requirement: Product CI enforces the bootstrap gate

The product CI SHALL lint Kotlin and Swift sources, verify generated contract drift including the mutation test, run shared device-identity and generated-contract tests, compile and where supported execute native secure-storage tests, build the Android application, build the unsigned iOS Simulator application, and validate OpenSpec artifacts.

#### Scenario: Clean change passes CI
- **WHEN** product CI runs on the committed device-identity tree
- **THEN** all lint, contract, shared-test, native secure-storage-test, Android-build, iOS-build, and OpenSpec jobs complete successfully

#### Scenario: Shared tests cannot be omitted
- **WHEN** the CI workflow definition is checked against the documented product gate
- **THEN** the check fails if shared device-identity tests or the applicable native secure-storage test command is absent from either source of truth
