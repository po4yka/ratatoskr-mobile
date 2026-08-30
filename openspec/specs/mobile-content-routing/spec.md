# Mobile Content Routing Specification

## Purpose

Routes validated Ratatoskr library links to the correct article, social, or AI-archive reader while keeping unsupported contracts and hostile identifiers outside the application navigation graph.

## Requirements

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

### Requirement: Canonical HTTPS links resolve through one exact route table

The client SHALL resolve canonical HTTPS links delivered by Android App Links or iOS Universal Links only when the scheme is lowercase `https`, the authority exactly matches a build-configured allowlisted link host with no credentials or non-default port, and the path exactly names a supported analysis, collection, or repository destination. Native shells SHALL forward the original URL to the same shared parser used by navigation and SHALL NOT infer or decode provider authority themselves.

#### Scenario: Analysis link resolves

- **WHEN** the operating system delivers `https://<configured-host>/analyses/123e4567-e89b-42d3-a456-426614174000`
- **THEN** the shared route table returns the analysis-reader destination for that exact opaque identifier

#### Scenario: Collection link resolves without claiming ownership

- **WHEN** the operating system delivers `https://<configured-host>/collections/research`
- **THEN** the shared route table returns the collection destination and the authenticated repository later decides whether it is live, fixture-only, absent, or not owned

#### Scenario: Repository link resolves

- **WHEN** the operating system delivers `https://<configured-host>/repos/ratatoskr/mobile`
- **THEN** the shared route table returns the repository destination with the exact validated owner and repository name

#### Scenario: Different host is rejected

- **WHEN** a syntactically valid supported path arrives on a host not present in the exact build allowlist
- **THEN** the route is rejected and no app destination is opened

### Requirement: Link parsing rejects ambiguous or content-bearing variants

HTTPS and custom-scheme route parsing SHALL reject mixed-case schemes or hosts, user information, explicit ports, query strings, fragments, percent encoding, dot segments, empty or extra path segments, Unicode lookalike hosts, noncanonical UUIDs, invalid collection slugs, and invalid repository owner/name segments. Links SHALL contain only route identity and SHALL NOT contain titles, queries, notes, tokens, result bodies, or provider credentials.

#### Scenario: Route rejection table is exhaustive

- **WHEN** the route table receives each invalid scheme, host, authority, encoding, traversal, identifier, query, fragment, or extra-segment fixture
- **THEN** every fixture is rejected without partial navigation or network access

#### Scenario: Notification route contains only opaque identity

- **WHEN** a completed-operation notification opens an app link
- **THEN** the link contains only an allowlisted destination and canonical opaque identifier and the authenticated read determines whether content is visible

### Requirement: Native declarations do not overstate verified association

Android and iOS SHALL declare only link hosts supplied by the build configuration intended for that application profile. Repository tests MAY use a synthetic host to validate dispatch, but the app SHALL NOT report a link as domain-verified unless the corresponding deployed Android and Apple association documents have been observed for the signed application identifiers.

#### Scenario: Development build has no production association evidence

- **WHEN** a development or unsigned simulator build uses the synthetic test host
- **THEN** routing tests may pass but product status continues to identify live domain association as unverified
