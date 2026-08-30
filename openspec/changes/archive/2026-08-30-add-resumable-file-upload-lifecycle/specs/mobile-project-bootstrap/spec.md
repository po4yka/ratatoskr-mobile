## ADDED Requirements

### Requirement: Product CI enforces file-transfer and erasure behavior

The documented local gate and hosted product CI SHALL verify pinned/generated blob-transfer drift
including mutation, shared resumption/finalization/retention/scheduling/erasure behavior, Android
protected file staging and WorkManager constraints on an emulator, iOS App Group file handoff and
background/Keychain/container erasure on a simulator, and both application builds. Fixture receiver
evidence SHALL be labelled separately from live Platform, physical-device, guaranteed background,
signing, provider, and store evidence.

#### Scenario: Clean file-transfer change passes CI
- **WHEN** CI runs from a clean checkout with synthetic files and the pinned contract receiver harness
- **THEN** contract drift, shared tests, Android instrumentation/build, iOS simulator tests/build, gate parity, and strict OpenSpec validation all complete successfully

#### Scenario: Required file test is removed from one gate
- **WHEN** a documented or hosted gate omits contract mutation, resume-after-interruption, cleanup bounds, scheduling decisions, complete erasure, or native file staging coverage
- **THEN** gate-parity validation fails and identifies the missing command or marker

#### Scenario: Evidence boundary remains explicit
- **WHEN** the deterministic receiver harness and simulators pass while Platform exposes no public receipt binding
- **THEN** retained evidence describes contract-fixture and simulator coverage and continues to mark live Platform upload integration pending
