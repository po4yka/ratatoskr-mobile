import Foundation

enum IosShellPresentation: Equatable {
  case idle
  case confirmationRequired
  case queuedOffline
  case operationDetail(String)
  case reauthenticationRequired
}

final class IosSubmissionStatusFlow {
  private(set) var presentation: IosShellPresentation = .idle
  private(set) var pollingVisible = true
  private(set) var renderedStatus: String?
  private var changedAt: Date?

  func importedURL() { presentation = .confirmationRequired }
  func confirmedOffline() { presentation = .queuedOffline }
  func accepted(operationId: String) { presentation = .operationDetail(operationId) }
  func setSceneActive(_ active: Bool) { pollingVisible = active }
  func requireReauthentication() { presentation = .reauthenticationRequired }

  func applyFixture(status: String, changedAt: Date) {
    let supported = ["running", "partially_succeeded", "failed", "cancelled", "succeeded"]
    guard supported.contains(status) else { return }
    if let current = self.changedAt, changedAt <= current { return }
    if let renderedStatus, Self.terminal.contains(renderedStatus) { return }
    self.changedAt = changedAt
    renderedStatus = status
  }

  private static let terminal = ["partially_succeeded", "failed", "cancelled", "succeeded"]
}
