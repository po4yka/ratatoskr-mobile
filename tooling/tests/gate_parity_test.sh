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

required_commands=(
    './gradlew --no-daemon ktlintCheck'
    './tooling/contracts/check.sh'
    './tooling/tests/contract_drift_test.sh'
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

if [[ "$failures" -eq 0 ]]; then
    echo "ok - ci_runs_shared_tests"
    echo "ok - documented_gate_matches_ci"
fi

exit "$failures"
