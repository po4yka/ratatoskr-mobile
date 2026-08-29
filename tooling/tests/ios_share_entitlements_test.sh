#!/usr/bin/env bash

set -u

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
project="$repository_root/iosApp/Ratatoskr.xcodeproj/project.pbxproj"
failures=0

assert_exact_array() {
    local plist="$1"
    local key="$2"
    local expected="$3"
    local label="$4"
    local actual
    actual="$(/usr/libexec/PlistBuddy -c "Print :$key:0" "$plist" 2>/dev/null || true)"
    local count
    count="$(/usr/libexec/PlistBuddy -c "Print :$key" "$plist" 2>/dev/null | grep -c '^    ' || true)"
    if [[ "$actual" != "$expected" || "$count" -ne 1 ]]; then
        echo "not ok - $label"
        failures=$((failures + 1))
    fi
}

app_entitlements="$repository_root/iosApp/Ratatoskr/Ratatoskr.entitlements"
extension_entitlements="$repository_root/iosApp/RatatoskrShare/RatatoskrShare.entitlements"
test_entitlements="$repository_root/iosApp/RatatoskrTests/RatatoskrTestHost.entitlements"

for plist in "$app_entitlements" "$extension_entitlements" "$test_entitlements"; do
    assert_exact_array "$plist" "com.apple.security.application-groups" \
        "group.com.ratatoskr.mobile" "exact_app_group_${plist##*/}"
    assert_exact_array "$plist" "keychain-access-groups" \
        '$(AppIdentifierPrefix)com.ratatoskr.mobile.shared' "exact_keychain_group_${plist##*/}"
    if [[ -f "$plist" ]] && grep -Eq '\*|Ratatoskr Next' "$plist"; then
        echo "not ok - no_wildcard_or_unrelated_group_${plist##*/}"
        failures=$((failures + 1))
    fi
done

required_project_markers=(
    'CODE_SIGN_ENTITLEMENTS = Ratatoskr/Ratatoskr.entitlements;'
    'CODE_SIGN_ENTITLEMENTS = RatatoskrShare/RatatoskrShare.entitlements;'
    'RatatoskrShare.appex in Embed App Extensions'
)
for marker in "${required_project_markers[@]}"; do
    if ! grep -Fq -- "$marker" "$project"; then
        echo "not ok - project_configuration_missing: $marker"
        failures=$((failures + 1))
    fi
done

if ! grep -Fq -- 'com.ratatoskr.mobile.submission.refresh' "$repository_root/iosApp/Ratatoskr/Info.plist"; then
    echo "not ok - reviewed_background_identifier_missing"
    failures=$((failures + 1))
fi

if [[ "$failures" -eq 0 ]]; then
    echo "ok - ios_share_entitlements_are_exact"
    echo "ok - extension_is_embedded_and_background_identifier_is_reviewed"
fi

exit "$failures"
