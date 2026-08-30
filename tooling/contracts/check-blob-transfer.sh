#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
contract_root="${RATATOSKR_BLOB_TRANSFER_CONTRACTS:-$repository_root/contracts/blob-transfer}"
generated_root="${RATATOSKR_BLOB_TRANSFER_GENERATED:-$repository_root/shared/src/commonMain/kotlin/com/ratatoskr/mobile/transfer/generated}"
lock="$contract_root/ratatoskr-contracts.lock.json"

[[ -f "$lock" ]] || {
    echo "blob-transfer contract lock is missing" >&2
    exit 1
}

pinned_paths="$(jq -er '.files | keys[]' "$lock")"
actual_paths="$({
    find "$contract_root" -type f ! -name 'ratatoskr-contracts.lock.json' -print |
        sed "s#^$contract_root/##" |
        sort
})"
if [[ "$pinned_paths" != "$actual_paths" ]]; then
    echo "blob-transfer contract file set differs from lock" >&2
    exit 1
fi

while IFS= read -r relative_path; do
    expected="$(jq -er --arg path "$relative_path" '.files[$path]' "$lock")"
    actual="$(shasum -a 256 "$contract_root/$relative_path" | awk '{print $1}')"
    if [[ "$actual" != "$expected" ]]; then
        echo "blob-transfer contract digest mismatch: $relative_path" >&2
        exit 1
    fi
    jq -e . "$contract_root/$relative_path" >/dev/null
done <<<"$pinned_paths"

[[ "$(find "$contract_root/schemas" -type f -name '*.json' | wc -l | tr -d ' ')" == "6" ]] || {
    echo "blob-transfer schema inventory must contain the six published wire documents" >&2
    exit 1
}
jq -e '.declared_size_bytes == 65536 and .chunk_size_bytes == 65536 and .digest.algorithm == "sha256"' \
    "$contract_root/fixtures/upload-session-request/minimal-declaration.json" >/dev/null
jq -e '.session_state == "open" and .received_chunks == [0, 1, 3] and .missing_chunks_count == 1' \
    "$contract_root/fixtures/upload-status-response/status-response-partial.json" >/dev/null
jq -e '.outcome == "stored" and .blob_ref.digest.algorithm == "sha256"' \
    "$contract_root/fixtures/upload-completion-outcome/completion-outcome-stored.json" >/dev/null

generation_root="$(mktemp -d /tmp/ratatoskr-blob-generated-check.XXXXXX)"
trap 'rm -rf "$generation_root"' EXIT
"$repository_root/gradlew" \
    --no-daemon \
    -p "$repository_root" \
    generateBlobTransferContracts \
    -PblobTransferContracts="$contract_root" \
    -PblobTransferOutput="$generation_root" \
    >/dev/null
diff -ru "$generated_root" "$generation_root"
