import Foundation

struct ClaimedShareEnvelope: Equatable, Sendable {
  let envelope: ShareEnvelope
  let fileURL: URL
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
        let claim = ClaimedShareEnvelope(envelope: envelope, fileURL: destination)
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

  private func createDirectories() throws {
    do {
      for url in [inboxURL, processingURL, rejectedURL] {
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
        return ClaimedShareEnvelope(envelope: try store.loadPublished(at: url), fileURL: url)
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
      activeID = nil
    } catch {
      throw ShareEnvelopeError.unavailable
    }
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
