#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
checker="$repository_root/tooling/contracts/check-blob-transfer.sh"
contract_root="$repository_root/contracts/blob-transfer"
generated_root="$repository_root/shared/src/commonMain/kotlin/com/ratatoskr/mobile/transfer/generated"

[[ -x "$checker" ]] || {
    echo "not ok - blob-transfer contract checker is missing" >&2
    exit 1
}

test_mutated_schema() {
    local fixture_root
    fixture_root="$(mktemp -d /tmp/ratatoskr-blob-contract.XXXXXX)"
    trap 'rm -rf "$fixture_root"' RETURN
    cp -R "$contract_root/." "$fixture_root/"
    printf '\n' >>"$fixture_root/schemas/upload-session-request.v1.schema.json"
    if RATATOSKR_BLOB_TRANSFER_CONTRACTS="$fixture_root" "$checker" >/dev/null 2>&1; then
        echo "not ok - mutated_pinned_transfer_schema_is_rejected" >&2
        return 1
    fi
    echo "ok - mutated_pinned_transfer_schema_is_rejected"
}

test_mutated_generated_type() {
    local fixture_root
    fixture_root="$(mktemp -d /tmp/ratatoskr-blob-generated.XXXXXX)"
    trap 'rm -rf "$fixture_root"' RETURN
    cp -R "$generated_root/." "$fixture_root/"
    printf '\n' >>"$fixture_root/UploadSessionRequest.kt"
    if RATATOSKR_BLOB_TRANSFER_GENERATED="$fixture_root" "$checker" >/dev/null 2>&1; then
        echo "not ok - mutated_generated_transfer_type_is_rejected" >&2
        return 1
    fi
    echo "ok - mutated_generated_transfer_type_is_rejected"
}

test_mutated_schema
test_mutated_generated_type
