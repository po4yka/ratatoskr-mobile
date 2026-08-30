# Accessibility and privacy checklist

> Reviewed: 2026-08-30
> Scope: plan item 9 shared search, exact links, completion-notification settings, and touched app surfaces

## Automated evidence

| Control | Evidence | Observed result | Proof boundary |
|---|---|---|---|
| Search and notification headings, named input, button roles, live status, and 48 dp targets | `AccessibilityUiTest` | 4 focused tests passed | Dedicated Android API 36 emulator |
| Library/read state, operation partial/error, GitHub confirmation, and clear-data semantics | `AccessibilityUiTest` plus affected Compose suites | 16 tests passed | Dedicated Android API 36 emulator, synthetic fixtures |
| Scalable/localized content | Russian catalog case at 2x font scale | Passed without semantic truncation or missing actions | Emulator semantics, not visual/device inspection |
| Logical traversal | Search field precedes retry/action controls | Passed | Compose semantics tree |
| Contrast | `AccessiblePaletteTest` | Normal text, large text, controls, and status pairs meet tested WCAG ratios | Token math; rendered-system variation remains |
| EN/RU completeness | `MobileStringsTest` | Every item-9 state/action key has English and Russian text with English fallback | Typed catalog |
| Android App Links | `AndroidAppLinkIntentTest` and `app_link_shell_test.sh` | Canonical configured routes pass; foreign and ambiguous forms fail closed | Manifest/parser only; no deployed association proof |
| iOS Universal Links | `IosLibraryRoutingTests` and `ios_share_entitlements_test.sh` | Raw browsing activity and exact associated-domain configuration pass | Hosted simulator/entitlement only; no deployed AASA proof |
| Notification consent | `CompletionNotificationStoreTest`, Android instrumentation, and `IosNotificationPermissionTests` | Integration-pending requests nothing; available fixture prompts once; denial does not reprompt; revoke clears | Policy/native adapter fixture; no APNs/FCM subscription or delivery |
| Content-free diagnostics | `MobileDiagnosticsTest` and `privacy_source_gate_test.sh` | Approved tree passes; eight injected logging/content-field mutations are rejected | Static source plus behavior canaries |
| Shell wiring and private-canary absence | Android and iOS item-9 shell smoke cases | Passed with synthetic paired state and routes | Emulator/simulator fixture, not live Platform |

The final local gate reran the complete repository families after review fixes: 57 Android app and
4 shared Android instrumented tests passed on the dedicated API 36 AVD; 65 hosted XCTest cases
passed on the dedicated iPhone 17 Pro/iOS 26.5 simulator; shared Android/JVM and iOS Simulator
tests, Kotlin/Swift lint, both application builds, generated-contract drift/mutation, shell/privacy
gates, workflow lint, and strict OpenSpec validation also passed.

## Manual inspection performed

- Confirmed the shared surfaces use explicit headings, status descriptions/live regions, named
  inputs, button roles, and the common 48 dp action/input primitives.
- Confirmed new user-visible search, routing, notification, loading, empty, offline, permission,
  and error text is sourced from the typed EN/RU catalog.
- Confirmed Android notifications and deep-link intents carry a generic outcome and opaque
  operation UUID only.
- Confirmed production notification state remains `IntegrationPending` while the pinned Platform
  OpenAPI has no subscribe path: there is no guessed endpoint, APNs/FCM registration, or token
  storage.
- Confirmed the diagnostic API accepts closed event/outcome enums only and production callers pass
  no query, URL, identifier, title, note, filename, payload, server error, throwable, or metadata.

## Remaining release evidence

- TalkBack and VoiceOver reading order, announcements, rotor/actions, and focus recovery on physical
  Android/iOS devices.
- Switch Control/keyboard operation, reduced motion, high-contrast OS modes, and visual clipping at
  the supported maximum font sizes across phone/tablet form factors.
- Deployed `assetlinks.json` and AASA files, DNS/TLS ownership, and cold/warm routing from real
  browsers and notifications.
- A reviewed public Platform subscription contract plus APNs/FCM token lifecycle, delivery,
  revocation, lock-screen redaction, and permission behavior on physical devices.
- Release signing, crash-reporting configuration audit, store privacy declarations, and store
  publication.

These gaps are not represented as passing acceptance evidence.

## Privacy impact and rollback

The change adds no content persistence, provider credentials, analytics fields, push token, or
backend endpoint. Search sends the explicit bounded query only to the paired public Platform
resource. Link configuration carries only opaque canonical identifiers. Revocation clears local
notification policy and native pending/delivered notifications.

Rollback is one client release: remove the search/navigation entry points and configured HTTPS
filters/associated domain, return notification availability to unavailable, and retain the exact
custom-scheme routes and existing library/operation behavior. No database migration or server-side
rollback is required because item 9 changes no current local schema or public contract.
