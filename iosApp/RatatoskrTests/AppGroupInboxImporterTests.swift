import Foundation
import Shared
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

  func testFileHandoffConvergesAcrossMainAppRestart() async throws {
    try publishFile(id: id(20))
    let first = AppGroupInbox(containerURL: containerURL)
    let possibleOriginal = try await first.claimNext()
    let original = try XCTUnwrap(possibleOriginal)

    await first.retain(original)
    let possibleRecovered = try await AppGroupInbox(containerURL: containerURL).claimNext()
    let recovered = try XCTUnwrap(possibleRecovered)

    XCTAssertEqual(recovered.envelope, original.envelope)
    XCTAssertEqual(recovered.artifactURL, original.artifactURL)
    XCTAssertNotNil(recovered.artifactURL)
    XCTAssertTrue(FileManager.default.fileExists(atPath: try XCTUnwrap(recovered.artifactURL).path))
  }

  func testConfirmedFileMovesToPrivateStagingBeforeQueueCommit() async throws {
    try publishFile(id: id(21))
    let possibleClaim = try await AppGroupInbox(containerURL: containerURL).claimNext()
    let claim = try XCTUnwrap(possibleClaim)
    let privateRoot = containerURL.appendingPathComponent("PrivateStaging", isDirectory: true)

    let imported = try AppGroupPrivateArtifactImporter(privateRootURL: privateRoot).import(claim)

    XCTAssertEqual(imported.descriptor, claim.envelope.file)
    XCTAssertTrue(FileManager.default.fileExists(atPath: imported.privateURL.path))
    XCTAssertTrue(FileManager.default.fileExists(atPath: try XCTUnwrap(claim.artifactURL).path))
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

  @discardableResult
  private func publishFile(id: UUID) throws -> URL {
    let source = containerURL.appendingPathComponent("source-\(id.uuidString).pdf")
    try FileManager.default.createDirectory(at: containerURL, withIntermediateDirectories: true)
    let bytes = Data("%PDF-1.7\nsynthetic".utf8)
    try bytes.write(to: source)
    let descriptor = try AppGroupArtifactStore(
      rootURL: containerURL.appendingPathComponent("ShareArtifacts", isDirectory: true)
    ).stage(
      ShareFileCandidate(
        sourceURL: source, mediaType: "application/pdf", displayName: "synthetic.pdf",
        sizeBytes: Int64(bytes.count)),
      artifactID: id)
    return try AppGroupEnvelopeStore(
      rootURL: containerURL.appendingPathComponent("ShareInbox", isDirectory: true)
    ).publish(ShareEnvelope(id: id, capturedAt: Date(), file: descriptor))
  }

  private func id(_ value: Int) -> UUID {
    UUID(uuidString: String(format: "00000000-0000-4000-8000-%012d", value))!
  }
}

final class IosLocalDataErasureTests: XCTestCase {
  private var root: URL!
  private var storage: IosKeychainCredentialStorage!
  private var defaultsSuite: String!
  private let boundary = EraseBoundaryFake()

  override func setUpWithError() throws {
    root = FileManager.default.temporaryDirectory
      .appendingPathComponent("ratatoskr-erasure-\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    storage = IosKeychainCredentialStorage(
      service: "com.ratatoskr.mobile.tests.erasure.\(UUID().uuidString)",
      account: "device-credentials",
      accessGroup: nil)
    defaultsSuite = "com.ratatoskr.mobile.tests.erasure.\(UUID().uuidString)"
  }

  override func tearDownWithError() throws {
    try? storage.clear()
    UserDefaults.standard.removePersistentDomain(forName: defaultsSuite)
    try? FileManager.default.removeItem(at: root)
  }

  func testCompleteWipeRemovesEveryRegisteredIosStore() throws {
    try seed()
    let eraser = makeEraser()

    XCTAssertTrue(eraser.begin(reason: "confirmed_clear_data"))

    XCTAssertTrue(eraser.inventory().isEmpty)
    XCTAssertFalse(eraser.markerExists())
  }

  func testInterruptedMarkerFinishesBeforeStoreReopen() throws {
    try seed()
    try Data("00000000-0000-4000-8000-000000000001:proven_remote_revocation".utf8).write(
      to: root.appendingPathComponent("ratatoskr-erasure.marker"))

    let eraser = makeEraser()

    XCTAssertTrue(eraser.resumeIfNeeded())
    XCTAssertTrue(eraser.inventory().isEmpty)
    XCTAssertFalse(eraser.markerExists())
  }

  func testMalformedMarkerFailsClosedWithoutDeletingStores() throws {
    try seed()
    try Data("malformed".utf8).write(
      to: root.appendingPathComponent("ratatoskr-erasure.marker"))

    let eraser = makeEraser()

    XCTAssertFalse(eraser.resumeIfNeeded())
    XCTAssertTrue(eraser.markerExists())
    XCTAssertFalse(eraser.inventory().isEmpty)
  }

  private func seed() throws {
    try storage.save(
      credentials: DeviceCredentials(
        origin: "https://platform.example", userId: "user-1", deviceId: "device-1",
        deviceSecret: "device-secret", accessToken: "access",
        accessExpiresAt: "2026-09-01T00:00:00Z",
        refreshToken: "refresh", refreshExpiresAt: "2026-10-01T00:00:00Z", refreshTokenUsable: true)
    )
    let queue = root.appendingPathComponent("capture-queue.sqlite")
    try Data([1]).write(to: queue)
    try Data([2]).write(to: URL(fileURLWithPath: queue.path + "-wal"))
    try Data([3]).write(to: URL(fileURLWithPath: queue.path + "-shm"))
    for name in [
      "ratatoskr-staging", "ShareInbox", "ShareProcessing", "ShareRejected", "ShareArtifacts",
      "ShareProcessingArtifacts", "Caches",
    ] {
      let directory = root.appendingPathComponent(name, isDirectory: true)
      try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
      try Data([4]).write(to: directory.appendingPathComponent("residue"))
    }
    UserDefaults(suiteName: defaultsSuite)?.set("seeded", forKey: "state")
    boundary.residue = 2
  }

  private func makeEraser() -> IosLocalDataEraser {
    let queue = root.appendingPathComponent("capture-queue.sqlite")
    let roots = [
      "ratatoskr-staging", "ShareInbox", "ShareProcessing", "ShareRejected", "ShareArtifacts",
      "ShareProcessingArtifacts", "Caches",
    ]
    .map { root.appendingPathComponent($0, isDirectory: true) }
    return IosLocalDataEraser(
      markerURL: root.appendingPathComponent("ratatoskr-erasure.marker"),
      queuePath: queue.path,
      ownedRoots: roots,
      userDefaultsSuites: [defaultsSuite],
      boundary: boundary,
      clearCredentials: { try self.storage.clear() },
      credentialsPresent: { try self.storage.load() != nil },
      closeQueue: {})
  }
}

private final class EraseBoundaryFake: IosEraseBoundary {
  var residue = 0
  func cancelBackgroundAndNotifications() { residue = 0 }
  func residueCount() -> Int { residue }
}
