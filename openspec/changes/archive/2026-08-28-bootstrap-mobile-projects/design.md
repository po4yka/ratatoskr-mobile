## Context

See `proposal.md` for motivation. The repository contains no source tree or product CI today. Platform owns the public HTTP API and currently publishes `openapi/openapi.json`; Contracts publishes deterministic TypeScript declarations but explicitly has no Kotlin or Swift generation. The mobile client therefore needs a pinned Platform OpenAPI snapshot as its language-neutral generation input.

The selected UI direction is shared Compose Multiplatform content inside thin Android and SwiftUI/UIKit shells. This is an explicit ADR exception to the repository's default preference for native UI; native share surfaces and operating-system lifecycle/security work remain outside the shared abstraction.

## Goals / Non-Goals

**Goals:**

- Make a clean checkout produce an Android debug app, an unsigned iOS Simulator app, and a tested shared framework.
- Establish one Gradle version catalog and convention-plugin layer for the requested KMP stack.
- Make the public Platform OpenAPI pin, generated Kotlin models, and committed output reproducible and fail closed on drift.
- Keep local commands and CI steps mechanically comparable and truthful.
- Record the long-term shared/native boundary before feature modules form around accidental seams.

**Non-Goals:**

- Capture, queue persistence, authentication, networking calls, navigation destinations, database schemas, or feature UI.
- Android Share Target or iOS Share Extension targets; the ADR assigns them but later plan items implement them.
- Provider contracts, Platform API changes, TypeScript-to-Kotlin translation, package publication, signing, provisioning, deployment, or store release.
- Swift Export in the first scaffold; the iOS shell uses the stable Objective-C framework interop path.

## Decisions

### D1. One KMP shared module with two thin application shells

The Gradle build will contain `:shared` and `:androidApp`, backed by `build-logic` convention plugins and `gradle/libs.versions.toml`. `:shared` targets Android, `iosArm64`, and `iosSimulatorArm64`; it owns the Compose root and exposes an Objective-C-compatible UIKit view-controller factory. `:androidApp` owns the Android manifest/application lifecycle and calls the same Compose root. The committed Xcode project owns the SwiftUI application lifecycle and wraps the shared UIKit controller.

The iOS Xcode target uses Kotlin's direct-integration build phase to invoke `:shared:embedAndSignAppleFrameworkForXcode`; unsigned simulator CI sets signing off. ObjC framework interop is the default because it is mature and predictable for Compose embedding. Swift Export may be enabled later only for selected non-UI APIs whose exported surface benefits from it.

Alternatives rejected:

- Fully native SwiftUI UI: contradicts the selected shared-Compose direction and duplicates presentation/navigation work.
- Compose-only platform entry points: hide lifecycle and make later Share Extension, background, entitlement, and accessibility integration less explicit.
- Multiple shared modules now: creates module ceremony before queue/API/auth seams have executable behavior.

### D2. ADR-0001 defines the durable ownership seam

ADR-0001 will assign to KMP: shared Compose presentation, Navigation 3 routes/state, AndroidX ViewModel UDF state, Flow/coroutines, public API models and Ktor client adapters, serialization/validation, capture-queue state transitions, Room KMP repositories where behavior is identical, DataStore-backed shared settings where semantics are identical, time/logging abstractions, and shared tests.

Native code owns: Android/iOS app and extension lifecycle, `ACTION_SEND` and `NSItemProvider` intake, URI/security-scoped resources, staged-file I/O, Keystore/Keychain, WorkManager/background URLSession scheduling, notifications/deep links, permissions/entitlements, system presentation, and platform accessibility integration. Shared interfaces may request those operations, but cannot erase their native failure or lifecycle semantics.

This boundary shares product presentation while keeping the reliability- and authority-sensitive OS work explicit. The cost is a Compose framework in the iOS app and an interop boundary; the benefit is one presentation/state implementation without pretending the two share mechanisms behave alike.

### D3. The requested stack is centrally pinned and introduced without placeholder features

The version catalog pins Kotlin 2.4.10/K2, Compose Multiplatform 1.11.1, coroutines 1.11.0, Ktor Client 3.5.2, kotlinx.serialization 1.11.0, Room 3.0.1 KMP (`androidx.room3`), Koin 4.2.2, Coil 3.5.x, kotlinx-datetime 0.8.x, Navigation 3 Multiplatform, DataStore KMP, Kermit, KSP, Turbine, and compatible Android/Gradle tooling. Manual dependency injection is the bootstrap choice; Koin remains catalogued but is not instantiated until a feature has an object graph.

The shared module wires Compose, Navigation 3, ViewModel, Flow/coroutines, Ktor, serialization, datetime, and logging dependencies at their intended boundary; the bootstrap directly exercises only shared Compose hosting and generated serialization. Room, DataStore, Coil, KSP, and Koin coordinates are pinned for the selected architecture but will not receive fake repositories, tables, image loaders, processors, or containers merely to appear used. Later plan items add those production dependencies to source sets when their real behavior arrives.

Alternatives rejected:

- Scattering versions through build scripts: makes compatibility and drift review difficult.
- Adding placeholder implementations for every selected library: violates the no-stub rule and expands the bootstrap into unfinished features.
- A third-party DI container at bootstrap: no meaningful object graph exists yet; manual construction is smaller and clearer.

### D4. Platform OpenAPI is the Kotlin generation source

The repository will commit a normalized copy of Platform's public `openapi/openapi.json` plus a lock manifest containing the producer repository, full producer commit, document SHA-256, generator identity/version, and generator configuration digest. The initial pin is taken from a clean current Platform `main` checkout; it is not a relative project dependency and regeneration requires an explicitly supplied Platform checkout or exact-revision artifact.

A pinned OpenAPI Generator release will run the Kotlin `multiplatform` model templates with kotlinx.serialization and kotlinx-datetime mappings, with API/server stubs disabled. OpenAPI Generator's 3.1 validator incorrectly requires `info.license.identifier` when the valid producer document supplies `license.name`, and its Kotlin generator loses string enums expressed as `oneOf` branches with `const` values. The build therefore creates a temporary generator-only copy that adds `identifier = name` and rewrites those string-constant sets as semantically equivalent `enum` arrays, validates and generates from that copy, and never rewrites the pinned document. Generated sources live under a generator-owned committed tree and are added to `commonMain`; handwritten code never enters that tree. The shared Ktor client boundary will consume these models when networking arrives, avoiding generator-owned transport code whose Ktor version could diverge from the requested stack.

The pinned generator also emits an invalid `HashMap` supertype for Kotlin models whose schemas combine fixed fields with `additionalProperties`; that output has a duplicated constructor on JVM and extends a final type on Kotlin/Native. The generation task deterministically removes only that invalid supertype and normalizes trailing blank lines before comparison or compilation. The normalization is recorded in the lock manifest and applies only inside the generated output tree; declared contract fields remain generator-owned.

Contracts' TypeScript declarations are not input because translating a language-specific projection would compound information loss and cannot produce Kotlin serialization metadata reliably. The Platform OpenAPI document is already the public HTTP surface consumed by mobile.

### D5. Drift verification checks provenance before generated bytes

Contract verification performs three independent checks without rewriting the checkout:

1. hash the committed OpenAPI document and compare it with the lock manifest;
2. create the documented generator-only license normalization, validate it, generate into a temporary directory, and compare the complete expected file set byte-for-byte with committed generated Kotlin sources, rejecting missing, changed, and orphaned files;
3. run a mutation test against a temporary document copy and assert that the same checker exits non-zero for the digest mismatch.

The mutation test changes a JSON value while preserving valid JSON, so failure proves provenance enforcement rather than parser rejection. Generation is a deliberate developer command; CI only checks, never regenerates before comparison.

Alternatives rejected:

- Fetching Platform `main` in CI: a moving branch is not a pin and makes old mobile commits non-reproducible.
- Digest-only verification: cannot detect stale or tampered generated sources.
- Generate-before-test in the checkout: repairs the evidence being checked and can make drift pass vacuously.

### D6. CI separates Linux Android work from macOS Apple work

The documented product gate lists exact commands, and CI contains matching commands:

- Linux/Android job: formatting and Kotlin lint, contract provenance/generated-output checks and mutation test, shared Android-host tests, Android debug assembly, and strict OpenSpec validation.
- macOS/iOS job: Swift formatting/lint validation, shared iOS Simulator tests/framework link, and unsigned `xcodebuild` for a generic iOS Simulator destination.
- A lightweight gate-parity test verifies that shared tests remain named in both `DEVELOPMENT.md` and `.github/workflows/ci.yml`.

All top-level local Gradle and Xcode test/build commands run through `build-gate`; CI runs their direct equivalents because the machine-wide local gate is not installed on hosted runners. Gradle worker count is capped at four in committed properties.

Two jobs give early Android feedback without consuming macOS time while still proving the Apple interop boundary. A single macOS job was rejected because it slows all checks and obscures platform-specific failures.

### D7. Bootstrap behavior uses shell tests before implementation

Before scaffold files exist, a lightweight repository test will assert the required Gradle modules, Android assemble task, Xcode scheme, shared entry point, ADR responsibility matrix, and CI command families; it must fail with a purpose-specific missing-foundation diagnostic rather than a compiler error. The scaffold then makes that test pass.

The contract checker receives a separate mutation test before its digest enforcement is completed; the red run must show that a valid-but-mutated document was wrongly accepted. Generated files and declarative configuration are documented exceptions where no meaningful red unit test exists, but their consuming smoke and drift behavior are covered before the gate is considered complete.

## Risks / Trade-offs

- [OpenAPI Generator template changes can create large diffs] → pin the generator and configuration, own the complete generated tree, and require byte drift checks.
- [Generated models may expose names awkward for Swift] → keep them behind shared Kotlin APIs and use ObjC interop now; evaluate selective Swift Export only with a concrete consumer.
- [Compose iOS increases binary/build cost] → keep the initial content minimal, measure later feature builds, and retain thin native lifecycle shells.
- [Future Share Extension cannot safely host the full Compose/application graph] → ADR-0001 keeps extension UI/lifecycle native and limits shared reuse to pure models/rules.
- [Cataloguing unused future stack coordinates can drift] → exercise only bootstrap dependencies in source sets and update/add the rest when their plan item starts; version-catalog aliases are not completion evidence.
- [Hosted Xcode images move] → select and record an available Xcode version in CI, build an unsigned simulator target, and keep the local command equivalent in `DEVELOPMENT.md`.
- [Platform contract pin becomes stale] → drift is intentionally explicit; updating the pin and generated tree is a reviewed mobile change, never an automatic CI mutation.

## Migration Plan

1. Add and verify the local scaffold/gate tests in the isolated task worktree.
2. Add the Gradle/KMP, Android, Xcode, contract-generation, ADR, documentation, and CI foundation; observe every planned red/green pair.
3. Run the complete documented gate through `build-gate`, archive the OpenSpec change after syncing its delta, and commit only the intended tree.
4. Integrate the task branch into `main`, push `main`, verify the exact hosted CI run, then remove the worktree and local task branch.

Rollback is a revert of the bootstrap commit; no production data, contract producer, deployed service, credential, schema, or migration is affected.
