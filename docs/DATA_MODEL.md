# Mobile local data model

The current Room schema binds one staged artifact and one upload transfer journal to the original
capture local ID/idempotency key. Checkpoints retain declaration/session/status facts; a verified
blob receipt remains distinct from Platform operation acceptance. During development schema version
1 is edited in place and tests create it from empty storage; no migration or parallel version exists.

## Shared durable state

- capture queue aggregate: stable local ID/idempotency key, exact URL/text-note/opaque staged-file
  payload, owner/source lane and sequence, lifecycle, attempt/eligibility/lease, safe failure,
  authoritative operation binding, conflict ID, and minimized monotonic projection.
- staged-file descriptors: opaque path/token, hash, size, media type, created/expiry/upload state.
- operation bindings/progress/result summaries.
- collections/tags/capability cache and bounded library/search projection.
- preferences, current schema identity, and sync checkpoints.

## Native sensitive storage

Device credentials and keys live only in Android Keystore/iOS Keychain. Native file access handles App Group/content URI/security-scoped details.

## Constraints

Owner/device scope is explicit. Queue state transitions are transactional and database uniqueness
protects idempotency keys and source sequences. Secrets are not in the shared DB, backups, or logs.
Staged filenames are internal/opaque. Cache is non-authoritative and safely rebuildable. Retention
for completed history, failed captures, staged files, cached content, logout/revoke, and device wipe
remains a later explicit policy. Android/iOS tests create the one current schema from an empty store
and exercise close/reopen behavior; there are no migrations.
