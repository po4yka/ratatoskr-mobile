import XCTest

final class IosSubmissionSchedulerTests: XCTestCase {
  private let now = Date(timeIntervalSince1970: 1_788_000_000)

  func testRegistrationUsesReviewedIdentifier() {
    let fixture = makeFixture()

    fixture.scheduler.start()

    XCTAssertEqual(fixture.boundary.registeredIdentifier, IosSubmissionScheduler.identifier)
  }

  func testScheduleUsesPersistedEarliestTime() {
    let fixture = makeFixture()
    let wake = now.addingTimeInterval(90)

    fixture.scheduler.schedule(nextWakeEpochMilliseconds: wake.epochMilliseconds)

    XCTAssertEqual(fixture.store.nextWake, wake)
    XCTAssertEqual(fixture.boundary.submissions.last?.earliest, wake)
  }

  func testDuplicateRequestsAreCoalesced() {
    let fixture = makeFixture()
    let wake = now.addingTimeInterval(90)

    fixture.scheduler.schedule(nextWakeEpochMilliseconds: wake.epochMilliseconds)
    fixture.scheduler.schedule(
      nextWakeEpochMilliseconds: wake.addingTimeInterval(30).epochMilliseconds)

    XCTAssertEqual(fixture.boundary.submissions.count, 1)
  }

  func testEarlyWakeSubmitsNothing() async {
    let fixture = makeFixture()
    fixture.scheduler.start()
    fixture.scheduler.schedule(
      nextWakeEpochMilliseconds: now.addingTimeInterval(90).epochMilliseconds)
    guard let launch = fixture.boundary.launch else { return XCTFail("background handler missing") }
    let execution = FakeExecution()

    launch(execution)
    await execution.waitForCompletion()

    XCTAssertEqual(fixture.drain.calls, 0)
    XCTAssertEqual(execution.success, true)
  }

  func testExpirationCancelsDrain() async {
    let fixture = makeFixture { try await Task.never() }
    fixture.scheduler.start()
    fixture.scheduler.schedule(nextWakeEpochMilliseconds: now.epochMilliseconds)
    guard let launch = fixture.boundary.launch else { return XCTFail("background handler missing") }
    let execution = FakeExecution()

    launch(execution)
    await fixture.drain.waitUntilCalled()
    execution.expirationHandler?()
    await execution.waitForCompletion()

    XCTAssertTrue(fixture.drain.wasCancelled)
    XCTAssertEqual(execution.success, false)
  }

  func testRevocationCompletesWithoutRescheduleStorm() async {
    let fixture = makeFixture { nil }
    fixture.scheduler.start()
    fixture.scheduler.schedule(nextWakeEpochMilliseconds: now.epochMilliseconds)
    guard let launch = fixture.boundary.launch else { return XCTFail("background handler missing") }
    let execution = FakeExecution()

    launch(execution)
    await execution.waitForCompletion()

    XCTAssertEqual(execution.success, true)
    XCTAssertNil(fixture.store.nextWake)
    XCTAssertEqual(fixture.boundary.submissions.count, 1)
  }

  func testSceneActivationRepairsMissingRequest() {
    let fixture = makeFixture()
    fixture.store.nextWake = now.addingTimeInterval(90)

    fixture.scheduler.sceneActivated()

    XCTAssertEqual(fixture.boundary.submissions.count, 1)
  }

  private func makeFixture(
    result: @escaping () async throws -> Int64? = { nil }
  ) -> Fixture {
    let boundary = FakeBoundary()
    let store = MemoryWakeStore()
    let drain = DrainSpy(result: result)
    let scheduler = IosSubmissionScheduler(
      boundary: boundary,
      store: store,
      now: { self.now },
      drain: drain.call
    )
    return Fixture(scheduler: scheduler, boundary: boundary, store: store, drain: drain)
  }
}

private struct Fixture {
  let scheduler: IosSubmissionScheduler
  let boundary: FakeBoundary
  let store: MemoryWakeStore
  let drain: DrainSpy
}

private final class FakeBoundary: IosBackgroundTaskBoundary {
  struct Submission {
    let identifier: String
    let earliest: Date?
  }
  var registeredIdentifier: String?
  var launch: ((IosBackgroundExecution) -> Void)?
  var submissions: [Submission] = []

  func register(identifier: String, launch: @escaping (IosBackgroundExecution) -> Void) {
    registeredIdentifier = identifier
    self.launch = launch
  }

  func submit(identifier: String, earliest: Date?) throws {
    submissions.append(Submission(identifier: identifier, earliest: earliest))
  }

  func cancel(identifier: String) {}
}

private final class MemoryWakeStore: IosWakeStateStore {
  var nextWake: Date?
}

private final class FakeExecution: IosBackgroundExecution, @unchecked Sendable {
  var expirationHandler: (() -> Void)?
  var success: Bool?
  private var completion: CheckedContinuation<Void, Never>?

  func complete(success: Bool) {
    self.success = success
    completion?.resume()
    completion = nil
  }

  func waitForCompletion() async {
    if success != nil { return }
    await withCheckedContinuation { completion = $0 }
  }
}

private final class DrainSpy {
  private let result: () async throws -> Int64?
  private(set) var calls = 0
  private(set) var wasCancelled = false

  init(result: @escaping () async throws -> Int64?) { self.result = result }

  func call() async throws -> Int64? {
    calls += 1
    do {
      return try await result()
    } catch is CancellationError {
      wasCancelled = true
      throw CancellationError()
    }
  }

  func waitUntilCalled() async {
    while calls == 0 { await Task.yield() }
  }
}

extension Date {
  fileprivate var epochMilliseconds: Int64 { Int64(timeIntervalSince1970 * 1_000) }
}

extension Task where Success == Never, Failure == Never {
  fileprivate static func never() async throws -> Int64? {
    try await withTaskCancellationHandler {
      while !Task.isCancelled { await Task.yield() }
      try Task.checkCancellation()
      return nil
    } onCancel: {
    }
  }
}
