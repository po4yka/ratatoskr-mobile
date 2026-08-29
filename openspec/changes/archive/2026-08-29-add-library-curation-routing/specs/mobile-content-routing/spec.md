## Purpose

Routes validated Ratatoskr library links to the correct article, social, or AI-archive reader while keeping unsupported contracts and hostile identifiers outside the application navigation graph.

## ADDED Requirements

### Requirement: Library deep links use an explicit allowlist
The client SHALL accept only the `ratatoskr` scheme, the `library` authority, one recognized route shape, closed provider names, and canonical opaque UUID identifiers. Query strings, fragments, credentials, extra path segments, traversal syntax, malformed encodings, and unknown source families or providers SHALL be rejected before repository access.

#### Scenario: Valid article link opens analysis reader
- **WHEN** the router receives `ratatoskr://library/analyses/00000000-0000-4000-8000-000000000001`
- **THEN** it selects the article analysis reader with only that opaque identifier

#### Scenario: Hostile or ambiguous link is rejected
- **WHEN** the router receives a library link with an unknown provider, malformed identifier, extra segment, query, fragment, credentials, or non-Ratatoskr scheme
- **THEN** it returns the same safe invalid-link result and performs no repository read

### Requirement: Source families route to distinct readers
The routing table SHALL map article analyses to the analysis reader, `x`, `instagram`, and `threads` sources to the social reader, and `chatgpt` and `claude` items to the AI-archive reader. It SHALL preserve the supplied source family, provider, and opaque identifier without treating one reader family's fixture as another family's content.

#### Scenario: Social providers use the social reader
- **WHEN** valid X, Instagram, and Threads fixture links are routed
- **THEN** each route selects the social reader with its original provider and opaque identifier

#### Scenario: AI archive providers use the archive reader
- **WHEN** valid ChatGPT and Claude fixture links are routed
- **THEN** each route selects the AI-archive reader with its original provider and opaque identifier

### Requirement: Unpublished reader contracts fail truthfully
Social and AI-archive reader destinations SHALL load only supplied contract-fixed fixture content until their public Platform read contracts exist. A valid route with no matching fixture or unavailable capability SHALL show an explained unavailable or integration-pending state and SHALL NOT call an internal service, guess a public endpoint, or open the external source as if it were archived content.

#### Scenario: Valid social identifier has no fixture
- **WHEN** a valid social reader route names an item absent from the fixed fixture repository
- **THEN** the reader reports unavailable fixture content with integration pending and performs no network request

#### Scenario: Fixture readers render supplied provenance
- **WHEN** matching social or AI-archive fixture content supplies provider, acquisition or import provenance, completeness, and ordered content
- **THEN** the selected reader renders those supplied facts distinctly without inferring native Saved state, provider authority, or archive completeness

### Requirement: Application shells pass only validated routes to shared navigation
Android and iOS application entry points SHALL parse incoming library links through the same shared routing table before activating shared Compose navigation. Invalid external input SHALL not mutate the current route, and a valid cold-start or already-running-app link SHALL select the same destination.

#### Scenario: Cold and warm routing agree
- **WHEN** the same valid article, social, or AI-archive link enters a cold application and an already-running application
- **THEN** both entry paths produce the same shared reader destination without duplicate repository loads caused by recomposition
