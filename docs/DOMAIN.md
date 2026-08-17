# Mobile domain model

## Terms

- **Share intake:** platform-native short-lived receipt of external URL/text/file.
- **Capture draft:** normalized user-editable payload and explicit mode/options.
- **Staged file:** app-controlled local copy with size/hash/type/expiry and upload state.
- **Queue item:** durable submission with idempotency, attempts, and safe error.
- **Device connection:** Platform endpoint, public device identity, and secure credential.
- **Operation binding:** accepted operation and progress/result projection.
- **Local cache:** bounded non-authoritative library/search projection.

## Lifecycle

`received -> staged -> draft -> queued -> uploading -> accepted -> processing -> completed | failed | paused`

## Invariants

1. Share extension/target performs minimal bounded work.
2. Provider credentials/domain authority never live on device.
3. Queue restart/retry cannot duplicate backend effects.
4. Staged files are not deleted before verified handoff or explicit user action/expiry policy.
5. External writes are explicit and confirmed.
6. Local cache is replaceable and owner-authorized.
7. Platform-native lifecycle/security concerns are not forced into KMP abstractions.
