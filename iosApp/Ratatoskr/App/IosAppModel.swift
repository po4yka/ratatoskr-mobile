import BackgroundTasks
import Foundation
import Shared
import UserNotifications

@MainActor
final class IosAppModel: ObservableObject {
  let controller: IosApplicationController
  private let scheduler: IosSubmissionScheduler
  private let fileScheduler: IosFileUploadScheduler
  private let eraser: IosLocalDataEraser
  private let statusFlow = IosSubmissionStatusFlow()
  private let inbox: AppGroupInbox?
  private let privateStagingURL: URL
  private var claim: ClaimedShareEnvelope?
  private var privateArtifactURL: URL?

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
    let fileSchedulerBox = FileSchedulerBox()
    let controllerBox = ControllerBox()
    let queuePath = applicationSupport.appendingPathComponent("capture-queue.sqlite").path
    let privateStagingURL = applicationSupport.appendingPathComponent(
      "ratatoskr-staging", isDirectory: true)
    let appGroupURL = FileManager.default.containerURL(
      forSecurityApplicationGroupIdentifier: "group.com.ratatoskr.mobile")
    let credentials = IosKeychainCredentialStorage(
      service: "com.ratatoskr.mobile.device-identity",
      account: "device-credentials",
      accessGroup: accessGroup)
    let eraser = IosLocalDataEraser(
      markerURL: applicationSupport.appendingPathComponent("ratatoskr-erasure.marker"),
      queuePath: queuePath,
      ownedRoots: [
        privateStagingURL, FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0],
      ]
        + (appGroupURL.map { root in
          [
            "ShareInbox", "ShareProcessing", "ShareRejected", "ShareArtifacts",
            "ShareProcessingArtifacts",
          ]
          .map { root.appendingPathComponent($0, isDirectory: true) }
        } ?? []),
      userDefaultsSuites: [Bundle.main.bundleIdentifier].compactMap { $0 },
      boundary: LiveIosEraseBoundary(
        cancelActive: {
          schedulerBox.scheduler?.cancelAll()
          fileSchedulerBox.scheduler?.cancelAll()
        }),
      clearCredentials: { try credentials.clear() },
      credentialsPresent: { try credentials.load() != nil },
      closeQueue: { controllerBox.controller?.closeQueueForLocalErasure() })
    precondition(
      eraser.resumeIfNeeded(), "Local data erasure must complete before application stores open")
    let controller = IosApplicationController(
      queuePath: queuePath,
      keychainAccessGroup: accessGroup,
      scheduleNativeWake: { wake in
        schedulerBox.scheduler?.schedule(nextWakeEpochMilliseconds: wake?.int64Value)
      },
      onProvenRevocation: { _ = eraser.begin(reason: "proven_remote_revocation") },
      localArtifactRoots: [privateStagingURL.path],
      eraseLocalData: { KotlinBoolean(bool: eraser.begin(reason: "confirmed_clear_data")) }
    )
    controllerBox.controller = controller
    self.controller = controller
    self.eraser = eraser
    self.privateStagingURL = privateStagingURL
    scheduler = IosSubmissionScheduler(
      boundary: LiveIosBackgroundTaskBoundary(),
      store: UserDefaultsWakeStateStore(),
      drain: { try await Self.reconcile(controller) }
    )
    schedulerBox.scheduler = scheduler
    fileScheduler = IosFileUploadScheduler(
      boundary: LiveIosProcessingTaskBoundary(),
      lowPowerMode: { ProcessInfo.processInfo.isLowPowerModeEnabled },
      drain: { throw FileTransferIntegrationPending() })
    fileSchedulerBox.scheduler = fileScheduler
    inbox = appGroupURL.map(AppGroupInbox.init)
    scheduler.start()
    fileScheduler.start()
    controller.start()
  }

  func setSceneActive(_ active: Bool) {
    statusFlow.setSceneActive(active)
    controller.setSceneActive(active: active)
    guard active else { return }
    scheduler.sceneActivated()
    fileScheduler.sceneActivated()
    Task { await importNextHandoff() }
  }

  func openLibraryURL(_ url: URL) {
    _ = controller.acceptLibraryLink(value: url.absoluteString)
  }

  private func importNextHandoff() async {
    guard claim == nil, let inbox else { return }
    do {
      guard let next = try await inbox.claimNext() else { return }
      claim = next
      if next.envelope.url != nil { statusFlow.importedURL() }
      if let file = next.envelope.file {
        let imported = try AppGroupPrivateArtifactImporter(privateRootURL: privateStagingURL)
          .import(next)
        privateArtifactURL = imported.privateURL
        controller.presentFileShare(
          handoffId: next.envelope.id.uuidString.lowercased(),
          stagedFileId: file.artifactID.uuidString.lowercased(),
          displayName: file.displayName,
          mediaType: file.mediaType,
          byteSize: file.sizeBytes,
          sha256Hex: file.sha256Hex,
          capturedAtEpochMilliseconds: Int64(
            next.envelope.capturedAt.timeIntervalSince1970 * 1_000),
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
          })
        return
      }
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
      if let claim { await inbox.retain(claim) }
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
        if let privateArtifactURL { try? FileManager.default.removeItem(at: privateArtifactURL) }
      }
      privateArtifactURL = nil
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

private struct FileTransferIntegrationPending: Error {}

private final class ControllerBox: @unchecked Sendable {
  weak var controller: IosApplicationController?
}

private final class FileSchedulerBox: @unchecked Sendable {
  private let lock = NSLock()
  private var stored: IosFileUploadScheduler?

  var scheduler: IosFileUploadScheduler? {
    get { lock.withLock { stored } }
    set { lock.withLock { stored = newValue } }
  }
}

private final class LiveIosEraseBoundary: IosEraseBoundary {
  private let cancelActive: () -> Void

  init(cancelActive: @escaping () -> Void) { self.cancelActive = cancelActive }

  func cancelBackgroundAndNotifications() {
    cancelActive()
    BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: IosSubmissionScheduler.identifier)
    BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: IosFileUploadScheduler.identifier)
    UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    UNUserNotificationCenter.current().removeAllDeliveredNotifications()
  }

  func residueCount() -> Int { 0 }
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
