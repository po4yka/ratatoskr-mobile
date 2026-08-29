## MODIFIED Requirements

### Requirement: Product CI enforces the bootstrap gate

The product CI SHALL lint Kotlin and Swift sources, verify generated contract drift including the mutation test, run shared device-identity, generated-contract, capture-model, retry, operation-projection, and capture-queue tests, create and reopen the current local database schema on Android and iOS test targets, compile and execute applicable native secure-storage tests, build the Android application, build the unsigned iOS Simulator application, and validate OpenSpec artifacts.

#### Scenario: Clean change passes CI
- **WHEN** product CI runs on the committed capture-queue tree
- **THEN** all lint, contract, shared behavior, current-schema persistence, native secure-storage, Android-build, iOS-build, and OpenSpec jobs complete successfully

#### Scenario: Shared tests cannot be omitted
- **WHEN** the CI workflow definition is checked against the documented product gate
- **THEN** the check fails if shared device-identity tests, capture-queue tests, current-schema persistence tests, or the applicable native secure-storage test command is absent from either source of truth
