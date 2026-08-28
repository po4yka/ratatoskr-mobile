#!/usr/bin/env bash

set -u

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
test_root="$(mktemp -d /tmp/ratatoskr-contract-drift-test.XXXXXX)"
failures=0

jq '.info.title = (.info.title + " mutation")' \
    "$repository_root/contracts/platform-openapi.json" \
    >"$test_root/mutated-openapi.json"

if RATATOSKR_PLATFORM_OPENAPI="$test_root/mutated-openapi.json" \
    "$repository_root/tooling/contracts/check.sh" >/dev/null 2>&1; then
    echo "not ok - mutated_pin_is_rejected: valid JSON mutation was accepted"
    failures=$((failures + 1))
else
    echo "ok - mutated_pin_is_rejected"
fi

cp -R \
    "$repository_root/shared/src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model" \
    "$test_root/stale-models"
stale_file="$test_root/stale-models/CapabilityDocument.kt"
printf '\n// synthetic stale output\n' >>"$stale_file"

if RATATOSKR_GENERATED_MODELS="$test_root/stale-models" \
    "$repository_root/tooling/contracts/check.sh" >/dev/null 2>&1; then
    echo "not ok - stale_generated_model_is_rejected: changed generated model was accepted"
    failures=$((failures + 1))
else
    echo "ok - stale_generated_model_is_rejected"
fi

if "$repository_root/tooling/contracts/check.sh" >/dev/null 2>&1 && \
    "$repository_root/tooling/contracts/check.sh" >/dev/null 2>&1; then
    echo "ok - clean_generation_is_deterministic"
else
    echo "not ok - clean_generation_is_deterministic: clean generation drifted"
    failures=$((failures + 1))
fi

cp -R \
    "$repository_root/shared/src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model" \
    "$test_root/missing-models"
rm "$test_root/missing-models/CapabilityDocument.kt"
if RATATOSKR_GENERATED_MODELS="$test_root/missing-models" \
    "$repository_root/tooling/contracts/check.sh" >/dev/null 2>&1; then
    echo "not ok - missing_generated_model_is_rejected: missing generated model was accepted"
    failures=$((failures + 1))
else
    echo "ok - missing_generated_model_is_rejected"
fi

cp -R \
    "$repository_root/shared/src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model" \
    "$test_root/orphan-models"
printf 'package com.ratatoskr.mobile.api.generated.model\n' \
    >"$test_root/orphan-models/OrphanModel.kt"
if RATATOSKR_GENERATED_MODELS="$test_root/orphan-models" \
    "$repository_root/tooling/contracts/check.sh" >/dev/null 2>&1; then
    echo "not ok - orphan_generated_model_is_rejected: orphan generated model was accepted"
    failures=$((failures + 1))
else
    echo "ok - orphan_generated_model_is_rejected"
fi

exit "$failures"
