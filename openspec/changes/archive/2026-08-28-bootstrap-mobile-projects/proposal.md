## Why

Ratatoskr Mobile has architecture documents but no buildable Android, iOS, or shared project, so no client work can be compiled, tested, or checked against the public Platform contract. This bootstrap establishes the smallest executable foundation for both platforms and makes contract drift and build regressions fail in CI before feature work starts.

## What Changes

- Add a Kotlin 2.4.10/K2 Gradle build with a version catalog, convention plugins, a shared KMP module, and a thin Android application shell hosting shared Compose Multiplatform content.
- Add a thin SwiftUI/UIKit iOS application shell that hosts the same shared Compose content while retaining native ownership of application lifecycle and future Share Extension integration.
- Record ADR-0001 with the selected KMP boundary: shared Compose presentation, models, capture-queue rules, and the public Platform API client; native platform lifecycle, share extensions/targets, secure storage, background scheduling, file access, notifications, and accessibility integration.
- Pin Platform's public OpenAPI document by producer commit and digest, generate Kotlin serialization models into the shared module, and add a deterministic drift check that rejects a mutated pinned document or stale generated output.
- Add shared-module tests plus Android, iOS, lint, contract, and OpenSpec checks to the documented local gate and GitHub Actions CI.
- Keep feature UI, capture behavior, persistence schemas, authentication, and provider-specific workflows out of this bootstrap.

## Capabilities

### New Capabilities

- `mobile-project-bootstrap`: Buildable Android and iOS shells, the shared KMP boundary, generated public Platform contracts, drift enforcement, and CI validation.

### Modified Capabilities

None.

## Impact

- New Gradle/KMP, Android, and Xcode project files; generated Kotlin contract sources; contract-generation tooling; ADR-0001; tests; development commands; and `.github/workflows/ci.yml`.
- Build dependencies use the requested KMP stack: Compose Multiplatform, Navigation 3 Multiplatform, AndroidX ViewModel/UDF, Flow/coroutines, Ktor, kotlinx.serialization, Room 3 KMP, DataStore KMP, Coil, kotlinx-datetime, Kermit, KSP, and the shared test libraries. Dependency injection remains manual until a feature requires Koin.
- The mobile repository consumes a committed OpenAPI snapshot from `ratatoskr-platform`; `ratatoskr-contracts` TypeScript declarations remain authoritative for TypeScript consumers and are not translated into Kotlin.
- No Platform route, wire contract, database schema, production deployment, credential, permission, entitlement, or store-release behavior changes.
