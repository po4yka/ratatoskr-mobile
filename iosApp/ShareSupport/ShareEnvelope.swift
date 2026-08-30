import CryptoKit
import Darwin
import Foundation

enum ShareIntake: Equatable, Sendable {
  case url(originalText: String, url: URL)
  case text(originalText: String)
}

struct ShareFileCandidate: Equatable, Sendable {
  let sourceURL: URL
  let mediaType: String
  let displayName: String
  let sizeBytes: Int64
}

enum ShareEnvelopeKind: String, Codable, Sendable {
  case url
  case text
  case file
}

struct ShareFileDescriptor: Codable, Equatable, Sendable {
  let artifactID: UUID
  let displayName: String
  let mediaType: String
  let sizeBytes: Int64
  let sha256Hex: String
}

struct ShareEnvelope: Codable, Equatable, Sendable {
  let schema: Int
  let id: UUID
  let capturedAt: Date
  let kind: ShareEnvelopeKind
  let originalText: String
  let url: URL?
  let file: ShareFileDescriptor?

  init(id: UUID, capturedAt: Date, intake: ShareIntake) {
    self.schema = 1
    self.id = id
    self.capturedAt = capturedAt
    switch intake {
    case .url(let originalText, let url):
      self.kind = .url
      self.originalText = originalText
      self.url = url
      self.file = nil
    case .text(let originalText):
      self.kind = .text
      self.originalText = originalText
      self.url = nil
      self.file = nil
    }
  }

  init(id: UUID, capturedAt: Date, file: ShareFileDescriptor) {
    self.schema = 1
    self.id = id
    self.capturedAt = capturedAt
    self.kind = .file
    self.originalText = file.displayName
    self.url = nil
    self.file = file
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
      let allowedKeys = requiredKeys.union(["url", "file"])
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
        envelope.file == nil,
        let scheme = url.scheme?.lowercased(),
        scheme == "http" || scheme == "https",
        url.host?.isEmpty == false
      else { throw ShareEnvelopeError.invalid }
    case .text:
      guard envelope.url == nil, envelope.file == nil else { throw ShareEnvelopeError.invalid }
    case .file:
      guard envelope.url == nil, let file = envelope.file else { throw ShareEnvelopeError.invalid }
      guard
        file.sizeBytes > 0, file.sizeBytes <= 100 * 1_024 * 1_024,
        Self.supportedFileTypes.contains(file.mediaType),
        file.sha256Hex.count == 64,
        file.sha256Hex.allSatisfy({ $0.isHexDigit && !$0.isUppercase }),
        !file.displayName.isEmpty, file.displayName.utf8.count <= 255
      else { throw ShareEnvelopeError.invalid }
    }
  }

  private static let maximumInputBytes = 100_000
  private static let maximumEnvelopeBytes = 128 * 1024
  private static let supportedFileTypes: Set<String> = [
    "application/pdf", "image/jpeg", "image/png", "text/plain",
  ]
}

struct AppGroupArtifactStore: Sendable {
  let rootURL: URL

  func stage(_ candidate: ShareFileCandidate, artifactID: UUID) throws -> ShareFileDescriptor {
    let maximumBytes: Int64 = 100 * 1_024 * 1_024
    guard
      candidate.sourceURL.isFileURL,
      candidate.sizeBytes > 0, candidate.sizeBytes <= maximumBytes,
      Self.supportedTypes.contains(candidate.mediaType)
    else { throw ShareEnvelopeError.invalid }
    do {
      let values = try candidate.sourceURL.resourceValues(
        forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
      guard
        values.isRegularFile == true, values.isSymbolicLink != true,
        Int64(values.fileSize ?? -1) == candidate.sizeBytes
      else { throw ShareEnvelopeError.invalid }
      try FileManager.default.createDirectory(
        at: rootURL, withIntermediateDirectories: true,
        attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
      let rootValues = try rootURL.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
      guard rootValues.isDirectory == true, rootValues.isSymbolicLink != true else {
        throw ShareEnvelopeError.invalid
      }
      let lockURL = rootURL.appendingPathComponent(".ratatoskr-stage.lock", isDirectory: false)
      let lockDescriptor = Darwin.open(lockURL.path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
      guard lockDescriptor >= 0, flock(lockDescriptor, LOCK_EX) == 0 else {
        if lockDescriptor >= 0 { Darwin.close(lockDescriptor) }
        throw ShareEnvelopeError.unavailable
      }
      defer {
        flock(lockDescriptor, LOCK_UN)
        Darwin.close(lockDescriptor)
      }
      let existing = try FileManager.default.contentsOfDirectory(
        at: rootURL,
        includingPropertiesForKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey],
        options: [.skipsHiddenFiles])
      var existingBytes: Int64 = 0
      for url in existing {
        let values = try url.resourceValues(
          forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
        guard values.isRegularFile == true, values.isSymbolicLink != true else {
          throw ShareEnvelopeError.invalid
        }
        existingBytes += Int64(values.fileSize ?? 0)
      }
      guard existing.count < Self.maximumArtifactCount,
        existingBytes + candidate.sizeBytes <= Self.maximumStagedBytes
      else { throw ShareEnvelopeError.oversized }
      let published = publishedArtifactURL(id: artifactID)
      guard !FileManager.default.fileExists(atPath: published.path) else {
        throw ShareEnvelopeError.invalid
      }
      let temporary = rootURL.appendingPathComponent(
        ".\(artifactID.uuidString.lowercased()).partial")
      defer { try? FileManager.default.removeItem(at: temporary) }
      guard
        FileManager.default.createFile(
          atPath: temporary.path, contents: nil,
          attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
      else { throw ShareEnvelopeError.unavailable }
      let input = try FileHandle(forReadingFrom: candidate.sourceURL)
      let output = try FileHandle(forWritingTo: temporary)
      defer {
        try? input.close()
        try? output.close()
      }
      var digest = SHA256()
      var copied: Int64 = 0
      var evidence = Data()
      while let chunk = try input.read(upToCount: 64 * 1_024), !chunk.isEmpty {
        copied += Int64(chunk.count)
        guard copied <= maximumBytes else { throw ShareEnvelopeError.oversized }
        guard existingBytes + copied <= Self.maximumStagedBytes else {
          throw ShareEnvelopeError.oversized
        }
        if evidence.count < 16 { evidence.append(chunk.prefix(16 - evidence.count)) }
        digest.update(data: chunk)
        try output.write(contentsOf: chunk)
      }
      guard copied == candidate.sizeBytes else { throw ShareEnvelopeError.invalid }
      guard Self.matchesEvidence(candidate.mediaType, evidence) else {
        throw ShareEnvelopeError.invalid
      }
      try output.synchronize()
      try output.close()
      try FileManager.default.moveItem(at: temporary, to: published)
      let sha256Hex = digest.finalize().map { String(format: "%02x", $0) }.joined()
      return ShareFileDescriptor(
        artifactID: artifactID,
        displayName: Self.sanitize(candidate.displayName),
        mediaType: candidate.mediaType,
        sizeBytes: copied,
        sha256Hex: sha256Hex)
    } catch let error as ShareEnvelopeError {
      throw error
    } catch {
      throw ShareEnvelopeError.unavailable
    }
  }

  func publishedArtifactURL(id: UUID) -> URL {
    rootURL.appendingPathComponent(id.uuidString.lowercased(), isDirectory: false)
  }

  func verifyPublished(_ descriptor: ShareFileDescriptor) -> Bool {
    let url = publishedArtifactURL(id: descriptor.artifactID)
    do {
      let values = try url.resourceValues(
        forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
      guard
        values.isRegularFile == true, values.isSymbolicLink != true,
        Int64(values.fileSize ?? -1) == descriptor.sizeBytes
      else { return false }
      let input = try FileHandle(forReadingFrom: url)
      defer { try? input.close() }
      var digest = SHA256()
      var evidence = Data()
      while let chunk = try input.read(upToCount: 64 * 1_024), !chunk.isEmpty {
        if evidence.count < 16 { evidence.append(chunk.prefix(16 - evidence.count)) }
        digest.update(data: chunk)
      }
      let hex = digest.finalize().map { String(format: "%02x", $0) }.joined()
      return hex == descriptor.sha256Hex && Self.matchesEvidence(descriptor.mediaType, evidence)
    } catch {
      return false
    }
  }

  private static func sanitize(_ value: String) -> String {
    let name = URL(fileURLWithPath: value).lastPathComponent
      .filter { !$0.isNewline && !$0.isASCIIControl }
      .trimmingCharacters(in: .whitespacesAndNewlines)
    return String((name.isEmpty ? "shared-file" : name).prefix(255))
  }

  private static func matchesEvidence(_ mediaType: String, _ bytes: Data) -> Bool {
    switch mediaType {
    case "application/pdf": return bytes.starts(with: Data("%PDF-".utf8))
    case "image/jpeg": return bytes.starts(with: Data([0xff, 0xd8, 0xff]))
    case "image/png":
      return bytes.starts(with: Data([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
    case "text/plain": return !bytes.contains(0)
    default: return false
    }
  }

  private static let supportedTypes: Set<String> = [
    "application/pdf", "image/jpeg", "image/png", "text/plain",
  ]
  private static let maximumStagedBytes: Int64 = 512 * 1_024 * 1_024
  private static let maximumArtifactCount = 64
}

extension Character {
  fileprivate var isASCIIControl: Bool {
    unicodeScalars.allSatisfy { $0.value < 0x20 || $0.value == 0x7f }
  }
}
