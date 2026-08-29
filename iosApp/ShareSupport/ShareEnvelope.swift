import Foundation

enum ShareIntake: Equatable, Sendable {
  case url(originalText: String, url: URL)
  case text(originalText: String)
}

enum ShareEnvelopeKind: String, Codable, Sendable {
  case url
  case text
}

struct ShareEnvelope: Codable, Equatable, Sendable {
  let schema: Int
  let id: UUID
  let capturedAt: Date
  let kind: ShareEnvelopeKind
  let originalText: String
  let url: URL?

  init(id: UUID, capturedAt: Date, intake: ShareIntake) {
    self.schema = 1
    self.id = id
    self.capturedAt = capturedAt
    switch intake {
    case .url(let originalText, let url):
      self.kind = .url
      self.originalText = originalText
      self.url = url
    case .text(let originalText):
      self.kind = .text
      self.originalText = originalText
      self.url = nil
    }
  }
}

enum ShareEnvelopeError: Error, Equatable {
  case invalid
  case oversized
  case unavailable
}

struct AppGroupEnvelopeStore: Sendable {
  let rootURL: URL

  func encode(_ envelope: ShareEnvelope) throws -> Data {
    try validate(envelope)
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .millisecondsSince1970
    encoder.outputFormatting = [.sortedKeys]
    let data = try encoder.encode(envelope)
    guard data.count <= Self.maximumEnvelopeBytes else { throw ShareEnvelopeError.oversized }
    return data
  }

  func publish(_ envelope: ShareEnvelope) throws -> URL {
    do {
      try FileManager.default.createDirectory(
        at: rootURL,
        withIntermediateDirectories: true,
        attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
      )
      let data = try encode(envelope)
      let published = publishedURL(for: envelope.id)
      if FileManager.default.fileExists(atPath: published.path) {
        guard try loadPublished(at: published) == envelope else { throw ShareEnvelopeError.invalid }
        return published
      }
      let temporary = rootURL.appendingPathComponent(".\(envelope.id.uuidString.lowercased()).tmp")
      defer { try? FileManager.default.removeItem(at: temporary) }
      try data.write(
        to: temporary,
        options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
      )
      let handle = try FileHandle(forWritingTo: temporary)
      try handle.synchronize()
      try handle.close()
      try FileManager.default.moveItem(at: temporary, to: published)
      return published
    } catch let error as ShareEnvelopeError {
      throw error
    } catch {
      throw ShareEnvelopeError.unavailable
    }
  }

  func loadPublished(at url: URL) throws -> ShareEnvelope {
    do {
      let standardizedRoot = rootURL.standardizedFileURL
      let standardizedURL = url.standardizedFileURL
      guard
        standardizedURL.deletingLastPathComponent() == standardizedRoot,
        standardizedURL.pathExtension == "json",
        let filenameID = UUID(
          uuidString: standardizedURL.deletingPathExtension().lastPathComponent),
        standardizedURL.deletingPathExtension().lastPathComponent
          == filenameID.uuidString.lowercased()
      else { throw ShareEnvelopeError.invalid }

      let values = try standardizedURL.resourceValues(forKeys: [.isSymbolicLinkKey, .fileSizeKey])
      guard values.isSymbolicLink != true else { throw ShareEnvelopeError.invalid }
      guard let size = values.fileSize, size <= Self.maximumEnvelopeBytes else {
        throw ShareEnvelopeError.oversized
      }
      let data = try Data(contentsOf: standardizedURL, options: [.mappedIfSafe])
      guard data.count <= Self.maximumEnvelopeBytes else { throw ShareEnvelopeError.oversized }
      let object = try JSONSerialization.jsonObject(with: data)
      guard let dictionary = object as? [String: Any] else { throw ShareEnvelopeError.invalid }
      let requiredKeys: Set<String> = ["schema", "id", "capturedAt", "kind", "originalText"]
      let allowedKeys = requiredKeys.union(["url"])
      guard requiredKeys.isSubset(of: dictionary.keys),
        Set(dictionary.keys).isSubset(of: allowedKeys)
      else { throw ShareEnvelopeError.invalid }

      let decoder = JSONDecoder()
      decoder.dateDecodingStrategy = .millisecondsSince1970
      let envelope = try decoder.decode(ShareEnvelope.self, from: data)
      try validate(envelope)
      guard envelope.id == filenameID else { throw ShareEnvelopeError.invalid }
      return envelope
    } catch let error as ShareEnvelopeError {
      throw error
    } catch {
      throw ShareEnvelopeError.invalid
    }
  }

  func publishedURLs() -> [URL] {
    guard
      let urls = try? FileManager.default.contentsOfDirectory(
        at: rootURL,
        includingPropertiesForKeys: [.isSymbolicLinkKey, .isRegularFileKey],
        options: [.skipsHiddenFiles]
      )
    else { return [] }
    return urls.filter { url in
      guard
        url.pathExtension == "json",
        let id = UUID(uuidString: url.deletingPathExtension().lastPathComponent),
        url.deletingPathExtension().lastPathComponent == id.uuidString.lowercased(),
        let values = try? url.resourceValues(forKeys: [.isSymbolicLinkKey, .isRegularFileKey])
      else { return false }
      return values.isRegularFile == true && values.isSymbolicLink != true
    }.sorted { $0.lastPathComponent < $1.lastPathComponent }
  }

  private func publishedURL(for id: UUID) -> URL {
    rootURL.appendingPathComponent("\(id.uuidString.lowercased()).json", isDirectory: false)
  }

  private func validate(_ envelope: ShareEnvelope) throws {
    guard
      envelope.schema == 1,
      envelope.originalText.lengthOfBytes(using: .utf8) <= Self.maximumInputBytes,
      !envelope.originalText.isEmpty
    else { throw ShareEnvelopeError.invalid }
    switch envelope.kind {
    case .url:
      guard
        let url = envelope.url,
        let scheme = url.scheme?.lowercased(),
        scheme == "http" || scheme == "https",
        url.host?.isEmpty == false
      else { throw ShareEnvelopeError.invalid }
    case .text:
      guard envelope.url == nil else { throw ShareEnvelopeError.invalid }
    }
  }

  private static let maximumInputBytes = 100_000
  private static let maximumEnvelopeBytes = 128 * 1024
}
