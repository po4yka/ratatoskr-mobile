import Shared
import XCTest

final class IosShareSmokeTests: XCTestCase {
  func testShellWiresUniversalLinksNotificationTruthRussianAndPrivateCanaryAbsence() throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: root) }
    let controller = IosApplicationController(
      queuePath: root.appendingPathComponent("queue.sqlite").path,
      keychainAccessGroup: "",
      scheduleNativeWake: { _ in }
    )
    defer { controller.close() }
    controller.configureContentLinkHost(host: "links.ratatoskr.test")

    XCTAssertTrue(
      controller.acceptLibraryLink(
        value:
          "https://links.ratatoskr.test/analyses/abcdef01-0000-4000-8000-000000000001")
    )
    XCTAssertEqual(controller.pendingLibraryRouteId(), "abcdef01-0000-4000-8000-000000000001")
    XCTAssertTrue(
      controller.acceptLibraryLink(value: "https://links.ratatoskr.test/collections/inbox")
    )
    XCTAssertEqual(controller.pendingLibraryRouteId(), "inbox")
    XCTAssertTrue(
      controller.acceptLibraryLink(value: "https://links.ratatoskr.test/repos/ratatoskr/mobile")
    )
    XCTAssertEqual(controller.pendingLibraryRouteId(), "ratatoskr/mobile")
    controller.notificationStore.updatePaired(value: true)
    let notificationState = try XCTUnwrap(
      controller.notificationStore.state.value as? CompletionNotificationState)
    XCTAssertEqual(
      notificationState.effective,
      CompletionNotificationEffectiveState.integrationpending)
    XCTAssertEqual(
      MobileStrings.shared.value(key: .searchtitle, locale: .russian),
      "Поиск")
    let publicText = MobileStrings.shared.value(
      key: .notificationsintegrationpending, locale: .english)
    for canary in ["private-search", "private-note", "private-user@example.test"] {
      XCTAssertFalse(publicText.contains(canary))
    }
  }

  func testSyntheticShareSurvivesExtensionGraphTerminationAndQueueReopenThenRendersTerminalStatus()
    async throws
  {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: root) }
    let handoffID = UUID()
    let capturedAt = Date(timeIntervalSince1970: 1_788_000_000)
    let parsed = await ShareExtensionParser().parse(
      loaders: [SmokeLoader(value: "Read https://example.test/ios-share")]
    )
    guard case .success(let intake) = parsed else { return XCTFail("synthetic parser failed") }
    let envelope = ShareEnvelope(id: handoffID, capturedAt: capturedAt, intake: intake)
    _ = try AppGroupEnvelopeStore(
      rootURL: root.appendingPathComponent("ShareInbox")
    ).publish(envelope)

    let inbox = AppGroupInbox(containerURL: root)
    let claim = try await inbox.claimNext()
    XCTAssertEqual(claim?.envelope.id, handoffID)
    guard let claim else { return }
    let queuePath = root.appendingPathComponent("capture-queue.sqlite").path
    let firstQueue = IosApplicationGraphKt.createIosCaptureQueue(path: queuePath)
    let request = CaptureRequest(
      owner: CaptureOwner(origin: "https://platform.example", accountId: "user-1"),
      source: CaptureSource.iosshareextension,
      payload: CapturePayloadUrl(value: try XCTUnwrap(claim.envelope.url?.absoluteString)),
      createdAt: KotlinInstant.companion.fromEpochMilliseconds(
        epochMilliseconds: Int64(capturedAt.timeIntervalSince1970 * 1_000)
      )
    )
    let key = "ios-share-\(handoffID.uuidString.lowercased())"
    let stored = try await enqueue(firstQueue, request: request, key: key)
    firstQueue.close()

    let reopened = IosApplicationGraphKt.createIosCaptureQueue(path: queuePath)
    let recovered = try await inspect(reopened, localID: stored)
    XCTAssertEqual(recovered?.idempotencyKey, key)
    XCTAssertEqual(recovered?.url, claim.envelope.url?.absoluteString)
    reopened.close()

    let status = IosSubmissionStatusFlow()
    status.accepted(operationId: "1518c249-a3d3-4a9b-954a-5a110a3f9dcb")
    status.applyFixture(status: "running", changedAt: capturedAt)
    status.applyFixture(status: "succeeded", changedAt: capturedAt.addingTimeInterval(1))
    XCTAssertEqual(status.renderedStatus, "succeeded")
    try await inbox.complete(claim)
  }

  func testSyntheticFileHandoffStagesPrivatelyAndSurvivesQueueReopen() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: root) }
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    let source = root.appendingPathComponent("synthetic.pdf")
    let bytes = Data("%PDF-1.7\nsynthetic smoke".utf8)
    try bytes.write(to: source)
    let artifactID = UUID()
    let descriptor = try AppGroupArtifactStore(
      rootURL: root.appendingPathComponent("ShareArtifacts", isDirectory: true)
    ).stage(
      ShareFileCandidate(
        sourceURL: source, mediaType: "application/pdf", displayName: "synthetic.pdf",
        sizeBytes: Int64(bytes.count)),
      artifactID: artifactID)
    let handoffID = UUID()
    _ = try AppGroupEnvelopeStore(
      rootURL: root.appendingPathComponent("ShareInbox", isDirectory: true)
    ).publish(ShareEnvelope(id: handoffID, capturedAt: Date(), file: descriptor))

    let inbox = AppGroupInbox(containerURL: root)
    let possibleClaim = try await inbox.claimNext()
    let claim = try XCTUnwrap(possibleClaim)
    let imported = try AppGroupPrivateArtifactImporter(
      privateRootURL: root.appendingPathComponent("PrivateStaging", isDirectory: true)
    ).import(claim)
    XCTAssertEqual(imported.descriptor, descriptor)

    let queuePath = root.appendingPathComponent("capture-queue.sqlite").path
    let queue = IosApplicationGraphKt.createIosCaptureQueue(path: queuePath)
    let stored = try await enqueue(
      queue,
      request: CaptureRequest(
        owner: CaptureOwner(origin: "https://platform.example", accountId: "user-1"),
        source: CaptureSource.iosshareextension,
        payload: CapturePayloadFileReference(
          stagedFileId: descriptor.artifactID.uuidString.lowercased(),
          displayName: descriptor.displayName,
          mediaType: descriptor.mediaType,
          byteSize: descriptor.sizeBytes),
        createdAt: KotlinInstant.companion.fromEpochMilliseconds(
          epochMilliseconds: 1_788_000_000_000)
      ),
      key: "ios-share-\(handoffID.uuidString.lowercased())")
    queue.close()

    let reopened = IosApplicationGraphKt.createIosCaptureQueue(path: queuePath)
    let recovered = try await inspect(reopened, localID: stored)
    XCTAssertEqual(recovered?.stagedFileID, descriptor.artifactID.uuidString.lowercased())
    XCTAssertTrue(FileManager.default.fileExists(atPath: imported.privateURL.path))
    reopened.close()
    try await inbox.complete(claim)
  }

  private func enqueue(
    _ queue: CaptureQueue,
    request: CaptureRequest,
    key: String
  ) async throws -> String {
    try await withCheckedThrowingContinuation { continuation in
      queue.enqueue(request: request, idempotencyKey: key) { result, error in
        if let error { return continuation.resume(throwing: error) }
        guard let stored = (result as? QueueResultSuccess<QueueRecord>)?.value else {
          return continuation.resume(throwing: SmokeError.queueRejected)
        }
        continuation.resume(returning: stored.localId)
      }
    }
  }

  private func inspect(_ queue: CaptureQueue, localID: String) async throws -> SmokeRecovered? {
    try await withCheckedThrowingContinuation { continuation in
      queue.inspect(localId: localID) { record, error in
        if let error { return continuation.resume(throwing: error) }
        continuation.resume(
          returning: record.map {
            SmokeRecovered(
              idempotencyKey: $0.idempotencyKey,
              url: ($0.request.payload as? CapturePayloadUrl)?.value,
              stagedFileID: ($0.request.payload as? CapturePayloadFileReference)?.stagedFileId
            )
          }
        )
      }
    }
  }
}

private struct SmokeLoader: ShareItemLoading {
  let value: String
  func loadRepresentations() async throws -> [ShareRepresentation] {
    [ShareRepresentation(kind: .plainText, value: value)]
  }
}

private enum SmokeError: Error { case queueRejected }

private struct SmokeRecovered: Sendable {
  let idempotencyKey: String
  let url: String?
  let stagedFileID: String?
}
