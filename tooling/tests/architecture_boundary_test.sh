#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
adr="$repo_root/docs/adr/0001-kmp-boundary.md"
test_name="adr_0001_assigns_every_required_boundary"

fail() {
  printf 'not ok - %s: %s\n' "$test_name" "$1" >&2
  exit 1
}

[[ -f "$adr" ]] || fail "ADR-0001 is absent"

required_terms=(
  "Status: Accepted"
  "Compose Multiplatform"
  "Navigation 3"
  "AndroidX ViewModel"
  "UDF/MVVM"
  "StateFlow"
  "capture queue"
  "Ktor Client"
  "Android lifecycle"
  "iOS lifecycle"
  "ACTION_SEND"
  "Share Extension"
  "Keystore"
  "Keychain"
  "WorkManager"
  "background URLSession"
  "file access"
  "notifications"
  "accessibility"
  "Objective-C framework"
  "Swift Export"
)

for term in "${required_terms[@]}"; do
  grep -Fq "$term" "$adr" || fail "ADR-0001 does not assign or explain: $term"
done

printf 'ok - %s\n' "$test_name"
