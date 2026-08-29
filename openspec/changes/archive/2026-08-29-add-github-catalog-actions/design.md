## Context

See `proposal.md` for motivation. Shared Compose navigation, device pairing, session rotation/revocation, capability discovery, authenticated request recovery, and contract-fixture presentation already exist. The pinned Platform OpenAPI does not contain GitHub routes, while Edge already reserves `/v1/gh` and `ratatoskr-contracts` publishes strict first-version repository preview/action schemas and fixtures. Those contracts define preview and action shapes but no catalog list/search page.

The current capability snapshot preserves top-level names and stale service names but discards service documents. GitHub enablement therefore needs a strict projection of the GitHub service document rather than a top-level name assumption. No provider token may cross the mobile boundary.

## Goals / Non-Goals

**Goals:**

- Make the shared Compose feature useful on both applications without widening native shells.
- Keep live preview/action traffic behind paired Platform authorization and one recovery attempt.
- Make track/star consent fresh, explicit, one-shot, and precise enough to distinguish Ratatoskr state from an external GitHub write.
- Preserve the shared contract's three independent outcome facts and uncertain-request idempotency.
- Pin enough upstream contract material to detect drift without a sibling-repository dependency.

**Non-Goals:**

- Direct GitHub API access, provider OAuth, provider-token storage, repository cloning, or Vault verification.
- Inventing a catalog list/search endpoint or claiming fixture catalog rows are live.
- Durable action outbox/background retry; an outcome-unknown action remains visible and requires an explicit same-key retry while this screen state exists.
- Share-target GitHub mode selection, notifications, or new deep-link routes.

## Decisions

### D1: Parse one strict GitHub service capability projection

Extend `CapabilitySnapshot` with an optional value projection containing `repository_preview` and the closed `repository_actions` set from the non-stale `service == "github"` document. The parser accepts only the documented keys and values, duplicate-free action arrays, `repository_preview == true`, and a current observation. Missing, stale, malformed, or ambiguous service entries yield no GitHub access.

Alternative: gate only on top-level `github.catalog` and `github.star_write`. Rejected because those names do not prove preview availability, acting-account context, or the current action set and would expose stale behavior.

### D2: Separate fixture browse from live detail and action authority

A small in-memory fixture catalog supplies deterministic browse/search rows and is labelled integration-pending. Search is local, case-insensitive, bounded, and stable. Selecting a row uses its canonical URL to request a live authenticated preview, which becomes the sole target/action authority. If live preview is unavailable, detail remains unavailable rather than substituting a fixture action target.

Alternative: call an inferred `/v1/gh/repos` search API. Rejected because no public first-version list/search contract or fixture defines its request, pagination, authorization, or response.

### D3: Pin schemas and canonical fixtures, then use explicit strict Kotlin DTOs

Copy the four upstream JSON schemas plus valid/invalid preview/action fixtures into `contracts/github/`, record the exact `ratatoskr-contracts` commit and digests in a lock file, and make the contract check verify every digest plus a deliberate mutation failure. Kotlin serialization DTOs stay transport-private and enforce the schema's cross-field rules before mapping to closed domain values. Fixture tests prove wire compatibility and reject invalid upstream fixtures.

Alternative: add a JSON-Schema code-generation dependency. Rejected because this bounded first-version surface does not justify a new production/build dependency, and schema generators do not enforce the canonical Rust cross-field aggregate invariants by themselves.

### D4: Reuse the authenticated request executor for `/v1/gh`

`PlatformGithubApi` exposes preview and action calls over Ktor with redirect refusal and bounded response decoding. `AuthorizedGithubRepository` wraps it with `DeviceAuthorizedRequestExecutor`, so 401 follows the existing one-refresh/recovery/revocation behavior. Transport outcomes distinguish retryable unavailable, invalid response, unauthorized, and outcome unknown for an action whose request may have been accepted.

Alternative: let the UI obtain access tokens. Rejected because it duplicates session rotation and increases credential exposure.

### D5: Model confirmation as a one-shot UDF selection

The detail store owns `PendingConfirmation` containing mode plus an immutable preview fingerprint. Selection performs no I/O. Confirm compares the pending fingerprint with the latest preview and capability projection, removes pending state before launching the request, then creates an identity bundle. Cancellation also consumes pending state. Metadata remains an explicit button action but does not require the track/star confirmation sheet.

The identity factory is injected at the public store seam for deterministic tests and produces bounded `mobile-confirmation:<uuid>` and `mobile-github-action.<uuid>` values in production. An uncertain action retains this bundle so explicit retry reuses the same idempotency key.

Alternative: Boolean `confirmed` state. Rejected because it can survive target/account changes, be replayed after recomposition, and cannot bind consent to the disclosed effect.

### D6: Project every component instead of flattening the aggregate

Transport validation derives the aggregate from the three decoded components and rejects mismatches. Presentation always lists metadata, provider star, and desired backup. `accepted` is worded as desired policy accepted for publication, while only a later Vault-owned contract could establish backup completion.

Alternative: show only succeeded/failed/partial. Rejected because it loses the partial-result and authority boundaries required by the contract.

### D7: Wire one shared graph into existing app navigation

Add a GitHub graph beside the library graph and a shared route reachable from the paired home. Android and iOS containers construct the same repository/store/surface using existing Ktor engines and session manager. No manifest, entitlement, share-extension, or platform permission changes are needed. Android Compose instrumentation and common/iOS KMP tests cover the same public states; existing full Xcode and Gradle gates prove shell linkage.

## Risks / Trade-offs

- [Fixture browse can be mistaken for live catalog search] → Label it on every list/detail entry, keep it reset-on-restart, and assert that browse/search performs zero Platform calls.
- [A service deploy implements different `/v1/gh` wire behavior before OpenAPI publication] → Fail closed on strict fixtures and report integration pending rather than accept additive or malformed drift silently.
- [Process death loses an uncertain action identity] → This item keeps same-key retry only in active screen state and names durable action outbox recovery as a non-goal; never imply automatic recovery.
- [A confirmation becomes stale while visible] → Bind it to target/account/actions plus current capability projection and invalidate it before submission.
- [Large or hostile provider strings affect UI] → Reject them at the transport boundary and render accepted strings only as inert Compose text.

## Migration Plan

1. Land the pinned contracts, strict domain/transport types, and RED/GREEN shared tests.
2. Wire the shared graph and Compose surfaces into Android and iOS shells, then add platform smoke/render evidence.
3. Update plan/status/security/interface/testing documentation and run the full local gate.
4. Sync and archive OpenSpec, commit the task branch, fast-forward `main`, push, and require exact-SHA hosted checks before cleanup.

Rollback is a normal revert of this additive mobile surface. It changes no server schema, local database schema, permission, entitlement, or provider authorization.
