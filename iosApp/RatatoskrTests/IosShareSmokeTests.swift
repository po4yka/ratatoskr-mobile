import Shared
import XCTest

final class IosShareSmokeTests: XCTestCase {
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
              url: ($0.request.payload as? CapturePayloadUrl)?.value
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
}
