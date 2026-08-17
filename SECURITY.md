# Security Policy for Ratatoskr Mobile

Report vulnerabilities privately. Do not publish device tokens, shared private files/URLs/text, local database dumps, Keychain/Keystore contents, production endpoints, or screenshots containing personal archives.

Security review is required for Share Target/Extension, file providers/bookmarks, deep links, device pairing, biometric/key access, local cache, background transfers, notifications, screenshots/clipboard, external-write confirmation, and diagnostics.

Baseline: validate hostile external input; copy only required data into app-controlled staged storage; enforce size/type/time/retention limits; Keychain/Keystore secrets; TLS; Platform API only; no provider credentials; explicit confirmation for external writes; protected local data; redacted logs/notifications; safe revoke/clear-data flows.
