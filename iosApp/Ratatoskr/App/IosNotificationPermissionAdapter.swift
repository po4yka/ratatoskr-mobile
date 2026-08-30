import Foundation
import UIKit
import UserNotifications

enum IosNotificationPermissionState: Equatable {
  case integrationPending
  case notDetermined
  case granted
  case denied
}

@MainActor
protocol IosNotificationCenterBoundary: AnyObject {
  func requestAuthorization() async -> Bool
  func openSettings()
  func clearNotifications()
}

@MainActor
final class IosNotificationPermissionAdapter {
  private let boundary: IosNotificationCenterBoundary
  private var permission: IosNotificationPermissionState
  private var settingsOpened = false

  init(
    permission: IosNotificationPermissionState,
    boundary: IosNotificationCenterBoundary
  ) {
    self.permission = permission
    self.boundary = boundary
  }

  func enable(contractAvailable: Bool) async -> IosNotificationPermissionState {
    guard contractAvailable else { return .integrationPending }
    switch permission {
    case .notDetermined:
      permission = await boundary.requestAuthorization() ? .granted : .denied
    case .denied:
      if !settingsOpened {
        settingsOpened = true
        boundary.openSettings()
      }
    case .granted, .integrationPending:
      break
    }
    return permission
  }

  func revoke() {
    settingsOpened = false
    permission = .notDetermined
    boundary.clearNotifications()
  }
}

@MainActor
final class SystemIosNotificationCenterBoundary: IosNotificationCenterBoundary {
  private let center = UNUserNotificationCenter.current()

  func requestAuthorization() async -> Bool {
    (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
  }

  func openSettings() {
    guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
    UIApplication.shared.open(url)
  }

  func clearNotifications() {
    center.removeAllPendingNotificationRequests()
    center.removeAllDeliveredNotifications()
  }
}
