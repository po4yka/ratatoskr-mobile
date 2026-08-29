## MODIFIED Requirements

### Requirement: Product CI enforces the bootstrap gate

The product CI SHALL lint Kotlin and Swift sources, verify generated contract drift including the mutation test, run shared device-identity, generated-contract, capture-model, retry, operation-projection, capture-queue, Android-share staging, authenticated submission, and operation-status fixture tests, create and reopen the current local database schema on Android and iOS test targets, execute Android Share Target/deep-link/WorkManager instrumentation and applicable native secure-storage tests, build the Android application, build the unsigned iOS Simulator application, record a deterministic Android emulator share-to-queued/status smoke artifact, and validate OpenSpec artifacts.

#### Scenario: Clean change passes CI
- **WHEN** product CI runs on the committed Android share-capture tree
- **THEN** all lint, contract, shared behavior, current-schema persistence, native secure-storage, Android Share Target/submission/status instrumentation and smoke, Android-build, iOS-build, and OpenSpec jobs complete successfully

#### Scenario: Shared tests cannot be omitted
- **WHEN** the CI workflow definition is checked against the documented product gate
- **THEN** the check fails if shared device-identity, capture-queue, Android-share staging, authenticated submission, operation-status fixture, current-schema persistence, Android Share Target/deep-link/WorkManager instrumentation, emulator smoke, or applicable native secure-storage commands are absent from either source of truth
