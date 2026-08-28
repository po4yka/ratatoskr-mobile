#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
pinned_openapi="${RATATOSKR_PLATFORM_OPENAPI:-$repository_root/contracts/platform-openapi.json}"
committed_models="${RATATOSKR_GENERATED_MODELS:-$repository_root/shared/src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model}"
lock_manifest="$repository_root/contracts/platform-openapi.lock.json"
generator_config="$repository_root/contracts/openapi-generator-config.json"
generation_root="$(mktemp -d /tmp/ratatoskr-contract-check.XXXXXX)"
generated_models="$generation_root/src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model"

expected_document_digest="$(jq -er '.document_sha256' "$lock_manifest")"
actual_document_digest="$(shasum -a 256 "$pinned_openapi" | awk '{print $1}')"
if [[ "$actual_document_digest" != "$expected_document_digest" ]]; then
    echo "pinned Platform OpenAPI digest mismatch" >&2
    exit 1
fi

expected_config_digest="$(jq -er '.generator_config_sha256' "$lock_manifest")"
actual_config_digest="$(shasum -a 256 "$generator_config" | awk '{print $1}')"
if [[ "$actual_config_digest" != "$expected_config_digest" ]]; then
    echo "OpenAPI Generator configuration digest mismatch" >&2
    exit 1
fi

"$repository_root/gradlew" \
    --no-daemon \
    -p "$repository_root" \
    generateContracts \
    -PplatformOpenApi="$pinned_openapi" \
    -PcontractOutput="$generation_root" \
    >/dev/null

diff -ru "$committed_models" "$generated_models"
