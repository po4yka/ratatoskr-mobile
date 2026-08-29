import Foundation
import XCTest

final class AppGroupInboxImporterTests: XCTestCase {
  private var containerURL: URL!

  override func setUpWithError() throws {
    containerURL = FileManager.default.temporaryDirectory
      .appendingPathComponent("ratatoskr-inbox-\(UUID().uuidString)", isDirectory: true)
  }

  override func tearDownWithError() throws {
    try? FileManager.default.removeItem(at: containerURL)
  }

  func test_published_item_is_claimed_once() async throws {
    let published = try publish(id: id(1))
    let inbox = AppGroupInbox(containerURL: containerURL)
    let claim = try await inbox.claimNext()

    XCTAssertEqual(claim?.envelope.id, id(1))
    XCTAssertFalse(FileManager.default.fileExists(atPath: published.path))
    let second = try await inbox.claimNext()
    XCTAssertNil(second)
  }

  func test_temporary_and_unrelated_files_are_ignored() async throws {
    let inboxURL = containerURL.appendingPathComponent("ShareInbox", isDirectory: true)
    try FileManager.default.createDirectory(at: inboxURL, withIntermediateDirectories: true)
    let temporary = inboxURL.appendingPathComponent(".partial.tmp")
    let unrelated = containerURL.appendingPathComponent("keep.txt")
    try Data("partial".utf8).write(to: temporary)
    try Data("keep".utf8).write(to: unrelated)

    let claim = try await AppGroupInbox(containerURL: containerURL).claimNext()
    XCTAssertNil(claim)
    XCTAssertTrue(FileManager.default.fileExists(atPath: temporary.path))
    XCTAssertTrue(FileManager.default.fileExists(atPath: unrelated.path))
  }

  func test_concurrent_import_claims_once() async throws {
    try publish(id: id(2))
    let inbox = AppGroupInbox(containerURL: containerURL)
    async let first = inbox.claimNext()
    async let second = inbox.claimNext()
    let claims = try await [first, second].compactMap { $0 }

    XCTAssertEqual(claims.map(\.envelope.id), [id(2)])
  }

  func test_cancel_removes_only_claimed_item() async throws {
    try publish(id: id(3))
    let unrelated = containerURL.appendingPathComponent("unrelated.bin")
    try FileManager.default.createDirectory(at: containerURL, withIntermediateDirectories: true)
    try Data([0x01]).write(to: unrelated)
    let inbox = AppGroupInbox(containerURL: containerURL)
    let possibleClaim = try await inbox.claimNext()
    let claim = try XCTUnwrap(possibleClaim)

    try await inbox.cancel(claim)

    XCTAssertFalse(FileManager.default.fileExists(atPath: claim.fileURL.path))
    XCTAssertTrue(FileManager.default.fileExists(atPath: unrelated.path))
  }

  func test_queue_failure_keeps_claim_recoverable() async throws {
    try publish(id: id(4))
    let first = AppGroupInbox(containerURL: containerURL)
    let possibleClaim = try await first.claimNext()
    let claim = try XCTUnwrap(possibleClaim)

    await first.retain(claim)
    let recovered = try await AppGroupInbox(containerURL: containerURL).claimNext()

    XCTAssertEqual(recovered?.envelope, claim.envelope)
  }

  func test_restart_recovers_processing_item() async throws {
    try publish(id: id(5))
    let first = AppGroupInbox(containerURL: containerURL)
    let possibleOriginal = try await first.claimNext()
    let original = try XCTUnwrap(possibleOriginal)

    let restarted = AppGroupInbox(containerURL: containerURL)
    let recovered = try await restarted.claimNext()

    XCTAssertEqual(recovered?.envelope, original.envelope)
    XCTAssertEqual(recovered?.fileURL, original.fileURL)
  }

  func test_rejected_state_is_bounded() async throws {
    let inboxURL = containerURL.appendingPathComponent("ShareInbox", isDirectory: true)
    try FileManager.default.createDirectory(at: inboxURL, withIntermediateDirectories: true)
    for value in 1...40 {
      try Data(#"{"schema":1,"invalid":true}"#.utf8).write(
        to: inboxURL.appendingPathComponent("\(id(value).uuidString.lowercased()).json")
      )
    }

    let claim = try await AppGroupInbox(containerURL: containerURL).claimNext()
    XCTAssertNil(claim)

    let rejected = containerURL.appendingPathComponent("ShareRejected", isDirectory: true)
    let retained = try FileManager.default.contentsOfDirectory(atPath: rejected.path)
    XCTAssertLessThanOrEqual(retained.count, 32)
  }

  @discardableResult
  private func publish(id: UUID) throws -> URL {
    try AppGroupEnvelopeStore(
      rootURL: containerURL.appendingPathComponent("ShareInbox", isDirectory: true)
    ).publish(
      ShareEnvelope(
        id: id,
        capturedAt: Date(timeIntervalSince1970: 1_788_000_000),
        intake: .url(originalText: "https://example.com", url: URL(string: "https://example.com")!)
      )
    )
  }

  private func id(_ value: Int) -> UUID {
    UUID(uuidString: String(format: "00000000-0000-4000-8000-%012d", value))!
  }
}
