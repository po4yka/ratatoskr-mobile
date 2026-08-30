import Foundation
import UniformTypeIdentifiers

enum ShareRepresentationKind: Hashable, Sendable {
  case url
  case plainText
}

struct ShareRepresentation: Hashable, Sendable {
  let kind: ShareRepresentationKind
  let value: String
}

protocol ShareItemLoading: Sendable {
  func loadRepresentations() async throws -> [ShareRepresentation]
}

enum ShareParseError: Error, Equatable, Sendable {
  case missingInput
  case unsupported
  case unreadable
  case oversized
  case ambiguous
  case timedOut
}

struct ShareFileParser: Sendable {
  func parse(_ candidates: [ShareFileCandidate]) -> Result<ShareFileCandidate, ShareParseError> {
    guard candidates.count == 1, let candidate = candidates.first else {
      return .failure(candidates.isEmpty ? .missingInput : .ambiguous)
    }
    guard candidate.sourceURL.isFileURL, !candidate.displayName.isEmpty else {
      return .failure(.unreadable)
    }
    guard Self.supportedTypes.contains(candidate.mediaType) else { return .failure(.unsupported) }
    guard candidate.sizeBytes > 0 else { return .failure(.unreadable) }
    guard candidate.sizeBytes <= 100 * 1_024 * 1_024 else { return .failure(.oversized) }
    return .success(candidate)
  }

  private static let supportedTypes: Set<String> = [
    "application/pdf", "image/jpeg", "image/png", "text/plain",
  ]
}

struct ShareExtensionParser: Sendable {
  func parse(
    loaders: [any ShareItemLoading],
    timeout: Duration = .seconds(5)
  ) async -> Result<ShareIntake, ShareParseError> {
    guard !loaders.isEmpty else { return .failure(.missingInput) }
    do {
      let representations = try await load(loaders: loaders, timeout: timeout)
      return classify(representations)
    } catch let error as ShareParseError {
      return .failure(error)
    } catch is CancellationError {
      return .failure(.timedOut)
    } catch {
      return .failure(.unreadable)
    }
  }

  private func load(
    loaders: [any ShareItemLoading],
    timeout: Duration
  ) async throws -> [ShareRepresentation] {
    try await withThrowingTaskGroup(of: [ShareRepresentation].self) { group in
      for loader in loaders {
        group.addTask { try await loader.loadRepresentations() }
      }
      group.addTask {
        try await Task.sleep(for: timeout)
        throw ShareParseError.timedOut
      }

      var loaded: [ShareRepresentation] = []
      var completedLoaders = 0
      while let values = try await group.next() {
        loaded.append(contentsOf: values)
        completedLoaders += 1
        if completedLoaders == loaders.count {
          group.cancelAll()
          return loaded
        }
      }
      throw ShareParseError.unreadable
    }
  }

  private func classify(
    _ representations: [ShareRepresentation]
  ) -> Result<ShareIntake, ShareParseError> {
    guard !representations.isEmpty else { return .failure(.missingInput) }
    let unique = Array(Set(representations))
    let byteCount = unique.reduce(0) { $0 + $1.value.lengthOfBytes(using: .utf8) }
    guard byteCount <= Self.maximumInputBytes else { return .failure(.oversized) }

    let plainTexts = Set(unique.filter { $0.kind == .plainText }.map(\.value))
    guard plainTexts.count <= 1 else { return .failure(.ambiguous) }

    var detected: [URL] = []
    for representation in unique {
      switch representation.kind {
      case .url:
        guard let url = Self.validPublicURL(representation.value) else {
          return .failure(.unsupported)
        }
        detected.append(url)
      case .plainText:
        let matches = Self.absoluteURLStrings(in: representation.value)
        guard matches.count <= 1 else { return .failure(.ambiguous) }
        if let match = matches.first {
          guard let url = Self.validPublicURL(match) else { return .failure(.unsupported) }
          detected.append(url)
        }
      }
    }

    let semanticURLs = Dictionary(grouping: detected, by: \.absoluteString).compactMap {
      $0.value.first
    }
    guard semanticURLs.count <= 1 else { return .failure(.ambiguous) }
    if let url = semanticURLs.first {
      let original = plainTexts.first ?? url.absoluteString
      return .success(.url(originalText: original, url: url))
    }
    if let text = plainTexts.first {
      return .success(.text(originalText: text))
    }
    return .failure(.unsupported)
  }

  private static func validPublicURL(_ value: String) -> URL? {
    guard
      let components = URLComponents(string: value),
      let scheme = components.scheme?.lowercased(),
      scheme == "http" || scheme == "https",
      components.host?.isEmpty == false,
      let url = components.url
    else { return nil }
    return url
  }

  private static func absoluteURLStrings(in text: String) -> [String] {
    let range = NSRange(text.startIndex..<text.endIndex, in: text)
    return absoluteURLExpression.matches(in: text, range: range).compactMap { match in
      guard let swiftRange = Range(match.range, in: text) else { return nil }
      return String(text[swiftRange]).trimmingCharacters(in: trailingDisplayPunctuation)
    }
  }

  private static let maximumInputBytes = 100_000
  private static let absoluteURLExpression =
    try! NSRegularExpression(pattern: #"(?i)\b[a-z][a-z0-9+.-]*://[^\s<>]+"#)
  private static let trailingDisplayPunctuation = CharacterSet(charactersIn: ".,;!?)]}")
}

struct ItemProviderLoader: ShareItemLoading, @unchecked Sendable {
  let provider: NSItemProvider

  func loadRepresentations() async throws -> [ShareRepresentation] {
    var loaded: [ShareRepresentation] = []
    var observedFailure = false
    if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
      do {
        let value = try await loadString(typeIdentifier: UTType.url.identifier, expectsURL: true)
        loaded.append(ShareRepresentation(kind: .url, value: value))
      } catch {
        observedFailure = true
      }
    }
    if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
      do {
        let value = try await loadString(
          typeIdentifier: UTType.plainText.identifier, expectsURL: false)
        loaded.append(ShareRepresentation(kind: .plainText, value: value))
      } catch {
        observedFailure = true
      }
    }
    if loaded.isEmpty {
      if observedFailure { throw ShareParseError.unreadable }
      throw ShareParseError.unsupported
    }
    return loaded
  }

  private func loadString(typeIdentifier: String, expectsURL: Bool) async throws -> String {
    try await withCheckedThrowingContinuation { continuation in
      provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil) { item, error in
        if let error {
          continuation.resume(throwing: error)
        } else if expectsURL, let url = item as? URL {
          continuation.resume(returning: url.absoluteString)
        } else if let value = item as? String {
          continuation.resume(returning: value)
        } else {
          continuation.resume(throwing: ShareParseError.unreadable)
        }
      }
    }
  }
}

struct ItemProviderFileStager: @unchecked Sendable {
  let provider: NSItemProvider

  var supportsFile: Bool {
    Self.binaryTypes.contains { provider.hasItemConformingToTypeIdentifier($0.type.identifier) }
      || (provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier)
        && provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier))
  }

  func stage(rootURL: URL, artifactID: UUID) async throws -> ShareFileDescriptor {
    let selected: (type: UTType, mediaType: String) =
      Self.binaryTypes.first(where: {
        provider.hasItemConformingToTypeIdentifier($0.type.identifier)
      }) ?? (type: UTType.plainText, mediaType: "text/plain")
    return try await withCheckedThrowingContinuation { continuation in
      provider.loadFileRepresentation(forTypeIdentifier: selected.type.identifier) { url, error in
        guard error == nil, let url else {
          continuation.resume(throwing: ShareParseError.unreadable)
          return
        }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
          let values = try url.resourceValues(forKeys: [.fileSizeKey])
          let candidate = ShareFileCandidate(
            sourceURL: url,
            mediaType: selected.mediaType,
            displayName: url.lastPathComponent,
            sizeBytes: Int64(values.fileSize ?? -1))
          guard case .success(let accepted) = ShareFileParser().parse([candidate]) else {
            throw ShareParseError.unsupported
          }
          let descriptor = try AppGroupArtifactStore(rootURL: rootURL).stage(
            accepted, artifactID: artifactID)
          continuation.resume(returning: descriptor)
        } catch {
          continuation.resume(throwing: error)
        }
      }
    }
  }

  private static let binaryTypes: [(type: UTType, mediaType: String)] = [
    (.pdf, "application/pdf"), (.jpeg, "image/jpeg"), (.png, "image/png"),
  ]
}
