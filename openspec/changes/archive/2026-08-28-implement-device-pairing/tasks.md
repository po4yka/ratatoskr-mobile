## 1. Platform identity transport

- [x] 1.1 Add the behavior-free public transport seam plus `shared/src/commonTest/.../PlatformIdentityApiTest.kt` tests `pairing_handshake_matrix_maps_contract_outcomes` and `transport_rejects_non_https_origin_without_request`; run `build-gate -- ./gradlew --no-daemon :shared:testDebugUnitTest` and observe the runtime assertions fail at the explicit unimplemented transport boundary rather than from a typo or compile error.
- [x] 1.2 Implement the generated-contract Ktor identity adapter with canonical HTTPS origin validation, redirects disabled, `mobile` pairing kind, and secret-free typed failures; rerun the focused shared tests and observe them pass.

## 2. Native secure credential storage

- [x] 2.1 Add the behavior-free secure-storage seam plus `AndroidKeystoreCredentialStorageTest.kt` and `IosKeychainCredentialStorageTest.kt` round-trip/replace/delete tests and common corrupt-record coverage; run the applicable Android instrumentation compile/test and iOS Simulator test commands and observe runtime failure at the explicit unimplemented native boundary rather than from a typo or compile error.
- [x] 2.2 Implement the shared opaque-record interface, Android AES-GCM/Keystore adapter, and device-only non-synchronizing iOS Keychain adapter; run the Android instrumentation and app-hosted iOS Simulator XCTest and observe the synthetic round trips pass; keep the unhosted K/N Simulator Keychain test skipped with its `errSecNotAvailable` boundary documented.

## 3. Rotation, recovery, revocation, and capabilities

- [x] 3.1 Add `DeviceSessionManagerTest.refresh_replaces_access_and_refresh_credentials_atomically`; run the focused shared test and observe runtime failure at the behavior-free manager seam.
- [x] 3.2 Implement restored-session refresh and atomic secure-storage replacement; rerun the focused test and observe it pass.
- [x] 3.3 Add `concurrent_refresh_callers_share_one_rotation`; run the focused test and observe the assertion fail because callers rotate independently.
- [x] 3.4 Serialize refresh and coalesce callers that observed the same refresh link; rerun the focused test and observe one Platform exchange and one replacement session.
- [x] 3.5 Add `uncertain_refresh_recovers_without_replaying_the_link`; run the focused test and observe the assertion fail because device-root recovery is absent.
- [x] 3.6 Implement one bounded device-root recovery after uncertain/refused refresh with identity matching; rerun the focused test and observe no refresh replay and a new stored session chain.
- [x] 3.7 Add `paired_elsewhere_revocation_clears_session_gracefully` and `local_sign_out_clears_only_local_authorization`; run the focused tests and observe state/storage assertions fail.
- [x] 3.8 Implement revocation and local sign-out state transitions with credential/capability clearing; rerun the focused tests and observe them pass.
- [x] 3.9 Add session-scoped capability tests for fresh availability, stale fail-closed behavior, unknown names, and cache replacement after recovery; run the focused tests and observe capability-state assertions fail.
- [x] 3.10 Implement authenticated capability discovery and conservative current-session cache projection; rerun shared Android/JVM and iOS Simulator tests and observe all manager scenarios pass.
- [x] 3.11 Add `capability_unauthorized_rotates_once_and_retries_with_replacement_access`; run the focused test and observe capability discovery remain stale because authorization recovery is absent.
- [x] 3.12 Route capability authorization refusal through the serialized refresh/recovery path and retry discovery once; rerun the focused test and observe the replacement access credential used exactly once.
- [x] 3.13 Add `restart_with_consumed_refresh_marker_recovers_without_replay`; run the focused test and observe no recovery because restart does not yet retain refresh-use state.
- [x] 3.14 Persist an unusable-refresh marker before exchange and recover directly after restart; rerun rotation/recovery tests and observe that no marked link is presented.

## 4. Shared pairing application surface

- [x] 4.1 Add shared presentation-state tests that assert a fresh installation exposes pairing input, accepted credentials expose current capabilities, and revocation returns to a safe re-pairing state; run the shared tests and observe failure because the application state holder is absent.
- [x] 4.2 Implement manual native-to-shared dependency wiring and a shared Compose pairing/session surface, preserving thin Android/iOS shells; rerun shared tests and both platform compiles and observe the state and shell boundaries pass.

## 5. Gate and documentation

- [x] 5.1 Extend `tooling/tests/gate_parity_test.sh` and architecture/bootstrap checks to require identity tests, native secure-storage coverage, and both shell adapters; run the repository scripts and observe them fail against the old CI/docs/project structure.
- [x] 5.2 Update CI, `DEVELOPMENT.md`, README/testing/architecture/security documentation, and the implementation-plan status to match the implemented gate and evidence boundary; configuration and documentation cannot begin with a behavioral unit test, so verify with repository scripts, actionlint, Swift format lint, and strict OpenSpec validation.

## 6. Full delivery

- [x] 6.1 Run contract drift/mutation checks, Kotlin lint, shared Android/iOS tests, Android instrumentation, Android debug assembly, shared iOS framework link, unsigned iOS Simulator build, all repository scripts, and strict OpenSpec validation; inspect the final diff for secrets, generated drift, scope creep, and missing call sites.
- [x] 6.2 Sync the delta specs, verify main-spec equivalence, rerun strict validation, and confirm the completed change is ready for archival; delivery follows the archived lifecycle outside the implementation task list.
