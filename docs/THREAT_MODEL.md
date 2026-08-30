# Mobile threat model

Shared files are hostile. Ratatoskr accepts reviewed types/sizes, validates content evidence,
sanitizes display names, uses opaque app-owned paths, refuses symlink traversal, and ends external
authority after atomic staging. Paths, content, tokens, and filenames do not enter scheduler data.
A proven revoke or confirmed clear-data writes an erase generation before cancellation, deletes
only registered roots, inventories residue, and removes its marker last. An interrupted wipe resumes
before Room, secure credentials, App Group inbox, or session restoration. Local clear never implies
server archive deletion. After a completed wipe the current process remains fail-closed and asks
the user to restart before re-pairing, so no closed or partly erased store can be reused.

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
- **Deep-link spoof/replay:** custom routes accept only exact lowercase
  `ratatoskr://library/{analyses|social|ai-archives}/...` shapes, while HTTPS routes accept only the
  configured ASCII-lowercase host and exact analysis/collection/repository paths. The parser rejects
  foreign hosts, ports, user-info, query, fragment, percent encoding, traversal, noncanonical IDs,
  and extra segments. Links carry opaque identifiers only, and live content still requires
  authenticated owner-scoped reads. Manifest/entitlement tests do not claim deployed association
  files, DNS, or TLS ownership.
- **Untrusted reader content:** render contract-fixed blocks, provenance, warnings, notes, and
  titles only through inert Compose text primitives; do not use WebView/HTML execution or infer
  provider Saved authority.
- **Fixture authority confusion:** label every local curation/reader fixture unsynchronized and
  integration-pending, reset it on process restart, and make fixture mutations issue zero Platform
  calls so preview state cannot masquerade as durable user content.
- **Android intent injection:** export only the documented `ACTION_SEND text/plain` surface; bound
  payload bytes; accept exactly one HTTP(S) URL; route internal status intents explicitly and
  validate the canonical operation UUID before any authenticated fetch.
- **Duplicate/external write surprise:** idempotency plus explicit mode/confirmation and truthful partial results.
- **Stale/replayed GitHub consent:** bind one pending track/star confirmation to immutable target,
  opaque account, preview actions, and current capability actions; consume before dispatch and
  invalidate on cancellation, replacement, or context change.
- **Provider credential exfiltration:** send GitHub operations only through paired Platform
  `/v1/gh`; the client stores no GitHub token, makes no direct provider request, follows no redirect,
  and renders only bounded contract-validated inert text.
- **False backup or fixture authority:** label browse/search unsynchronized, require live preview as
  action authority, preserve all three component outcomes, and describe accepted desired policy
  without claiming Vault backup completion.
- **Duplicate submission after crash:** persist one idempotency key before work, recover expired
  leases, reject stale claim tokens, converge on the same Platform operation, and fail closed if one
  key resolves to conflicting operations.
- **Queue cross-account disclosure:** owner-scope every record and claim; another instance/account
  cannot dequeue it.
- **Local disclosure:** Android private storage with backup disabled; iOS
  CompleteUntilFirstUserAuthentication protection for the queue database and sidecars; minimal
  projection/error data; screenshot/notification privacy and safe logout/wipe in their owning work.
- **Background abuse/battery drain:** OS schedulers, constraints, backoff, user-visible controls.
- **Private lock-screen disclosure:** Android notifications use generic outcome text and an opaque
  operation UUID only; URLs, titles, notes, tokens, and server result bodies never enter the
  notification, PendingIntent, WorkManager input, or diagnostic labels.
- **Notification permission or token overreach:** model Platform subscribe availability separately
  from OS permission; the current missing contract remains `IntegrationPending` and performs no
  prompt, APNs/FCM registration, token storage, or guessed request. Revocation removes local policy
  state and cancels pending/delivered native notifications.
- **Search/query and crash-log disclosure:** a closed diagnostics boundary accepts only event and
  outcome enums. A source mutation gate rejects direct Android/Swift/Kotlin logging, crash or
  breadcrumb metadata, raw throwables, direct Kermit use, and diagnostic content fields in
  production source. Search queries, link values, user identifiers, and server errors never enter
  diagnostic records.
- **Inaccessible or misleading state:** shared heading/status/input/action primitives expose roles,
  names, live state descriptions, traversal order, 48 dp targets, tested contrast, and EN/RU labels.
  Automated emulator/simulator checks do not replace physical-device TalkBack/VoiceOver and visual
  release review.
- **Supply-chain/release compromise:** pinned dependencies, signing, provenance, store/release verification.

Re-review for biometric gates, media preview/rendering, local search embeddings, widgets, cross-device sync, or additional share file types.
