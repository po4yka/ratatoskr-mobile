## ADDED Requirements

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
