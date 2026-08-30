## Purpose

Ensures Ratatoskr Mobile discovery, status, and settings surfaces remain perceivable and operable while diagnostics and system integrations reveal no private user content.

## ADDED Requirements

### Requirement: Interactive surfaces expose complete accessibility semantics

Shared and native user-facing surfaces SHALL expose a unique accessible name, role, enabled/disabled state, selected or checked state where applicable, and state-change announcement for search, navigation, notification preference, read-state, favorite, collection, tag, retry, confirmation, and destructive actions. Headings and progress/error/partial states SHALL be discoverable in a logical focus order without relying only on color or motion.

#### Scenario: Screen reader traverses search results

- **WHEN** a search page contains unread and read matches plus a retry or next-page action
- **THEN** accessibility inspection finds an ordered heading, named search field, result actions whose labels include their purpose and state, and a named retry or next-page action with no unlabeled interactive node

#### Scenario: Notification state is announced without color dependence

- **WHEN** notifications are denied, unavailable, disabled, or enabled
- **THEN** the permission/preference state and available action are exposed as text and semantics rather than color alone

### Requirement: Text, contrast, targets, and motion meet the mobile baseline

User text SHALL scale without clipping essential actions at supported Android font scale and iOS Dynamic Type settings. Interactive targets SHALL be at least 48 density-independent units in shared Compose surfaces and at least 44 points in native iOS controls. Normal text SHALL meet a 4.5:1 contrast ratio, large text and non-text controls SHALL meet 3:1, and essential state changes SHALL remain understandable when reduced motion is enabled.

#### Scenario: Automated accessibility baseline passes

- **WHEN** the deterministic Android and iOS accessibility fixtures render search, route failure, notification settings, operation partial, and confirmation states
- **THEN** target-size, label/state, contrast, and scalable-text checks pass with no essential content clipped or conveyed only by animation

### Requirement: English and Russian resources preserve action meaning

New user-visible search, route, notification, accessibility, and privacy text SHALL be supplied through English and Russian resources. Neither locale SHALL truncate or weaken the distinction among unavailable, offline, permission denied, integration pending, completed, partial, failed, and destructive actions in the tested phone layout.

#### Scenario: Locale state table remains complete

- **WHEN** each new surface is rendered with English and Russian resources
- **THEN** every required state and action has a nonblank localized label and the tested layouts preserve the complete action meaning

### Requirement: Diagnostics and crash metadata are content free

Production diagnostics, logs, metric labels, breadcrumbs, and crash metadata SHALL use a closed event name and bounded non-content classification only. They SHALL NOT accept or emit search queries, URLs, titles, notes, filenames, collection or tag names, provider usernames, document or analysis identifiers, operation identifiers, device or subscription tokens, authorization material, response bodies, or raw backend errors. No production crash-reporting SDK SHALL be introduced by this change.

#### Scenario: Sensitive fixture never reaches diagnostics

- **WHEN** search, deep-link, notification, partial-operation, and error flows process a fixture containing unique private values
- **THEN** captured diagnostics and crash metadata contain none of those values and include only allowlisted event and outcome classifications

#### Scenario: Forbidden logging API enters production source

- **WHEN** a production source adds direct platform logging, standard output, raw throwable reporting, or unbounded breadcrumb metadata outside the approved diagnostic boundary
- **THEN** the privacy source gate fails before the build is accepted

### Requirement: Accessibility and privacy evidence stays scoped

The repository SHALL record the inspected surfaces, automated checks, emulator/simulator configuration, findings, and unresolved physical-device or service boundaries. Passing repository checks SHALL NOT be represented as VoiceOver/TalkBack physical-device acceptance, deployed domain association, push-provider delivery, live Platform subscription, signing, or store-review evidence.

#### Scenario: Checklist records an unverified boundary

- **WHEN** the accessibility and privacy checklist is completed from repository, emulator, and simulator evidence only
- **THEN** it names each passing check and explicitly leaves physical-device, live-domain, push-provider, and store evidence unverified
