## MODIFIED Requirements

### Requirement: Product CI enforces the bootstrap gate
The product CI SHALL lint Kotlin and Swift sources, verify generated contract drift including the mutation test, run shared device-identity, generated-contract, capture-model, retry, operation-projection, capture-queue, Android-share staging, authenticated submission, operation-status fixture, generated library-contract, recent-library/read-state, fixture-curation, safe-reader, and content-routing tests, create and reopen the current local database schema on Android and iOS test targets, execute Android Share Target/deep-link/WorkManager instrumentation and applicable native secure-storage tests, build the Android application, build the unsigned iOS Simulator application, record deterministic Android emulator and iOS Simulator smoke artifacts, and validate OpenSpec artifacts.

#### Scenario: Clean change passes CI
- **WHEN** product CI runs on the committed mobile library and content-routing tree
- **THEN** all lint, contract, shared behavior, library/curation/routing, current-schema persistence, native secure-storage, Android Share Target/submission/status instrumentation and smoke, Android-build, iOS shared/shell smoke and build, and OpenSpec jobs complete successfully

#### Scenario: Shared tests cannot be omitted
- **WHEN** the CI workflow definition is checked against the documented product gate
- **THEN** the check fails if shared device-identity, capture-queue, Android-share staging, authenticated submission, operation-status fixture, generated library-contract, recent-library/read-state, fixture-curation, safe-reader, content-routing, current-schema persistence, Android Share Target/deep-link/WorkManager instrumentation, emulator/simulator smoke, or applicable native secure-storage commands are absent from either source of truth
