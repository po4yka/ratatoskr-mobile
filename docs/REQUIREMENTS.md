# Mobile requirements

## Goals

1. Accept URLs, text, and supported files through Android Share Target and iOS Share Extension.
2. Let users add notes/collections/tags and select safe article/social/GitHub modes.
3. Persist an offline queue and staged files across process/extension termination.
4. Pair a device securely and submit only through Platform.
5. Show operation status, library/search results, and actionable errors accessibly.

## Non-goals

Provider OAuth/token storage, scraping, LLM inference, Git backup execution, direct internal-service/database access, or background behavior that violates OS policies.

## Requirements

- External inputs are validated, bounded, and copied into app-controlled staging before acknowledgment.
- Queue items have stable idempotency and crash-safe lifecycle.
- Staged files expire only after verified upload/terminal policy and are cleaned safely.
- Social acquisition is explicit user capture; GitHub `star`/write requires confirmation.
- Device credentials are revocable and protected by platform secure storage.
- UI works offline/degraded and meets accessibility/localization readiness.

First slice: share one URL offline -> queue -> reconnect -> Platform operation -> final status on Android and iOS.
