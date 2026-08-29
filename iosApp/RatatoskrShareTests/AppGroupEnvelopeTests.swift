import Foundation
import XCTest

final class AppGroupEnvelopeTests: XCTestCase {
  private var rootURL: URL!

  override func setUpWithError() throws {
    rootURL = FileManager.default.temporaryDirectory
      .appendingPathComponent("ratatoskr-envelope-\(UUID().uuidString)", isDirectory: true)
  }

  override func tearDownWithError() throws {
    try? FileManager.default.removeItem(at: rootURL)
  }

  func test_published_envelope_round_trips_exactly() throws {
    let envelope = fixture()
    let store = AppGroupEnvelopeStore(rootURL: rootURL)

    let published = try store.publish(envelope)

    XCTAssertEqual(try store.loadPublished(at: published), envelope)
    XCTAssertEqual(store.publishedURLs(), [published])
  }

  func test_temporary_write_is_not_importable() throws {
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let temporary = rootURL.appendingPathComponent(".\(UUID().uuidString).tmp")
    try Data("partial".utf8).write(to: temporary)

    XCTAssertEqual(AppGroupEnvelopeStore(rootURL: rootURL).publishedURLs(), [])
  }

  func test_oversized_or_malformed_envelope_is_refused() throws {
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let oversized = rootURL.appendingPathComponent("\(UUID().uuidString.lowercased()).json")
    try Data(repeating: 0x61, count: 128 * 1024 + 1).write(to: oversized)
    XCTAssertThrowsError(try AppGroupEnvelopeStore(rootURL: rootURL).loadPublished(at: oversized)) {
      XCTAssertEqual($0 as? ShareEnvelopeError, .oversized)
    }

    let malformed = rootURL.appendingPathComponent("\(UUID().uuidString.lowercased()).json")
    try Data(#"{"schema":1,"secret":"no"}"#.utf8).write(to: malformed)
    XCTAssertThrowsError(try AppGroupEnvelopeStore(rootURL: rootURL).loadPublished(at: malformed)) {
      XCTAssertEqual($0 as? ShareEnvelopeError, .invalid)
    }
  }

  func test_filename_and_identifier_must_match() throws {
    let store = AppGroupEnvelopeStore(rootURL: rootURL)
    let data = try store.encode(fixture())
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let wrongName = rootURL.appendingPathComponent("\(UUID().uuidString.lowercased()).json")
    try data.write(to: wrongName)

    XCTAssertThrowsError(try store.loadPublished(at: wrongName)) {
      XCTAssertEqual($0 as? ShareEnvelopeError, .invalid)
    }
  }

  func test_envelope_contains_no_identity_or_credential_fields() throws {
    let object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: AppGroupEnvelopeStore(rootURL: rootURL).encode(fixture()))
        as? [String: Any]
    )

    XCTAssertEqual(Set(object.keys), ["schema", "id", "capturedAt", "kind", "originalText", "url"])
    XCTAssertTrue(
      Set(object.keys).isDisjoint(with: ["origin", "accountId", "accessToken", "deviceSecret"]))
  }

  func test_publish_failure_does_not_complete_successfully() throws {
    try Data("not-a-directory".utf8).write(to: rootURL)
    let store = AppGroupEnvelopeStore(rootURL: rootURL)

    XCTAssertThrowsError(try store.publish(fixture()))
    XCTAssertFalse(
      FileManager.default.fileExists(atPath: rootURL.appendingPathExtension("json").path))
  }

  private func fixture() -> ShareEnvelope {
    ShareEnvelope(
      id: UUID(uuidString: "10dd7e57-9848-4af1-823b-9217df8058bb")!,
      capturedAt: Date(timeIntervalSince1970: 1_788_000_000.125),
      intake: .url(
        originalText: "Article https://example.com/read",
        url: URL(string: "https://example.com/read")!
      )
    )
  }
}
