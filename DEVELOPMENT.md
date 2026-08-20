# Developing Ratatoskr Mobile

> Status: Proposed  
> Last reviewed: 2026-08-20

Architecture bootstrap: Android, iOS, KMP modules, Share Target/Extension, local queue, device auth, and CI are not implemented.

## Intended toolchain

Kotlin Multiplatform for shared contracts/network/queue/domain where appropriate; native Android with modern Kotlin/Compose/WorkManager/DataStore/Room/Keystore; native iOS with Swift/SwiftUI, Share Extension, background URLSession, Keychain, and local persistence. Generated Platform API contracts are the network source of truth.

## Code size limits

There is no code here yet, so no limit is enforced yet. This is also one of the two repositories whose first code is Swift or Kotlin, and the fleet has chosen no linter for either language. `fleet.yml` asserts that a `Cargo.toml` arrives with a `clippy.toml` and that a `package.json` arrives with an `eslint.config.js`. It can assert nothing for a `Package.swift` or a `build.gradle.kts`, because there is no fleet answer to name. The scaffold pull request here names the tool and the file that carry the limits, and adds that assertion to `fleet.yml` in all seventeen repositories.

`ratatoskr-workspace/docs/QUALITY_GATES.md` holds the numbers the repositories with code use today, the command that measured each one, and the limits that were rejected with the reason. Read it before you choose numbers, then measure this tree. Each limit is set at the worst case the tree already has, so that the check fails on a regression and not on work that has not been done yet.

## Workflow

1. Keep platform lifecycle/security/UI native where sharing creates constraints.
2. Treat every inbound URL/file/text as hostile and stage files before extension lifetime ends.
3. Persist queue/idempotency before network work and make retries restart-safe.
4. Use device credentials only through Keystore/Keychain.
5. Test offline, process death, background limits, duplicate shares, low storage, revoked credentials, accessibility, and privacy.

The first scaffold PR must document exact Gradle/Xcode/KMP/test/build commands and supported OS versions.

## What a clone needs before you plan a change

A change is planned with OpenSpec, which is a CLI a clone installs for itself. Use the version
`.github/workflows/openspec.yml` pins, so your terminal and the gate answer the same:

```bash
npm install --global @fission-ai/openspec@1.10.0
```

Cross-repository behaviour lives in a store, and registering one is per-machine state that no
repository can turn on for you — the same kind of step as `git config core.hooksPath .githooks`:

```bash
git clone git@github.com:po4yka/ratatoskr-workspace.git <path>
openspec store register <path> --id ratatoskr-workspace
```

`openspec doctor` reports whether both are in place.
