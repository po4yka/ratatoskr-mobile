#!/usr/bin/env bash

set -u

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
development="$repository_root/DEVELOPMENT.md"
workflow="$repository_root/.github/workflows/ci.yml"
failures=0

assert_in_file() {
    local file="$1"
    local needle="$2"
    local label="$3"

    if [[ ! -f "$file" ]] || ! grep -Fq -- "$needle" "$file"; then
        echo "not ok - $label: missing '$needle' in ${file#"$repository_root/"}"
        failures=$((failures + 1))
    fi
}

for shared_test in ':shared:testDebugUnitTest' ':shared:iosSimulatorArm64Test'; do
    assert_in_file "$development" "$shared_test" "ci_runs_shared_tests"
    assert_in_file "$workflow" "$shared_test" "ci_runs_shared_tests"
done

queue_gate_markers=(
    'CaptureQueueIdempotencyTest'
    'AndroidCaptureQueuePersistenceTest'
    'IosCaptureQueuePersistenceTest'
)

for marker in "${queue_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_capture_queue_current_schema_tests"
    assert_in_file "$workflow" "$marker" "ci_runs_capture_queue_current_schema_tests"
done

required_commands=(
    './gradlew --no-daemon ktlintCheck'
    './tooling/contracts/check.sh'
    './tooling/tests/contract_drift_test.sh'
    './tooling/tests/platform_library_contract_test.sh'
    './tooling/tests/app_link_shell_test.sh'
    './tooling/tests/privacy_source_gate_test.sh'
    ':androidApp:assembleDebug'
    ':shared:connectedDebugAndroidTest'
    'swift format lint --recursive --strict iosApp'
    'xcodebuild -quiet -project iosApp/Ratatoskr.xcodeproj -scheme Ratatoskr'
    'xcodebuild -project iosApp/Ratatoskr.xcodeproj'
    'openspec validate --all --strict'
    'openspec validate --archived'
)

for command in "${required_commands[@]}"; do
    assert_in_file "$development" "$command" "documented_gate_matches_ci"
    assert_in_file "$workflow" "$command" "documented_gate_matches_ci"
done

android_share_gate_markers=(
    ':androidApp:connectedDebugAndroidTest'
    'AndroidShareIntentParserTest'
    'ShareStagingUiTest'
    'CaptureSubmissionWorkerTest'
    'OperationStatusUiTest'
    'CaptureStatusNotificationTest'
    'AndroidShareSmokeTest'
    'LibraryDeepLinkIntentTest'
    'LibraryUiTest'
    'android-share-test-reports'
)

for marker in "${android_share_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_android_share_status_and_smoke"
    assert_in_file "$workflow" "$marker" "ci_runs_android_share_status_and_smoke"
done

ios_share_gate_markers=(
    './tooling/tests/ios_share_entitlements_test.sh'
    'ShareExtensionParserTests'
    'AppGroupEnvelopeTests'
    'AppGroupInboxImporterTests'
    'IosKeychainCredentialStorageTests'
    'IosSubmissionSchedulerTests'
    'IosSubmissionStatusFlowTests'
    'IosShareSmokeTests'
    'IosLibraryRoutingTests'
    'RatatoskrShare.appex'
    'ios-share-test-results'
)

for marker in "${ios_share_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_ios_share_status_and_smoke"
    assert_in_file "$workflow" "$marker" "ci_runs_ios_share_status_and_smoke"
done

library_gate_markers=(
    'PlatformLibraryApiTest'
    'LibraryListStoreTest'
    'FixtureUserContentRepositoryTest'
    'LibraryReaderStoreTest'
    'ContentRouteTableTest'
    'LibrarySearchStoreTest'
    'CompletionNotificationStoreTest'
    'MobileStringsTest'
    'AccessiblePaletteTest'
    'MobileDiagnosticsTest'
)

for marker in "${library_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_library_contract_and_state_tests"
    assert_in_file "$workflow" "$marker" "ci_runs_library_contract_and_state_tests"
done

github_gate_markers=(
    './tooling/tests/github_contract_fixture_test.sh'
    'GithubContractCodecTest'
    'PlatformGithubApiTest'
    'GithubCatalogStoreTest'
    'GithubConfirmationStoreTest'
    'GithubActionOutcomeStoreTest'
    'GithubCatalogUiTest'
    'github_graph_shares_device_authorization_capabilities_and_fixture_browse_without_provider_credentials'
)

for marker in "${github_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_github_contract_state_and_surface_tests"
    assert_in_file "$workflow" "$marker" "ci_runs_github_contract_state_and_surface_tests"
done

file_lifecycle_gate_markers=(
    './tooling/tests/blob_transfer_contract_drift_test.sh'
    'BlobTransferContractTest'
    'ResumableUploadCoordinatorTest'
    'ArtifactRetentionPolicyTest'
    'TransferSchedulingPolicyTest'
    'LocalDataErasureCoordinatorTest'
    'AndroidStagedArtifactStoreTest'
    'FileUploadWorkerTest'
    'AndroidLocalDataErasureInstrumentedTest'
    'LocalStorageUiTest'
    'IosFileUploadSchedulerTests'
    'IosLocalDataErasureTests'
    'IosLocalStorageSurfaceSmokeTests'
)

for marker in "${file_lifecycle_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_file_transfer_retention_and_erasure"
    assert_in_file "$workflow" "$marker" "ci_runs_file_transfer_retention_and_erasure"
done

item_nine_android_gate_markers=(
    'AndroidAppLinkIntentTest'
    'LibrarySearchUiTest'
    'AccessibilityUiTest'
    'shell_wires_search_https_routes_notification_truth_russian_and_accessible_navigation'
)

for marker in "${item_nine_android_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_android_search_links_notifications_and_accessibility"
    assert_in_file "$workflow" "$marker" "ci_runs_android_search_links_notifications_and_accessibility"
done

item_nine_ios_gate_markers=(
    'IosNotificationPermissionTests'
    'testConfiguredUniversalLinksForwardRawAndResolveCanonicalDestinations'
    'testForeignOrAmbiguousUniversalLinksAreRejected'
    'testBrowsingUserActivityUsesTheSharedRouteTable'
    'testShellWiresUniversalLinksNotificationTruthRussianAndPrivateCanaryAbsence'
)

for marker in "${item_nine_ios_gate_markers[@]}"; do
    assert_in_file "$development" "$marker" "ci_runs_ios_links_notifications_accessibility_and_privacy"
    assert_in_file "$workflow" "$marker" "ci_runs_ios_links_notifications_accessibility_and_privacy"
done

if [[ "$failures" -eq 0 ]]; then
    echo "ok - ci_runs_shared_tests"
    echo "ok - documented_gate_matches_ci"
    echo "ok - ci_runs_capture_queue_current_schema_tests"
    echo "ok - ci_runs_android_share_status_and_smoke"
    echo "ok - ci_runs_ios_share_status_and_smoke"
    echo "ok - ci_runs_library_contract_and_state_tests"
    echo "ok - ci_runs_github_contract_state_and_surface_tests"
    echo "ok - ci_runs_file_transfer_retention_and_erasure"
    echo "ok - ci_runs_android_search_links_notifications_and_accessibility"
    echo "ok - ci_runs_ios_links_notifications_accessibility_and_privacy"
fi

exit "$failures"
