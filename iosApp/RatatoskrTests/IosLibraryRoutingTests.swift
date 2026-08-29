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
