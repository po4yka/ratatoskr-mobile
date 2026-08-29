#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
document="$repository_root/contracts/platform-openapi.json"

jq -e '
  .paths["/v1/library/search"].get.operationId == "searchLibrary" and
  .paths["/v1/library/items/{analysis_id}/read-state"].put.operationId == "replaceLibraryReadState" and
  .components.schemas.LibraryPage.required == ["items", "limit", "offset", "has_more"] and
  .components.schemas.LibraryItem.required == ["analysis_id", "document_id", "title", "read_state"] and
  .components.schemas.ReplaceReadState.additionalProperties == false and
  .components.schemas.ReplaceReadState.required == ["read_state"] and
  .components.schemas.ReadStateResource.additionalProperties == false and
  .components.schemas.ReadStateResource.required == ["read_state"]
' "$document" >/dev/null || {
  echo "not ok - pinned_document_exposes_library_search_and_exact_read_state_resource: missing /v1/library/search or exact read-state resource" >&2
  exit 1
}

echo "ok - pinned_document_exposes_library_search_and_exact_read_state_resource"
