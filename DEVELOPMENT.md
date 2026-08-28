# Developing Ratatoskr Mobile

> Status: Architecture bootstrap
> Last reviewed: 2026-08-28

The repository contains buildable Android and iOS application shells around one shared Compose
Multiplatform root. It does not yet contain capture features, share extensions, durable queue
behavior, device authentication, or feature UI.

## Toolchain

- JDK 17 and the committed Gradle wrapper;
- Android SDK platform 36; the shell supports Android 8.0/API 26 and newer;
- Xcode with an arm64 iOS Simulator SDK and the bundled `swift format` command; the Compose 1.11.1
  shell supports iOS 18.5 and newer;
- OpenSpec 1.10.0 for proposal/spec validation.

Kotlin/Compose and library versions are pinned in `gradle/libs.versions.toml`. Gradle is capped at
four workers in `gradle.properties`. KSP is pinned but is intentionally not applied until a real
processor-backed feature exists. Manual dependency injection is the bootstrap choice.

## Product gate

Run these commands from the repository root. `build-gate` is the machine-wide serialization guard
for every local Gradle or Xcode build on the Ratatoskr development Mac; hosted CI runs the same
underlying commands directly.

```bash
./tooling/tests/bootstrap_structure_test.sh
./tooling/tests/architecture_boundary_test.sh
./tooling/tests/gate_parity_test.sh

build-gate -- ./gradlew --no-daemon ktlintCheck
build-gate -- ./tooling/contracts/check.sh
build-gate -- ./tooling/tests/contract_drift_test.sh
build-gate -- ./gradlew --no-daemon :shared:testDebugUnitTest :androidApp:assembleDebug

swift format lint --recursive --strict iosApp/Ratatoskr/App
build-gate -- ./gradlew --no-daemon :shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
build-gate -- xcodebuild -project iosApp/Ratatoskr.xcodeproj -scheme Ratatoskr -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build

openspec validate --all --strict
openspec validate --archived
```

The Android app is a thin native Activity. The iOS app is a thin SwiftUI/UIKit shell whose Xcode
build phase embeds the shared Objective-C-compatible framework. Both host `RatatoskrApp`; there is
no feature UI in this bootstrap.

## Generated Platform contracts

`contracts/platform-openapi.json` is an exact pinned Platform public OpenAPI document. Its producer
commit, document digest, generator version, configuration digest, and documented compatibility
normalizations live in `contracts/platform-openapi.lock.json`. The generator owns only
`shared/src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model`.

To update the pin, first obtain the exact reviewed Platform revision, then update the snapshot and
lock manifest before running:

```bash
build-gate -- ./gradlew --no-daemon generateContracts -PplatformOpenApi=/absolute/path/to/platform/openapi/openapi.json
build-gate -- ./tooling/contracts/check.sh
```

Never hand-edit the generated model tree. The checker verifies source/configuration provenance and
compares a fresh temporary generation with every committed generated file. Its mutation suite also
proves that a valid-JSON change to the pin fails closed.

## OpenSpec setup

Install the version used by the gate:

```bash
npm install --global @fission-ai/openspec@1.10.0
```

Cross-repository behavior lives in the workspace store, whose registration is per-machine state:

```bash
git clone git@github.com:po4yka/ratatoskr-workspace.git <path>
openspec store register <path> --id ratatoskr-workspace
openspec doctor
```

## Working rules

1. Start every non-trivial change with OpenSpec and pair each behavior test task with its passing
   implementation task.
2. Keep shared Compose presentation/domain behavior separate from native lifecycle, share,
   security, background, file-access, notification, and accessibility adapters per ADR-0001.
3. Treat every inbound URL, file, and text item as hostile and persist bounded queue state before
   network work once capture behavior exists.
4. Keep device secrets behind Keystore/Keychain adapters and provider credentials in backend
   services.
5. Add platform lifecycle, offline, failure, accessibility, privacy, and performance evidence with
   the features that introduce those behaviors.
