import Foundation
import Shared
import XCTest

final class IosLibraryRoutingTests: XCTestCase {
  func testColdAndWarmLinksSelectTheSameSharedDestination() throws {
    let fixture = try makeController()
    defer { fixture.controller.close() }
    let link = "ratatoskr://library/analyses/\(Self.id)"

    XCTAssertTrue(fixture.controller.acceptLibraryLink(value: link))
    XCTAssertEqual(fixture.controller.pendingLibraryRouteId(), Self.id)
    XCTAssertTrue(fixture.controller.acceptLibraryLink(value: link))
    XCTAssertEqual(fixture.controller.pendingLibraryRouteId(), Self.id)
  }

  func testInvalidExternalLinkDoesNotChangeRoute() throws {
    let fixture = try makeController()
    defer { fixture.controller.close() }
    XCTAssertTrue(
      fixture.controller.acceptLibraryLink(value: "ratatoskr://library/social/x/\(Self.id)")
    )
    let before = fixture.controller.pendingLibraryRouteId()

    XCTAssertFalse(
      fixture.controller.acceptLibraryLink(value: "ratatoskr://library/social/facebook/\(Self.id)")
    )
    XCTAssertEqual(fixture.controller.pendingLibraryRouteId(), before)
  }

  private func makeController() throws -> (controller: IosApplicationController, directory: URL) {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
    return (
      IosApplicationController(
        queuePath: directory.appendingPathComponent("queue.sqlite").path,
        keychainAccessGroup: "",
        scheduleNativeWake: { _ in }
      ),
      directory
    )
  }

  private static let id = "abcdef01-0000-4000-8000-000000000001"
}

final class IosLocalStorageSurfaceSmokeTests: XCTestCase {
  func testUsageIntegrationPendingAndClearConfirmationAreHostedByIosShell() async throws {
    let root = FileManager.default.temporaryDirectory
      .appendingPathComponent("ratatoskr-storage-smoke-\(UUID().uuidString)", isDirectory: true)
    let staging = root.appendingPathComponent("ratatoskr-staging", isDirectory: true)
    try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
    try Data([1, 2, 3, 4, 5]).write(to: staging.appendingPathComponent("artifact-1"))
    var eraseCalls = 0
    let controller = IosApplicationController(
      queuePath: root.appendingPathComponent("capture-queue.sqlite").path,
      keychainAccessGroup: "",
      scheduleNativeWake: { _ in },
      onProvenRevocation: {},
      localArtifactRoots: [staging.path],
      eraseLocalData: {
        eraseCalls += 1
        try? FileManager.default.removeItem(at: staging)
        return KotlinBoolean(bool: true)
      })
    defer {
      controller.close()
      try? FileManager.default.removeItem(at: root)
    }

    let ready = try XCTUnwrap(controller.localStorageState() as? LocalStorageStateReady)
    XCTAssertEqual(ready.usage.artifactCount, 1)
    XCTAssertEqual(ready.usage.totalBytes, 5)
    XCTAssertEqual(ready.fileTransferAvailability, FileTransferAvailability.integrationpending)

    controller.dispatchLocalStorage(action: LocalStorageActionRequestClear.shared)
    XCTAssertTrue(controller.localStorageState() is LocalStorageStateConfirmClear)
    controller.dispatchLocalStorage(action: LocalStorageActionCancelClear.shared)
    XCTAssertTrue(controller.localStorageState() is LocalStorageStateReady)
    XCTAssertEqual(eraseCalls, 0)

    controller.dispatchLocalStorage(action: LocalStorageActionRequestClear.shared)
    controller.dispatchLocalStorage(action: LocalStorageActionConfirmClear.shared)
    for _ in 0..<100 {
      if controller.localStorageState() is LocalStorageStateEmpty { break }
      try await Task.sleep(for: .milliseconds(10))
    }
    XCTAssertTrue(controller.localStorageState() is LocalStorageStateEmpty)
    XCTAssertEqual(eraseCalls, 1)
  }
}
