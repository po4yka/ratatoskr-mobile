import Foundation

struct ClaimedShareEnvelope: Equatable, Sendable {
  let envelope: ShareEnvelope
  let fileURL: URL
  let artifactURL: URL?

  init(envelope: ShareEnvelope, fileURL: URL, artifactURL: URL? = nil) {
    self.envelope = envelope
    self.fileURL = fileURL
    self.artifactURL = artifactURL
  }
}

struct ImportedPrivateArtifact: Equatable, Sendable {
  let descriptor: ShareFileDescriptor
  let privateURL: URL
}

struct AppGroupPrivateArtifactImporter: Sendable {
  let privateRootURL: URL

  func `import`(_ claim: ClaimedShareEnvelope) throws -> ImportedPrivateArtifact {
    guard let expected = claim.envelope.file, let sourceURL = claim.artifactURL else {
      throw ShareEnvelopeError.invalid
    }
    let store = AppGroupArtifactStore(rootURL: privateRootURL)
    if store.verifyPublished(expected) {
      return ImportedPrivateArtifact(
        descriptor: expected,
        privateURL: store.publishedArtifactURL(id: expected.artifactID))
    }
    let staged = try store.stage(
      ShareFileCandidate(
        sourceURL: sourceURL,
        mediaType: expected.mediaType,
        displayName: expected.displayName,
        sizeBytes: expected.sizeBytes),
      artifactID: expected.artifactID)
    guard staged == expected else { throw ShareEnvelopeError.invalid }
    return ImportedPrivateArtifact(
      descriptor: staged,
      privateURL: store.publishedArtifactURL(id: staged.artifactID))
  }
}

actor AppGroupInbox {
  let containerURL: URL
  private var activeID: UUID?

  init(containerURL: URL) {
    self.containerURL = containerURL
  }

  func claimNext() throws -> ClaimedShareEnvelope? {
    guard activeID == nil else { return nil }
    try createDirectories()

    if let recovered = try firstValidProcessingClaim() {
      activeID = recovered.envelope.id
      return recovered
    }

    let inboxStore = AppGroupEnvelopeStore(rootURL: inboxURL)
    for published in inboxStore.publishedURLs() {
      let destination = processingURL.appendingPathComponent(published.lastPathComponent)
      do {
        try FileManager.default.moveItem(at: published, to: destination)
      } catch {
        continue
      }
      do {
        let envelope = try AppGroupEnvelopeStore(rootURL: processingURL).loadPublished(
          at: destination)
        let claim = try claim(envelope: envelope, fileURL: destination)
        activeID = envelope.id
        return claim
      } catch {
        try reject(destination)
      }
    }
    return nil
  }

  func complete(_ claim: ClaimedShareEnvelope) throws {
    try remove(claim)
  }

  func cancel(_ claim: ClaimedShareEnvelope) throws {
    try remove(claim)
  }

  func retain(_ claim: ClaimedShareEnvelope) {
    if activeID == claim.envelope.id {
      activeID = nil
    }
  }

  private var inboxURL: URL {
    containerURL.appendingPathComponent("ShareInbox", isDirectory: true)
  }

  private var processingURL: URL {
    containerURL.appendingPathComponent("ShareProcessing", isDirectory: true)
  }

  private var rejectedURL: URL {
    containerURL.appendingPathComponent("ShareRejected", isDirectory: true)
  }

  private var artifactsURL: URL {
    containerURL.appendingPathComponent("ShareArtifacts", isDirectory: true)
  }

  private var processingArtifactsURL: URL {
    containerURL.appendingPathComponent("ShareProcessingArtifacts", isDirectory: true)
  }

  private func createDirectories() throws {
    do {
      for url in [inboxURL, processingURL, rejectedURL, artifactsURL, processingArtifactsURL] {
        try FileManager.default.createDirectory(
          at: url,
          withIntermediateDirectories: true,
          attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
        )
      }
    } catch {
      throw ShareEnvelopeError.unavailable
    }
  }

  private func firstValidProcessingClaim() throws -> ClaimedShareEnvelope? {
    let store = AppGroupEnvelopeStore(rootURL: processingURL)
    for url in store.publishedURLs() {
      do {
        return try claim(envelope: store.loadPublished(at: url), fileURL: url)
      } catch {
        try reject(url)
      }
    }
    return nil
  }

  private func remove(_ claim: ClaimedShareEnvelope) throws {
    guard
      activeID == claim.envelope.id,
      claim.fileURL.standardizedFileURL.deletingLastPathComponent()
        == processingURL.standardizedFileURL,
      claim.fileURL.lastPathComponent == "\(claim.envelope.id.uuidString.lowercased()).json"
    else { throw ShareEnvelopeError.invalid }
    do {
      try FileManager.default.removeItem(at: claim.fileURL)
      if let artifactURL = claim.artifactURL,
        FileManager.default.fileExists(atPath: artifactURL.path)
      {
        try FileManager.default.removeItem(at: artifactURL)
      }
      activeID = nil
    } catch {
      throw ShareEnvelopeError.unavailable
    }
  }

  private func claim(envelope: ShareEnvelope, fileURL: URL) throws -> ClaimedShareEnvelope {
    guard let descriptor = envelope.file else {
      return ClaimedShareEnvelope(envelope: envelope, fileURL: fileURL)
    }
    let destination = processingArtifactsURL.appendingPathComponent(
      descriptor.artifactID.uuidString.lowercased(), isDirectory: false)
    if !FileManager.default.fileExists(atPath: destination.path) {
      let source = artifactsURL.appendingPathComponent(
        descriptor.artifactID.uuidString.lowercased(), isDirectory: false)
      guard FileManager.default.fileExists(atPath: source.path) else {
        throw ShareEnvelopeError.invalid
      }
      try FileManager.default.moveItem(at: source, to: destination)
    }
    let values = try destination.resourceValues(
      forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
    guard
      values.isRegularFile == true, values.isSymbolicLink != true,
      Int64(values.fileSize ?? -1) == descriptor.sizeBytes
    else { throw ShareEnvelopeError.invalid }
    return ClaimedShareEnvelope(envelope: envelope, fileURL: fileURL, artifactURL: destination)
  }

  private func reject(_ url: URL) throws {
    guard url.standardizedFileURL.deletingLastPathComponent() == processingURL.standardizedFileURL
    else { throw ShareEnvelopeError.invalid }
    let destination = rejectedURL.appendingPathComponent("\(UUID().uuidString.lowercased()).json")
    do {
      try FileManager.default.moveItem(at: url, to: destination)
      try pruneRejectedFiles()
    } catch {
      throw ShareEnvelopeError.unavailable
    }
  }

  private func pruneRejectedFiles() throws {
    let keys: Set<URLResourceKey> = [
      .contentModificationDateKey, .isRegularFileKey, .isSymbolicLinkKey,
    ]
    let files = try FileManager.default.contentsOfDirectory(
      at: rejectedURL,
      includingPropertiesForKeys: Array(keys),
      options: [.skipsHiddenFiles]
    ).filter { url in
      guard
        url.pathExtension == "json",
        let values = try? url.resourceValues(forKeys: keys)
      else { return false }
      return values.isRegularFile == true && values.isSymbolicLink != true
    }.sorted { first, second in
      let firstDate = try? first.resourceValues(forKeys: [.contentModificationDateKey])
        .contentModificationDate
      let secondDate = try? second.resourceValues(forKeys: [.contentModificationDateKey])
        .contentModificationDate
      if firstDate == secondDate { return first.lastPathComponent < second.lastPathComponent }
      return (firstDate ?? .distantPast) < (secondDate ?? .distantPast)
    }
    for file in files.dropLast(Self.maximumRejectedFiles) {
      try FileManager.default.removeItem(at: file)
    }
  }

  private static let maximumRejectedFiles = 32
}

protocol IosEraseBoundary: AnyObject {
  func cancelBackgroundAndNotifications()
  func residueCount() -> Int
}

struct IosEraseInventory: Equatable {
  let credentials: Bool
  let databaseFiles: Int
  let ownedFiles: Int
  let preferenceEntries: Int
  let scheduledOrNotified: Int

  var isEmpty: Bool {
    !credentials && databaseFiles + ownedFiles + preferenceEntries + scheduledOrNotified == 0
  }
}

final class IosLocalDataEraser {
  private let markerURL: URL
  private let queuePath: String
  private let ownedRoots: [URL]
  private let userDefaultsSuites: [String]
  private let boundary: IosEraseBoundary
  private let clearCredentials: () throws -> Void
  private let credentialsPresent: () throws -> Bool
  private let closeQueue: () -> Void

  init(
    markerURL: URL,
    queuePath: String,
    ownedRoots: [URL],
    userDefaultsSuites: [String],
    boundary: IosEraseBoundary,
    clearCredentials: @escaping () throws -> Void,
    credentialsPresent: @escaping () throws -> Bool,
    closeQueue: @escaping () -> Void
  ) {
    self.markerURL = markerURL
    self.queuePath = queuePath
    self.ownedRoots = ownedRoots
    self.userDefaultsSuites = userDefaultsSuites
    self.boundary = boundary
    self.clearCredentials = clearCredentials
    self.credentialsPresent = credentialsPresent
    self.closeQueue = closeQueue
  }

  func begin(reason: String) -> Bool {
    guard reason.range(of: "^[a-z_]{1,64}$", options: .regularExpression) != nil else {
      return false
    }
    do {
      let value = "\(UUID().uuidString.lowercased()):\(reason)"
      try Data(value.utf8).write(to: markerURL, options: .atomic)
      return eraseMarkedData()
    } catch {
      return false
    }
  }

  func resumeIfNeeded() -> Bool {
    !markerExists() || eraseMarkedData()
  }

  func markerExists() -> Bool {
    FileManager.default.fileExists(atPath: markerURL.path)
  }

  func inventory() -> IosEraseInventory {
    let databaseFiles = [queuePath, queuePath + "-wal", queuePath + "-shm"]
      .filter { FileManager.default.fileExists(atPath: $0) }.count
    let preferenceEntries = userDefaultsSuites.reduce(into: 0) { count, suite in
      count += UserDefaults.standard.persistentDomain(forName: suite)?.count ?? 0
    }
    return IosEraseInventory(
      credentials: (try? credentialsPresent()) ?? true,
      databaseFiles: databaseFiles,
      ownedFiles: ownedRoots.reduce(0) { $0 + ownedFileCount($1) },
      preferenceEntries: preferenceEntries,
      scheduledOrNotified: boundary.residueCount())
  }

  private func eraseMarkedData() -> Bool {
    guard markerExists() else { return true }
    guard markerIsValid() else { return false }
    boundary.cancelBackgroundAndNotifications()
    try? clearCredentials()
    closeQueue()
    for path in [queuePath, queuePath + "-wal", queuePath + "-shm"] {
      try? FileManager.default.removeItem(atPath: path)
    }
    for root in ownedRoots {
      try? FileManager.default.removeItem(at: root)
    }
    for suite in userDefaultsSuites {
      UserDefaults.standard.removePersistentDomain(forName: suite)
    }
    guard inventory().isEmpty else { return false }
    do {
      try FileManager.default.removeItem(at: markerURL)
      return true
    } catch {
      return false
    }
  }

  private func markerIsValid() -> Bool {
    guard
      let data = try? Data(contentsOf: markerURL), data.count <= 128,
      let value = String(data: data, encoding: .utf8)
    else { return false }
    return value.range(
      of:
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:[a-z_]{1,64}$",
      options: .regularExpression) != nil
  }

  private func ownedFileCount(_ root: URL) -> Int {
    guard FileManager.default.fileExists(atPath: root.path) else { return 0 }
    let values = try? root.resourceValues(forKeys: [.isSymbolicLinkKey, .isDirectoryKey])
    if values?.isSymbolicLink == true || values?.isDirectory != true { return 1 }
    let keys: [URLResourceKey] = [.isSymbolicLinkKey]
    return FileManager.default.enumerator(
      at: root,
      includingPropertiesForKeys: keys,
      options: [],
      errorHandler: { _, _ in false })?.allObjects.count ?? 0
  }
}
