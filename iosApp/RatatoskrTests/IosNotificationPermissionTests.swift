import XCTest

@MainActor
final class IosNotificationPermissionTests: XCTestCase {
  func testIntegrationPendingRequestsNothing() async {
    let boundary = RecordingNotificationCenter()
    let adapter = IosNotificationPermissionAdapter(permission: .notDetermined, boundary: boundary)

    let state = await adapter.enable(contractAvailable: false)

    XCTAssertEqual(state, .integrationPending)
    XCTAssertEqual(boundary.requestCalls, 0)
    XCTAssertEqual(boundary.settingsCalls, 0)
  }

  func testExplicitAvailableEnableRequestsAuthorizationOnce() async {
    let boundary = RecordingNotificationCenter(requestResult: true)
    let adapter = IosNotificationPermissionAdapter(permission: .notDetermined, boundary: boundary)

    let first = await adapter.enable(contractAvailable: true)
    let second = await adapter.enable(contractAvailable: true)

    XCTAssertEqual(first, .granted)
    XCTAssertEqual(second, .granted)
    XCTAssertEqual(boundary.requestCalls, 1)
  }

  func testDeniedStateDoesNotReprompt() async {
    let boundary = RecordingNotificationCenter()
    let adapter = IosNotificationPermissionAdapter(permission: .denied, boundary: boundary)

    let first = await adapter.enable(contractAvailable: true)
    let second = await adapter.enable(contractAvailable: true)

    XCTAssertEqual(first, .denied)
    XCTAssertEqual(second, .denied)
    XCTAssertEqual(boundary.requestCalls, 0)
    XCTAssertEqual(boundary.settingsCalls, 1)
  }

  func testRevocationCancelsPendingAndDeliveredNotifications() {
    let boundary = RecordingNotificationCenter()
    let adapter = IosNotificationPermissionAdapter(permission: .granted, boundary: boundary)

    adapter.revoke()

    XCTAssertEqual(boundary.clearCalls, 1)
  }
}

@MainActor
private final class RecordingNotificationCenter: IosNotificationCenterBoundary {
  private let requestResult: Bool
  var requestCalls = 0
  var settingsCalls = 0
  var clearCalls = 0

  init(requestResult: Bool = false) {
    self.requestResult = requestResult
  }

  func requestAuthorization() async -> Bool {
    requestCalls += 1
    return requestResult
  }

  func openSettings() {
    settingsCalls += 1
  }

  func clearNotifications() {
    clearCalls += 1
  }
}
