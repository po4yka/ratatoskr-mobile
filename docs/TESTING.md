# Mobile testing strategy

Required suites:

- Shared pure-model/queue/idempotency/retry and current-schema creation tests.
- Android Share Target for URL/text/single/multiple content URI, permission expiry, process death, WorkManager, Keystore, app links, notifications.
- iOS Share Extension item-provider/file/App Group handoff, extension timeout/memory, app launch, background URLSession, Keychain, universal links.
- Hostile/oversized/unsupported files, low disk, hash mismatch, duplicate shares, offline/auth revoke/server retry/partial results.
- Staged-file retention/cleanup and no-content logs/notifications.
- Accessibility: screen readers, Dynamic Type/font scaling, contrast, focus, localization layout.
- Generated API/capability compatibility and workspace mobile -> Platform -> domain flow.

Use synthetic files/local servers and platform emulators/simulators plus selected real-device lifecycle tests. No personal provider data in fixtures.

## Test-first

A change is planned before it is built, and the plan is a task list in which behaviour arrives in
pairs: one task adds a failing test, the next makes it pass. `openspec/config.yaml` carries that
rule, which is what puts it into every planning and implementation request rather than only into this
document.

The loop:

1. Write the test the scenario names. Run it. Confirm it fails, and read the failure — a test that
   fails because it does not compile has proved nothing about the behaviour.
2. Write the smallest change that makes it pass. Run it again.
3. Refactor only once it is green, adding no test and changing no behaviour.

Two checks stand behind this, and neither of them can see the order:

- `openspec validate --archived`, in `.github/workflows/openspec.yml`, fails when a change was
  archived with a task left unticked.
- A step in `.github/workflows/fleet.yml` fails when this repository holds a manifest and a `ci.yml`
  that never runs a test.

`ratatoskr-workspace/docs/QUALITY_GATES.md` records why the order itself is not checkable, rather
than leaving the gap to be discovered.
