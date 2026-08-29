#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
contract_root="${RATATOSKR_GITHUB_CONTRACTS:-$repository_root/contracts/github}"
lock="$contract_root/ratatoskr-contracts.lock.json"

[[ -f "$lock" ]] || {
    echo "GitHub contract lock is missing" >&2
    exit 1
}

pinned_paths="$(jq -er '.files | keys[]' "$lock")"
actual_paths="$(
    find "$contract_root" -type f ! -name 'ratatoskr-contracts.lock.json' -print |
        sed "s#^$contract_root/##" |
        sort
)"

if [[ "$pinned_paths" != "$actual_paths" ]]; then
    echo "GitHub contract file set differs from lock" >&2
    exit 1
fi

while IFS= read -r relative_path; do
    expected="$(jq -er --arg path "$relative_path" '.files[$path]' "$lock")"
    actual="$(shasum -a 256 "$contract_root/$relative_path" | awk '{print $1}')"
    if [[ "$actual" != "$expected" ]]; then
        echo "GitHub contract digest mismatch: $relative_path" >&2
        exit 1
    fi
    jq -e . "$contract_root/$relative_path" >/dev/null
done <<<"$pinned_paths"

jq -e '
  .target.github_repository_numeric_id == 42 and
  .target.repository_full_name == "owner/repository" and
  .available_actions == ["metadata", "track", "star"]
' "$contract_root/fixtures/repository-preview-response/valid/repository.json" >/dev/null

jq -e '
  .aggregate == "partial" and
  .metadata.status == "succeeded" and
  .provider_star.status == "succeeded" and
  .desired_backup == {"status":"failed", "reason":"dependency_unavailable"}
' "$contract_root/fixtures/repository-action-result/valid/partial.json" >/dev/null
