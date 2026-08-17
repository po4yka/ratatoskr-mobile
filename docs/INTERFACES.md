# Mobile interfaces

## Android

`ACTION_SEND`/`ACTION_SEND_MULTIPLE`, content URIs and persistable grants where available, app links, WorkManager/background transfer, notifications, Keystore, and native UI/navigation.

## iOS

Share Extension `NSExtensionItem`/item providers, App Group handoff, security-scoped/file copying, background URLSession, universal links, notifications, Keychain, and native UI/navigation.

## Shared/Platform

Generated API models, device pair/refresh/revoke, captures/uploads, operation status, capabilities, library/search, collections/tags, and idempotency.

## Rules

Inbound content is type/size/count/time bounded; file access is copied before source permission expires. KMP may own pure models/queue policy/network abstractions, but native adapters own lifecycle and secure storage. Deep links are allowlisted and do not carry secrets. Errors distinguish invalid input, staging/storage, offline, auth/revoked, unsupported capability, upload, provider partial, and terminal states.
