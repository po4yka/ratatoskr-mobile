# Mobile threat model

## Assets

Private shared URLs/text/files, staged copies, device credentials, local notes/cache, operation results, deep links, notifications, and release integrity.

## Threats and controls

- **Malicious share URI/file:** validate scheme/provider, copy with limits/timeouts, MIME sniff, no execution, opaque paths.
- **Permission expiry/data loss:** stage before extension exits; durable handoff and hash.
- **Credential theft:** one opaque origin-bound record; AES-GCM under a non-exportable Android
  Keystore key or a device-only, non-synchronizing Keychain item; no logs/shared DB/clipboard.
- **Refresh replay:** serialize credential mutation, persist an unusable-link marker before
  exchange, present each single-use refresh link at most once across process death, and use one
  bounded device-root recovery after an uncertain outcome.
- **Credential forwarding:** accept only canonical HTTPS origins and disable redirects on native
  Ktor clients.
- **Revoked authorization:** refusal of both refresh and device-root recovery clears local
  credentials and current-session capabilities before exposing re-pairing.
- **Deep-link spoof/replay:** allowlisted HTTPS/app routes, opaque one-time intents where needed, authenticated server state.
- **Duplicate/external write surprise:** idempotency plus explicit mode/confirmation and truthful partial results.
- **Local disclosure:** data protection/encryption policy, minimal cache, screenshot/notification privacy, safe logout/wipe.
- **Background abuse/battery drain:** OS schedulers, constraints, backoff, user-visible controls.
- **Supply-chain/release compromise:** pinned dependencies, signing, provenance, store/release verification.

Re-review for biometric gates, media preview/rendering, local search embeddings, widgets, cross-device sync, or additional share file types.
