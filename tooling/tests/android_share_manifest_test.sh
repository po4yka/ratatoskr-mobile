#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
manifest="$repo_root/androidApp/src/main/AndroidManifest.xml"
activity="$repo_root/androidApp/src/main/kotlin/com/ratatoskr/mobile/MainActivity.kt"
test_name="manifest_declares_only_explicit_text_share_target"

fail() {
  printf 'not ok - %s: %s\n' "$test_name" "$1" >&2
  exit 1
}

[[ -f "$manifest" ]] || fail "Android manifest is absent"
[[ -f "$activity" ]] || fail "MainActivity is absent"

required_manifest_terms=(
  '<action android:name="android.intent.action.SEND" />'
  '<category android:name="android.intent.category.DEFAULT" />'
  '<data android:mimeType="text/plain" />'
  'android:allowBackup="false"'
)

for term in "${required_manifest_terms[@]}"; do
  grep -Fq "$term" "$manifest" || fail "manifest does not contain: $term"
done

for forbidden in 'android.intent.action.SEND_MULTIPLE' 'application/pdf' 'image/*'; do
  if grep -Fq "$forbidden" "$manifest"; then
    fail "manifest exposes out-of-scope share input: $forbidden"
  fi
done

grep -Fq 'ACTION_VIEW_OPERATION' "$activity" || fail "internal operation-detail action is absent"
grep -Fq 'EXTRA_OPERATION_ID' "$activity" || fail "operation-detail identifier contract is absent"

printf 'ok - %s\n' "$test_name"
