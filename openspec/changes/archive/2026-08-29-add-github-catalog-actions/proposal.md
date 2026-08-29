## Why

Ratatoskr Mobile cannot yet browse GitHub catalog entries or request metadata, tracking, and provider-star actions. Adding the flow now closes plan item 7 while keeping provider writes explicit and preserving the component-level truth defined by the shared GitHub interaction contracts.

## What Changes

- Add a shared Compose GitHub catalog browser, bounded search, repository detail, and capability-unavailable states.
- Consume the strict `ratatoskr-contracts` repository preview/action shapes through the paired Platform `/v1/gh` boundary; keep the browse/search dataset explicitly fixture-only until a public catalog-list contract exists.
- Require a fresh, one-shot confirmation for both `track` and `star`; the prompt names the repository, distinguishes Ratatoskr desired-backup tracking from the external GitHub write, and names the opaque connected-account reference for `star`.
- Generate a stable idempotency key and bounded mobile confirmation-evidence reference only after confirmation, never transport provider credentials, and invalidate pending confirmation when target, account, or capabilities change.
- Render metadata, provider-star, and desired-backup outcomes independently so `partial`, refused, skipped, failed, already-applied, and accepted-policy results cannot be collapsed into success or backup-complete claims.
- Pin the shared GitHub contract schemas/fixtures with a drift check and extend shared/JVM, Android Compose, iOS KMP, documentation, and CI gates.

## Capabilities

### New Capabilities

- `mobile-github-catalog-actions`: Shared mobile browse/detail behavior, capability gating, explicit confirmation, authenticated Platform actions, and truthful component outcomes for GitHub repositories.

### Modified Capabilities

None.

## Impact

- Shared KMP domain, transport, UDF stores, generated/validated contract models, and shared Compose navigation/surfaces.
- Thin Android and iOS application graphs that supply the existing paired session/capability boundary; no new platform permission, entitlement, or provider credential storage.
- Contract pin/drift tooling, shared fixtures, tests, README/interface/threat-model/testing documentation, and existing Android/iOS CI command families.
- Platform compatibility: preview and actions target `/v1/gh/repositories/preview` and `/v1/gh/repositories/actions`; catalog browse/search remains marked integration-pending until Platform publishes a first-version public list/search contract.
