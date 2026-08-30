#!/usr/bin/env bash

set -u

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
failures=0

production_roots=(
    "$repository_root/androidApp/src/main"
    "$repository_root/shared/src/commonMain"
    "$repository_root/shared/src/androidMain"
    "$repository_root/shared/src/iosMain"
    "$repository_root/iosApp/Ratatoskr"
    "$repository_root/iosApp/RatatoskrShare"
    "$repository_root/iosApp/ShareSupport"
)

scan_sources() {
    local roots=("$@")
    local files=()
    while IFS= read -r file; do files+=("$file"); done < <(
        find "${roots[@]}" -type f \( -name '*.kt' -o -name '*.swift' \) -print 2>/dev/null | sort
    )
    [[ "${#files[@]}" -gt 0 ]] || return 0

    if grep -En \
        '(android\.util\.Log|(^|[^[:alnum:]_])(Log\.[vdiewtf]|println|print|NSLog)[[:space:]]*\(|Crashlytics|SentrySDK|recordException[[:space:]]*\(|addBreadcrumb[[:space:]]*\(|setUserID[[:space:]]*\(|setCustomValue[[:space:]]*\()' \
        "${files[@]}" >/dev/null; then
        return 1
    fi

    local file
    for file in "${files[@]}"; do
        if [[ "$file" != */diagnostics/MobileDiagnostics.kt ]] && grep -Eq 'Logger\.' "$file"; then
            return 1
        fi
        if [[ "$file" == */diagnostics/MobileDiagnostics.kt ]] &&
            grep -Eq 'MobileDiagnosticRecord.*(String|Throwable|Exception|Map|Any|ByteArray)' "$file"; then
            return 1
        fi
    done
    return 0
}

if scan_sources "${production_roots[@]}"; then
    echo "ok - production diagnostics are content free"
else
    echo "not ok - production source contains a forbidden logging or crash-metadata path"
    failures=$((failures + 1))
fi

temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/ratatoskr-privacy-gate.XXXXXX")"
# shellcheck disable=SC2329 # Invoked indirectly by the EXIT trap.
cleanup() {
    find "$temporary_root" -type f -delete 2>/dev/null || true
    find "$temporary_root" -depth -type d -exec rmdir {} \; 2>/dev/null || true
}
trap cleanup EXIT

assert_mutation_rejected() {
    local label="$1"
    local relative_path="$2"
    local content="$3"
    local fixture_root="$temporary_root/$label"
    mkdir -p "$fixture_root/$(dirname "$relative_path")"
    printf '%s\n' "$content" >"$fixture_root/$relative_path"
    if scan_sources "$fixture_root"; then
        echo "not ok - mutation accepted: $label"
        failures=$((failures + 1))
    else
        echo "ok - mutation rejected: $label"
    fi
}

assert_mutation_rejected android_log Main.kt 'fun leak() = android.util.Log.d("Ratatoskr", "private-url")'
assert_mutation_rejected kotlin_print Main.kt 'fun leak() = println("private-note")'
assert_mutation_rejected swift_print Main.swift 'func leak() { print("private-title") }'
assert_mutation_rejected swift_nslog Main.swift 'func leak() { NSLog("private-file") }'
assert_mutation_rejected raw_throwable Main.kt 'fun leak(error: Throwable) = Crashlytics.recordException(error)'
assert_mutation_rejected breadcrumb_metadata Main.swift 'func leak() { SentrySDK.addBreadcrumb(Breadcrumb()) }'
assert_mutation_rejected direct_kermit Main.kt 'fun leak(searchQuery: String) = Logger.i { searchQuery }'
assert_mutation_rejected diagnostic_content_field diagnostics/MobileDiagnostics.kt \
    'data class MobileDiagnosticRecord(val url: String, val event: String)'

exit "$failures"
