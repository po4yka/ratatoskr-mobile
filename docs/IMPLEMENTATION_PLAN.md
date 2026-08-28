# Mobile implementation plan

1. **Complete (2026-08-28):** establish Android/iOS projects, KMP boundary ADR, generated contracts,
   and lint/test/build CI. Evidence is limited to repository tests, Android/JVM and iOS Simulator
   shared tests, Android debug assembly, unsigned iOS Simulator build, contract drift/mutation
   checks, and the hosted CI definition; it is not feature, signing, device, provider, or live
   Platform proof.
2. **Complete (2026-08-28):** implement device pairing with Keystore/Keychain and Platform
   capability discovery. Evidence covers the contract handshake matrix, serialized refresh and
   bounded device-root recovery, paired-elsewhere revocation, current-session fail-closed
   capabilities, Android Emulator Keystore, app-hosted iOS Simulator Keychain, and CI definitions;
   it is not live Platform, physical-device, signing, or provider evidence.
3. **Complete (2026-08-28):** implement shared URL/text-note/staged-file capture models, monotonic
   operation projections, and a durable bounded Room KMP queue from one current schema. Evidence
   covers common Android/JVM and iOS Simulator behavior tests, Android Emulator and iOS Simulator
   close/reopen tests, and the CI definitions; it is not share-target, submission-worker, live
   Platform, physical-device file-protection, or staged-file lifecycle proof.
4. Build Android Share Target, staging, offline submission, and operation status.
5. Build iOS Share Extension/App Group staging, offline submission, and operation status.
6. Add notes/collections/tags and article/social routing.
7. Add GitHub metadata/track/star with explicit write confirmation.
8. Add resumable file upload, retention/cleanup, background constraints, revoke/clear-data.
9. Add library/search/deep links/notifications, accessibility and privacy polish.
10. Add real-device lifecycle, release signing/store profiles, and workspace integration.

Definition of Done: URL/file capture survives termination/offline without duplication/data loss; credentials are protected; writes explicit; staged cleanup safe; accessibility/security/platform/workspace tests pass.
