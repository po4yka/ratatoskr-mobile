# Developing Ratatoskr Mobile

> Status: Proposed  
> Last reviewed: 2026-08-17

Architecture bootstrap: Android, iOS, KMP modules, Share Target/Extension, local queue, device auth, and CI are not implemented.

## Intended toolchain

Kotlin Multiplatform for shared contracts/network/queue/domain where appropriate; native Android with modern Kotlin/Compose/WorkManager/DataStore/Room/Keystore; native iOS with Swift/SwiftUI, Share Extension, background URLSession, Keychain, and local persistence. Generated Platform API contracts are the network source of truth.

## Workflow

1. Keep platform lifecycle/security/UI native where sharing creates constraints.
2. Treat every inbound URL/file/text as hostile and stage files before extension lifetime ends.
3. Persist queue/idempotency before network work and make retries restart-safe.
4. Use device credentials only through Keystore/Keychain.
5. Test offline, process death, background limits, duplicate shares, low storage, revoked credentials, accessibility, and privacy.

The first scaffold PR must document exact Gradle/Xcode/KMP/test/build commands and supported OS versions.
