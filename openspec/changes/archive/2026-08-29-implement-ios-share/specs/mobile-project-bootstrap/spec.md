## ADDED Requirements

### Requirement: iOS share and App Group behavior are part of the product gate

The repository SHALL provide a buildable iOS application with an embedded Share Extension and SHALL run deterministic checks for extension parsing, atomic App Group handoff, entitlement scope, shared queue submission, operation status, and a simulator-hosted share smoke in the documented local gate and hosted CI.

#### Scenario: iOS share gate runs from a clean checkout
- **WHEN** the documented iOS product gate runs with the pinned toolchain and a synthetic simulator profile
- **THEN** shared iOS tests, Swift lint, application and extension builds, parser and handoff XCTest, Keychain policy, submission and status fixtures, and the simulator smoke all pass without signing secrets or private captures

#### Scenario: Simulator smoke evidence is retained honestly
- **WHEN** hosted CI exercises the synthetic Share Extension handoff through the main-app queue and fixture operation flow
- **THEN** it retains the XCTest result or report as simulator evidence and does not label it live Platform, physical-device, background-execution, provider, signing, or App Store proof
