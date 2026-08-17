# Mobile local data model

## Shared durable state

- capture drafts and queue items: local ID/idempotency, kind, minimized metadata, options, lifecycle, attempts, next retry, safe error.
- staged-file descriptors: opaque path/token, hash, size, media type, created/expiry/upload state.
- operation bindings/progress/result summaries.
- collections/tags/capability cache and bounded library/search projection.
- preferences, migration version, and sync checkpoints.

## Native sensitive storage

Device credentials and keys live only in Android Keystore/iOS Keychain. Native file access handles App Group/content URI/security-scoped details.

## Constraints

Owner/device scope is explicit. State transitions are transactional. Secrets are not in shared DB/backups/logs. Staged filenames are internal/opaque. Cache is non-authoritative and safely rebuildable. Retention handles completed queue history, failed drafts, staged files, cached content, logout/revoke, and device wipe. Android/iOS migrations are tested from prior versions.
