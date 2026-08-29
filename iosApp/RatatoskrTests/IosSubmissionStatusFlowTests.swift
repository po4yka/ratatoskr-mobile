import XCTest

final class IosSubmissionStatusFlowTests: XCTestCase {
  func testImportedURLRequiresConfirmation() {
    let flow = IosSubmissionStatusFlow()
    flow.importedURL()
    XCTAssertEqual(flow.presentation, .confirmationRequired)
  }

  func testConfirmedURLReportsQueuedOffline() {
    let flow = IosSubmissionStatusFlow()
    flow.importedURL()
    flow.confirmedOffline()
    XCTAssertEqual(flow.presentation, .queuedOffline)
  }

  func testFixtureAcceptanceOpensOperationDetail() {
    let flow = IosSubmissionStatusFlow()
    flow.accepted(operationId: "1518c249-a3d3-4a9b-954a-5a110a3f9dcb")
    XCTAssertEqual(
      flow.presentation,
      .operationDetail("1518c249-a3d3-4a9b-954a-5a110a3f9dcb")
    )
  }

  func testRunningPartialFailedCancelledAndCompletedFixturesRender() {
    let base = Date(timeIntervalSince1970: 1_788_000_000)
    for (offset, expected) in [
      "running", "partially_succeeded", "failed", "cancelled", "succeeded",
    ].enumerated() {
      let flow = IosSubmissionStatusFlow()
      flow.applyFixture(status: expected, changedAt: base.addingTimeInterval(Double(offset)))
      XCTAssertEqual(flow.renderedStatus, expected)
    }
  }

  func testSceneInactiveStopsPolling() {
    let flow = IosSubmissionStatusFlow()
    flow.setSceneActive(false)
    XCTAssertFalse(flow.pollingVisible)
  }

  func testReauthStateIsActionable() {
    let flow = IosSubmissionStatusFlow()
    flow.requireReauthentication()
    XCTAssertEqual(flow.presentation, .reauthenticationRequired)
  }
}
