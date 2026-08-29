#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
lock="$repository_root/contracts/github/ratatoskr-contracts.lock.json"

if [[ ! -f "$lock" ]]; then
    echo "not ok - pinned_github_schemas_and_fixtures_match_the_reviewed_contract_revision: missing contracts/github/ratatoskr-contracts.lock.json" >&2
    exit 1
fi

producer_commit="$(jq -er '.producer_commit' "$lock")"
if [[ ! "$producer_commit" =~ ^[0-9a-f]{40}$ ]]; then
    echo "not ok - pinned_github_schemas_and_fixtures_match_the_reviewed_contract_revision: invalid producer commit" >&2
    exit 1
fi

expected_count="$(jq -er '.files | length' "$lock")"
if [[ "$expected_count" -ne 14 ]]; then
    echo "not ok - pinned_github_schemas_and_fixtures_match_the_reviewed_contract_revision: expected 14 pinned files" >&2
    exit 1
fi

"$repository_root/tooling/contracts/check-github.sh"
echo "ok - pinned_github_schemas_and_fixtures_match_the_reviewed_contract_revision"
