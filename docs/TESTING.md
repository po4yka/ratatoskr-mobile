# Mobile testing strategy

Required suites:

- Shared pure-model/queue/idempotency/retry/migration tests.
- Android Share Target for URL/text/single/multiple content URI, permission expiry, process death, WorkManager, Keystore, app links, notifications.
- iOS Share Extension item-provider/file/App Group handoff, extension timeout/memory, app launch, background URLSession, Keychain, universal links.
- Hostile/oversized/unsupported files, low disk, hash mismatch, duplicate shares, offline/auth revoke/server retry/partial results.
- Staged-file retention/cleanup and no-content logs/notifications.
- Accessibility: screen readers, Dynamic Type/font scaling, contrast, focus, localization layout.
- Generated API/capability compatibility and workspace mobile -> Platform -> domain flow.

Use synthetic files/local servers and platform emulators/simulators plus selected real-device lifecycle tests. No personal provider data in fixtures.
