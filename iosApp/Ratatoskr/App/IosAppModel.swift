import Foundation
import Shared

@MainActor
final class IosAppModel: ObservableObject {
  let controller: IosApplicationController
  private let scheduler: IosSubmissionScheduler
  private let statusFlow = IosSubmissionStatusFlow()
  private let inbox: AppGroupInbox?
  private var claim: ClaimedShareEnvelope?

  init() {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    try? FileManager.default.createDirectory(
      at: applicationSupport,
      withIntermediateDirectories: true,
      attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
    )
    let accessGroup =
      Bundle.main.object(forInfoDictionaryKey: "RatatoskrKeychainAccessGroup") as? String ?? ""
    let schedulerBox = SchedulerBox()
    let controller = IosApplicationController(
      queuePath: applicationSupport.appendingPathComponent("capture-queue.sqlite").path,
      keychainAccessGroup: accessGroup,
      scheduleNativeWake: { wake in
        schedulerBox.scheduler?.schedule(nextWakeEpochMilliseconds: wake?.int64Value)
      }
    )
    self.controller = controller
    scheduler = IosSubmissionScheduler(
      boundary: LiveIosBackgroundTaskBoundary(),
      store: UserDefaultsWakeStateStore(),
      drain: { try await Self.reconcile(controller) }
    )
    schedulerBox.scheduler = scheduler
    inbox = FileManager.default.containerURL(
      forSecurityApplicationGroupIdentifier: "group.com.ratatoskr.mobile"
    ).map(AppGroupInbox.init)
    scheduler.start()
    controller.start()
  }

  func setSceneActive(_ active: Bool) {
    statusFlow.setSceneActive(active)
    controller.setSceneActive(active: active)
    guard active else { return }
    scheduler.sceneActivated()
    Task { await importNextHandoff() }
  }

  private func importNextHandoff() async {
    guard claim == nil, let inbox else { return }
    do {
      guard let next = try await inbox.claimNext() else { return }
      claim = next
      if next.envelope.url != nil { statusFlow.importedURL() }
      controller.presentShare(
        handoffId: next.envelope.id.uuidString.lowercased(),
        originalText: next.envelope.originalText,
        url: next.envelope.url?.absoluteString,
        capturedAtEpochMilliseconds: Int64(next.envelope.capturedAt.timeIntervalSince1970 * 1_000),
        onCommitted: { [weak self] in
          Task { @MainActor in
            self?.statusFlow.confirmedOffline()
            await self?.finishClaim(committed: true)
          }
        },
        onCancelled: { [weak self] in
          Task { @MainActor in await self?.finishClaim(committed: false) }
        },
        onFailure: { [weak self] in
          Task { @MainActor in await self?.retainClaim() }
        }
      )
    } catch {
      claim = nil
    }
  }

  private func finishClaim(committed: Bool) async {
    guard let claim, let inbox else { return }
    do {
      if committed {
        try await inbox.complete(claim)
      } else {
        try await inbox.cancel(claim)
      }
      self.claim = nil
      await importNextHandoff()
    } catch {
      await retainClaim()
    }
  }

  private func retainClaim() async {
    guard let claim, let inbox else { return }
    await inbox.retain(claim)
    self.claim = nil
  }

  private static func reconcile(_ controller: IosApplicationController) async throws -> Int64? {
    let handleBox = ReconcileHandleBox()
    return try await withTaskCancellationHandler {
      try await withCheckedThrowingContinuation { continuation in
        handleBox.handle = controller.beginReconcile(
          onComplete: { wake in continuation.resume(returning: wake?.int64Value) },
          onFailure: { continuation.resume(throwing: CancellationError()) }
        )
      }
    } onCancel: {
      handleBox.cancel()
    }
  }
}

private final class SchedulerBox: @unchecked Sendable {
  private let lock = NSLock()
  private var stored: IosSubmissionScheduler?

  var scheduler: IosSubmissionScheduler? {
    get { lock.withLock { stored } }
    set { lock.withLock { stored = newValue } }
  }
}

private final class ReconcileHandleBox: @unchecked Sendable {
  private let lock = NSLock()
  private var stored: IosReconcileHandle?

  var handle: IosReconcileHandle? {
    get { lock.withLock { stored } }
    set { lock.withLock { stored = newValue } }
  }

  func cancel() { handle?.cancel() }
}
