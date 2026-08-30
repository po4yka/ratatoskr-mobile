## 1. Pinned blob-transfer contracts

- [x] 1.1 Add `tooling/tests/blob_transfer_contract_drift_test.sh` cases `mutated_pinned_transfer_schema_is_rejected` and `mutated_generated_transfer_type_is_rejected`; run the script and confirm its assertions fail because no pinned transfer lock/generated tree is checked (configuration/generation has no useful unit seam before this contract test).
- [x] 1.2 Pin the six published `ratatoskr-contracts` transfer schemas/valid fixtures, add the exact revision/digest lock, deterministic Gradle Kotlin generation, and `tooling/contracts/check-blob-transfer.sh`; use a bounded local failure classification because upstream publishes no transfer-failure wire schema, then run the task 1.1 script plus generation twice and verify the committed tree matches byte-for-byte.

## 2. Shared resumable transfer protocol

- [x] 2.1 Add `shared/src/commonTest/kotlin/com/ratatoskr/mobile/transfer/BlobTransferContractTest.kt` tests `declaration_derives_exact_chunk_lengths` and `malformed_fixture_values_fail_closed`, with compile-only public transfer declarations returning an explicit unsupported result; run the common test target and confirm the literal chunk/codec assertions fail, not compilation.
- [x] 2.2 Implement generated-contract adapters, bounded chunk arithmetic, wire validation, and safe transfer failures; run `BlobTransferContractTest` and verify the contract examples pass.
- [x] 2.3 Add `ResumableUploadCoordinatorTest.resume_after_interruption_sends_only_receiver_missing_chunks` and `receiver_status_recovers_uncheckpointed_ack`; run them and confirm the fake receiver observes duplicate/already-recorded sends under the compile-only coordinator seam.
- [x] 2.4 Implement `BlobReceiptTransport`, streaming `StagedArtifactSource`, transfer lease/checkpoint state, status-first reconciliation, and missing-chunk delivery; run the task 2.3 tests and verify only missing indices are sent with the original capture identity.
- [x] 2.5 Add `ResumableUploadCoordinatorTest` cases `uncertain_finalize_reconciles_without_new_session`, `expired_session_reopens_same_declaration`, `changed_staged_bytes_fail_integrity`, and `receipt_does_not_complete_platform_operation`; run them and confirm the expected state/identity assertions fail against the first coordinator slice.
- [x] 2.6 Implement uncertain finalize/status recovery, expired-session replacement, pre-resume size/digest verification, completion-receipt verification, and distinct uploaded-versus-Platform-accepted projections; run all shared transfer tests and verify them green.
- [x] 2.7 Add `ProductionFileTransferAvailabilityTest.missing_public_receipt_binding_is_integration_pending_and_sends_nothing`; run it and confirm the production graph currently exposes no truthful file-transfer state.
- [x] 2.8 Wire an explicit `IntegrationPending` production transport/availability state and the deterministic receiver only in tests; rerun task 2.7 and verify no internal or guessed endpoint is contacted.

## 3. Durable queue, artifact, and upload persistence

- [x] 3.1 Add `shared/src/commonTest/kotlin/com/ratatoskr/mobile/queue/StagedFileQueueTest.kt` tests `transfer_checkpoint_preserves_capture_identity` and `platform_receipt_acceptance_releases_bytes_only_after_binding`; run them with a compiling in-memory persistence seam and confirm checkpoint/receipt/reference assertions fail.
- [x] 3.2 Extend queue transactions and models so one staged artifact/transfer/receipt remains bound to the original capture and source ordering; run `StagedFileQueueTest` and existing queue tests and verify them green.
- [x] 3.3 Add Android and iOS current-schema reopen tests `file_transfer_survives_database_reopen_with_same_identity` in `CaptureQueuePersistenceInstrumentedTest.kt` and `IosCaptureQueuePersistenceTest.kt`; run each target and confirm persisted checkpoint/receipt fields are absent after reopen.
- [x] 3.4 Edit Room version 1 in place with indexed artifact/transfer entities and native builders/close-delete support, without migration files; rerun both reopen suites and verify capture, artifact, checkpoint, receipt, source sequence, and idempotency key survive.

## 4. Retention, cleanup, and usage

- [x] 4.1 Add `ArtifactRetentionPolicyTest.cleanup_removes_only_accepted_cancelled_expired_orphans` and `unfinished_files_are_never_evicted_at_capacity`; run them with a compile-only policy returning no decisions and confirm the expected deletion/admission assertions fail.
- [x] 4.2 Implement the 100 MiB file, 512 MiB/64 artifact, 24-hour temp, and seven-day terminal-failure policy with conservative reference-safe decisions; rerun task 4.1 and verify exact literal usage and cleanup sets.
- [x] 4.3 Add `ArtifactCleanupCoordinatorTest.interrupted_cleanup_converges_without_touching_referenced_or_outside_paths` and `delete_failure_remains_counted_and_visible`; run them and confirm inventory/usage convergence assertions fail.
- [x] 4.4 Implement native-inventory reconciliation through opaque IDs, symlink/root refusal, idempotent delete/ledger repair, and content-free `StorageUsage`; run all retention/cleanup tests and verify the hard bound refuses rather than evicts unfinished work.

## 5. Shared and native scheduling constraints

- [x] 5.1 Add `TransferSchedulingPolicyTest` cases `offline_defers`, `low_battery_or_low_power_defers_without_attempt`, `large_ios_transfer_requires_external_power`, `integration_pending_never_schedules`, and `duplicate_wakeup_has_one_lease`; run them against a compiling placeholder projection and confirm decision literals fail.
- [x] 5.2 Implement the pure scheduling projection and durable one-transfer-per-owner lease behavior; run task 5.1 and existing queue retry/order tests and verify no deferral increments attempts.
- [x] 5.3 Add `FileUploadWorkerTest.maps_connected_battery_not_low_and_storage_not_low_constraints` and `stale_worker_generation_cannot_write_after_cancel`; run Android instrumentation and confirm WorkManager constraint/generation assertions fail.
- [x] 5.4 Implement distinct opaque Android file-upload unique work, constraint mapping, queue re-read, cancellation, and generation fencing; rerun task 5.3 and verify no content/token is present in WorkManager input.
- [x] 5.5 Add `IosFileUploadSchedulerTests` cases `uses_processing_network_and_large_file_power_constraints`, `low_power_mode_defers_before_claim`, and `expiration_cancels_stream_and_preserves_checkpoint`; run XCTest and confirm boundary observations fail.
- [x] 5.6 Implement the iOS `BGProcessingTaskRequest` boundary, Low Power Mode decision input, large-file power mapping, foreground repair, and expiration cancellation; rerun task 5.5 and verify durable eligibility remains authoritative.

## 6. Android explicit file staging

- [x] 6.1 Extend `AndroidShareIntentParserTest` and add `AndroidStagedArtifactStoreTest` cases `supported_content_uri_is_atomically_staged`, `mismatched_or_oversized_file_is_refused`, `url_and_file_is_ambiguous`, and `interrupted_copy_publishes_no_artifact`; run instrumentation with synthetic content and confirm current parser/store returns unsupported assertions rather than compile errors.
- [x] 6.2 Add reviewed file MIME intent filters and implement streamed grant-bounded copy, type evidence, digest, opaque no-backup paths, atomic publish, sanitized display metadata, and grant release; rerun task 6.1 plus manifest checks and verify no external URI/path enters the queue.
- [x] 6.3 Add `ShareStagingUiTest.confirmed_file_enqueues_before_schedule` and `cancelled_file_removes_only_unreferenced_draft`; run instrumentation and confirm the current URL-only staging assertions fail for file state.
- [x] 6.4 Extend shared staging/UDF and the Android shell for file preview, usage/upload impact, explicit confirmation, durable enqueue, cancellation cleanup, offline and integration-pending states; rerun task 6.3 and the Android share smoke with synthetic bytes.

## 7. iOS Share Extension file handoff

- [x] 7.1 Extend `ShareExtensionParserTests.swift` and `AppGroupEnvelopeTests.swift` with `testSupportedFileIsProtectedAndPublishedAtomically`, `testInterruptedFileCopyPublishesNothing`, `testMultipleOrMismatchedFilesAreRejected`, and path/digest bounds; run XCTest and confirm the URL/text-only result assertions fail.
- [x] 7.2 Implement single-file `NSItemProvider` loading, bounded streaming copy/digest/type evidence, protected opaque App Group artifact/envelope publication, scoped-access release, and no extension network/Keychain/Room access; rerun task 7.1 and entitlement checks.
- [x] 7.3 Add `AppGroupInboxImporterTests.swift` cases `testFileHandoffConvergesAcrossMainAppRestart` and `testConfirmedFileMovesToPrivateStagingBeforeQueueCommit`; run XCTest and confirm the current envelope-only importer loses or rejects the file facts.
- [x] 7.4 Implement main-app claim/revalidation/capacity reservation, restartable App Group-to-private staging transfer, queue convergence, cancel cleanup, and retained handoff on commit failure; rerun task 7.3 plus the iOS synthetic share smoke.

## 8. Replay-safe revoke and clear-data erasure

- [x] 8.1 Add `LocalDataErasureCoordinatorTest` cases `confirmed_clear_erases_every_registered_store`, `cancel_is_noop`, `restart_completes_interrupted_marker`, and `stale_callback_cannot_recreate_data`; seed public fake stores and run the tests, confirming non-credential residue/marker/generation assertions fail.
- [x] 8.2 Implement erase marker/state/generation, idempotent registered participants, cancellation ordering, post-wipe inventory, marker-last completion, and explicit-confirmation input counts; rerun task 8.1 and verify completion is observable only from empty public stores.
- [x] 8.3 Update `DeviceSessionManagerTest.paired_elsewhere_revocation_clears_session_gracefully` to assert the erasure callback completes before unpaired state, plus retain `local_sign_out_clears_only_local_authorization`; run the tests and confirm remote revoke still clears credentials only.
- [x] 8.4 Inject the erasure coordinator into proven revocation while preserving credential-only sign-out and in-memory capability fencing; rerun all identity, revocation-race, GitHub, library, submission, and task 8.3 tests.
- [x] 8.5 Add `AndroidLocalDataErasureInstrumentedTest` seeding Keystore ciphertext, Room/WAL/SHM, staged/temp files, preferences, cache, work, and notification state, then testing complete and interrupted wipes; run instrumentation and confirm seeded residue remains.
- [x] 8.6 Implement Android erase marker/startup barrier and exact native participants for work/notifications, secure storage, databases/sidecars, no-backup staging/temp, preferences/DataStore, and caches; rerun task 8.5 and verify stale workers cannot recreate data.
- [x] 8.7 Add `IosLocalDataErasureTests.swift` seeding Keychain, Room/sidecars, app staging/temp, App Group inbox/processing/rejected/artifacts, UserDefaults, cache, and background requests, then testing complete/interrupted wipes; run XCTest and confirm seeded residue remains.
- [x] 8.8 Implement the iOS startup barrier and exact app/App Group/Keychain/background participants with protection-safe inventory; rerun task 8.7 and existing Keychain/App Group suites and verify no registered residue remains.

## 9. Shared storage and destructive-confirmation surface

- [x] 9.1 Add `LocalStorageStoreTest` cases `usage_projection_is_truthful_and_content_free`, `clear_requires_one_shot_confirmation`, `cancel_changes_nothing`, and `erase_failure_stays_visible`; run the common tests against a compiling empty store and confirm count/confirmation/effect assertions fail.
- [x] 9.2 Implement the shared UDF/Compose local-storage section with category bytes/counts, capacity/expiry, cleanup action, integration-pending upload truth, exact destructive confirmation, erasing/error/empty states, and one-shot effects; rerun task 9.1 and shared Compose UI tests.
- [x] 9.3 Add Android shared-Compose instrumentation and iOS hosted/simulator smoke assertions for file preview, usage, integration-pending, clear confirmation/cancel/completion, and accessibility labels; run them and confirm the new semantics are absent from both shells.
- [x] 9.4 Wire native artifact/scheduler/erasure adapters into the existing thin Android/iOS graphs and shared Compose root; rerun task 9.3 and verify no duplicate native feature UI or unredacted path/content appears.

## 10. Architecture, gate, and delivery evidence

- [x] 10.1 Update ADR-0001/ADR-0004 or add the next focused ADR, plus README, INTERFACES, DATA_MODEL, THREAT_MODEL, TESTING, IMPLEMENTATION_PLAN, and DEVELOPMENT with the shared/native seam, permissions/entitlements, retention, destructive revoke behavior, privacy, rollback, and fixture-versus-live boundary; no failing test applies because these are architecture/configuration records, then run documentation/architecture checks.
- [x] 10.2 Extend `tooling/tests/gate_parity_test.sh` with file-contract mutation, shared transfer/retention/scheduling/erasure, Android file/worker/erase, and iOS file/background/erase markers; run it and confirm it fails because DEVELOPMENT/CI do not yet contain every command.
- [x] 10.3 Add the same targeted commands and retained emulator/simulator evidence to DEVELOPMENT and `.github/workflows/ci.yml`, keeping all heavy local commands behind `build-gate`; rerun gate parity, actionlint, Swift format, shell checks, and strict OpenSpec validation.
- [x] 10.4 Run the smallest shared, Android, and iOS suites after each vertical slice, then run the complete documented local gate through `build-gate` for Gradle/Xcode work and record exact pass/fail output without claiming live Platform, physical-device, guaranteed background, signing, provider, or store proof.
- [x] 10.5 Review the final diff for scope, secrets/private content, direct internal/BlobStore calls, guessed receipt routes, database migrations/version negotiation, stale generated files, unsafe paths, missing erasure participants, accessibility, and unrelated edits; fix findings and rerun affected checks.
- [x] 10.6 Mark every task complete only after its named evidence is observed, validate `openspec validate --all --strict` and `openspec validate --archived` expectations, then sync/archive the change before the requested commit, `main` integration, push, exact-SHA hosted gate verification, worktree removal, and branch deletion.
