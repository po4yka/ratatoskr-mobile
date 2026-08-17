# Mobile implementation plan

1. Establish Android/iOS projects, KMP boundary ADR, generated contracts, lint/test/build CI.
2. Implement device pairing with Keystore/Keychain and Platform capability discovery.
3. Implement shared capture/queue models and local persistence/migrations.
4. Build Android Share Target, staging, offline submission, and operation status.
5. Build iOS Share Extension/App Group staging, offline submission, and operation status.
6. Add notes/collections/tags and article/social routing.
7. Add GitHub metadata/track/star with explicit write confirmation.
8. Add resumable file upload, retention/cleanup, background constraints, revoke/clear-data.
9. Add library/search/deep links/notifications, accessibility and privacy polish.
10. Add real-device lifecycle, release signing/store profiles, and workspace integration.

Definition of Done: URL/file capture survives termination/offline without duplication/data loss; credentials are protected; writes explicit; staged cleanup safe; accessibility/security/platform/workspace tests pass.
