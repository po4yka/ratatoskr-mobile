## 1. Published Platform library contract

- [x] 1.1 RED: add `tooling/tests/platform_library_contract_test.sh::pinned_document_exposes_library_search_and_exact_read_state_resource` and extend `GeneratedContractSmokeTest.library_contract_models_round_trip`; run the shell test before changing the pin and observe the exact missing `/v1/library/search` assertion (not a syntax/tool failure).
- [x] 1.2 GREEN: copy the clean published Platform OpenAPI from commit `070b718238c4e6e45a5b7fc08ebe719ed5374e33`, update its digest/revision lock, regenerate the complete owned model tree, and run the new contract test plus `build-gate -- ./tooling/contracts/check.sh` and `build-gate -- ./tooling/tests/contract_drift_test.sh` to green, including mutation/stale/orphan/missing/determinism checks.

## 2. Authenticated recent-library and read-state adapter

- [x] 2.1 RED: add the compile-only public library API seam and `shared/src/commonTest/kotlin/com/ratatoskr/mobile/library/PlatformLibraryApiTest.kt` tests `blank_query_uses_bounded_library_path`, `read_state_put_uses_exact_generated_body`, `authorization_and_unavailable_responses_are_distinct`, and `redirect_is_refused`; run the focused shared test and observe an assertion/runtime failure caused by absent transport behavior, not a missing symbol or malformed fixture.
- [x] 2.2 GREEN: implement the generated-type Ktor library adapter, add `LibrarySearch` and `LibraryReadState` capability mappings, route requests through `DeviceAuthorizedRequestExecutor`, and rerun `PlatformLibraryApiTest` on the Android/JVM target to green.
- [x] 2.3 RED: add the compile-only store seam and `shared/src/commonTest/kotlin/com/ratatoskr/mobile/library/LibraryListStoreTest.kt` tests `recent_items_keep_platform_order`, `missing_capability_sends_no_request`, `authoritative_read_response_updates_only_read_state`, `uncertain_read_response_keeps_last_confirmed_state`, and `revocation_requests_repairing`; run them and observe state assertions fail because list/read orchestration is absent.
- [x] 2.4 GREEN: implement the pessimistic capability-gated recent-list/read-state UDF store with loading, empty, offline, retry, and re-pairing states; rerun `LibraryListStoreTest` to green.

## 3. Contract-fixed notes, favorites, collections, and tags

- [x] 3.1 RED: add the compile-only fixture repository seam and `shared/src/commonTest/kotlin/com/ratatoskr/mobile/library/FixtureUserContentRepositoryTest.kt` tests `favorite_toggle_preserves_read_and_memberships`, `bounded_note_round_trips_and_oversize_preserves_previous_note`, `collection_add_is_idempotent`, `tag_remove_changes_only_named_relation`, `failed_mutation_returns_last_confirmed_snapshot`, and `fixture_mutations_make_no_platform_calls`; run them and observe behavioral assertions fail against the empty fixture projection.
- [x] 3.2 GREEN: implement the deterministic article/social/AI-archive fixture catalog, explicit fixture authority, serialized in-memory mutations, 2,000-scalar note bound, idempotent memberships, and failure rollback; rerun `FixtureUserContentRepositoryTest` to green.
- [x] 3.3 RED: add the compile-only reader store seam and `shared/src/commonTest/kotlin/com/ratatoskr/mobile/library/LibraryReaderStoreTest.kt` tests `article_reader_preserves_provenance_warnings_and_ordered_inert_blocks`, `live_summary_without_detail_is_integration_pending`, `social_reader_does_not_infer_saved_authority`, and `ai_archive_reader_preserves_import_completeness`; run them and observe reader state remains unavailable or omits the supplied evidence.
- [x] 3.4 GREEN: implement article, social, and AI-archive reader projections plus safe unavailable/partial states using only supplied fixture facts; rerun `LibraryReaderStoreTest` to green.

## 4. Validated content routing

- [x] 4.1 RED: add the compile-only route parser seam and `shared/src/commonTest/kotlin/com/ratatoskr/mobile/library/ContentRouteTableTest.kt` tests `article_social_and_ai_archive_matrix_maps_to_distinct_routes`, `unknown_providers_and_families_are_rejected`, `noncanonical_ids_and_encoded_ambiguity_are_rejected`, and `query_fragment_credentials_and_extra_segments_are_rejected`; run them and observe the valid matrix is rejected while invalid inputs remain inert.
- [x] 4.2 GREEN: implement the exact shared `ratatoskr://library/...` allowlist, canonical UUID validation, typed Navigation 3 keys, and safe invalid result; rerun the routing table suite to green.
- [x] 4.3 RED: add `androidApp/src/androidTest/kotlin/com/ratatoskr/mobile/library/LibraryDeepLinkIntentTest.kt` and `iosApp/RatatoskrTests/IosLibraryRoutingTests.swift` tests `cold_and_warm_links_select_the_same_shared_destination` and `invalid_external_link_does_not_change_route`; run the Android/iOS focused tests and observe link delivery is absent while the apps still start.
- [x] 4.4 GREEN: add the narrow Android custom-scheme intent filter/new-intent handoff and iOS URL-scheme/`onOpenURL` handoff into the long-lived shared controller without implementing universal links; rerun both native routing tests to green.

## 5. Shared Compose library, reader, and curation surfaces

- [x] 5.1 RED: add `androidApp/src/androidTest/kotlin/com/ratatoskr/mobile/library/LibraryUiTest.kt` tests `renders_recent_read_state_and_dispatches_one_replacement`, `fixture_preview_labels_unsynchronized_favorite_note_and_memberships`, `reader_renders_provenance_warnings_and_hostile_text_inertly`, and `loading_empty_offline_reauth_and_integration_pending_states_are_visible`; run the focused emulator tests and observe missing library semantics/actions.
- [x] 5.2 GREEN: extend the shared Navigation 3 root with paired-session Library entry, recent list, fixture preview, article/social/AI-archive readers, notes, collections, tags, membership controls, and first-class failure states; wire Android/iOS application graphs to live and fixture repositories and rerun `LibraryUiTest` plus common library suites to green.
- [x] 5.3 RED: extend `shared/src/iosTest/kotlin/com/ratatoskr/mobile/IosApplicationGraphTest.kt` with `library_stores_share_live_authorization_and_fixture_authority_without_network_curation`; run `build-gate -- ./gradlew --no-daemon :shared:iosSimulatorArm64Test` and observe the graph lacks the library stores or violates the call-count assertion.
- [x] 5.4 GREEN: finish the iOS controller/store lifecycle and cancellation wiring, rerun the iOS shared suite, then run the existing hosted XCTest scheme and observe the new routing test plus all prior Share/Keychain/status/smoke tests pass.

## 6. CI, documentation, and evidence boundary

- [x] 6.1 RED: extend `tooling/tests/gate_parity_test.sh` to require the platform-library contract check, common library/routing suites, Android library instrumentation, and iOS routing XCTest in both `DEVELOPMENT.md` and `.github/workflows/ci.yml`; run it before editing either source and observe each missing command/name diagnostic.
- [x] 6.2 GREEN: update CI and `DEVELOPMENT.md` so both app builds and all shared/native library tests run, retain existing synthetic emulator/simulator artifacts, and rerun `gate_parity_test.sh` to green. Configuration cannot start from an additional behavior test beyond the parity RED because it changes only gate wiring.
- [x] 6.3 Update README, implementation-plan item 6, architecture/interfaces/testing/privacy documentation, and evidence labels for live Platform recency/read state versus reset-on-restart contract fixtures; documentation cannot start from a failing behavior test, so verify exact boundary phrases with `rg` and review the rendered Markdown.

## 7. Full gate and OpenSpec readiness

- [x] 7.1 Run every command in `DEVELOPMENT.md`, routing all Gradle and Xcode builds/tests through `build-gate`, including contract mutation/drift, Kotlin/Swift lint, Android/JVM and iOS Simulator common tests, Android emulator instrumentation, full hosted XCTest scheme, both app builds, strict OpenSpec validation, `git diff --check`, and relevant action/workflow lint; record exact pass/fail counts and do not relabel fixture/simulator evidence as live Platform or physical-device proof.
- [x] 7.2 Review the complete diff for undeclared endpoints/capabilities, generated-tree edits, unsafe link parsing, active content rendering, fixture/live authority confusion, token/private-content leakage, duplicate recomposition actions, unrelated changes, and stale call sites; fix any finding and rerun its narrow test plus affected gate.
- [x] 7.3 Sync all three deltas into main specs, verify delta/main equivalence, rerun `openspec validate --all --strict` and `openspec validate --archived`, and confirm every implementation task is checked and the change is ready for archive and the requested commit/integrate/push/cleanup lifecycle.
