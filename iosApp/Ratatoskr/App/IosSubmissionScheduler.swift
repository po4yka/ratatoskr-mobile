import BackgroundTasks
import Foundation

protocol IosBackgroundExecution: AnyObject, Sendable {
  var expirationHandler: (() -> Void)? { get set }
  func complete(success: Bool)
}

final class LiveIosBackgroundTaskBoundary: IosBackgroundTaskBoundary {
  func register(identifier: String, launch: @escaping (IosBackgroundExecution) -> Void) {
    BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
      guard let refresh = task as? BGAppRefreshTask else {
        task.setTaskCompleted(success: false)
        return
      }
      launch(LiveIosBackgroundExecution(task: refresh))
    }
  }

  func submit(identifier: String, earliest: Date?) throws {
    let request = BGAppRefreshTaskRequest(identifier: identifier)
    request.earliestBeginDate = earliest
    try BGTaskScheduler.shared.submit(request)
  }

  func cancel(identifier: String) {
    BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
  }
}

private final class LiveIosBackgroundExecution: IosBackgroundExecution, @unchecked Sendable {
  private let task: BGAppRefreshTask

  init(task: BGAppRefreshTask) { self.task = task }

  var expirationHandler: (() -> Void)? {
    get { task.expirationHandler }
    set { task.expirationHandler = newValue }
  }

  func complete(success: Bool) { task.setTaskCompleted(success: success) }
}

final class UserDefaultsWakeStateStore: IosWakeStateStore {
  private let defaults: UserDefaults
  private let key: String

  init(defaults: UserDefaults = .standard, key: String = "iosSubmissionNextWake") {
    self.defaults = defaults
    self.key = key
  }

  var nextWake: Date? {
    get { defaults.object(forKey: key) as? Date }
    set { defaults.set(newValue, forKey: key) }
  }
}

protocol IosBackgroundTaskBoundary: AnyObject {
  func register(identifier: String, launch: @escaping (IosBackgroundExecution) -> Void)
  func submit(identifier: String, earliest: Date?) throws
  func cancel(identifier: String)
}

protocol IosWakeStateStore: AnyObject {
  var nextWake: Date? { get set }
}

final class IosSubmissionScheduler: @unchecked Sendable {
  static let identifier = "com.ratatoskr.mobile.submission.refresh"

  init(
    boundary: IosBackgroundTaskBoundary,
    store: IosWakeStateStore,
    now: @escaping () -> Date = Date.init,
    drain: @escaping () async throws -> Int64?
  ) {
    self.boundary = boundary
    self.store = store
    self.now = now
    self.drain = drain
  }

  func start() {
    let shouldRegister = lock.withLock {
      guard !started else { return false }
      started = true
      return true
    }
    guard shouldRegister else { return }
    boundary.register(identifier: Self.identifier) { [weak self] execution in
      self?.handle(execution)
    }
  }

  func schedule(nextWakeEpochMilliseconds: Int64?) {
    lock.withLock {
      scheduleLocked(nextWakeEpochMilliseconds: nextWakeEpochMilliseconds)
    }
  }

  private func scheduleLocked(nextWakeEpochMilliseconds: Int64?) {
    guard let nextWakeEpochMilliseconds else {
      store.nextWake = nil
      boundary.cancel(identifier: Self.identifier)
      return
    }
    let requested = Date(timeIntervalSince1970: Double(nextWakeEpochMilliseconds) / 1_000)
    if let existing = store.nextWake, existing <= requested { return }
    boundary.cancel(identifier: Self.identifier)
    do {
      try boundary.submit(identifier: Self.identifier, earliest: requested)
      store.nextWake = requested
    } catch {
      store.nextWake = requested
    }
  }

  func sceneActivated() {
    lock.withLock {
      guard let persisted = store.nextWake else { return }
      try? boundary.submit(identifier: Self.identifier, earliest: persisted)
    }
  }

  private let boundary: IosBackgroundTaskBoundary
  private let store: IosWakeStateStore
  private let now: () -> Date
  private let drain: () async throws -> Int64?
  private let lock = NSLock()
  private var started = false

  private func handle(_ execution: IosBackgroundExecution) {
    let earlyWake = lock.withLock { store.nextWake }.flatMap { persisted in
      persisted > now() ? persisted : nil
    }
    if let earlyWake {
      try? boundary.submit(identifier: Self.identifier, earliest: earlyWake)
      execution.complete(success: true)
      return
    }
    let task = Task { [weak self] in
      guard let self else {
        execution.complete(success: false)
        return
      }
      do {
        let next = try await drain()
        schedule(nextWakeEpochMilliseconds: next)
        execution.complete(success: true)
      } catch {
        execution.complete(success: false)
      }
    }
    execution.expirationHandler = { task.cancel() }
  }
}
