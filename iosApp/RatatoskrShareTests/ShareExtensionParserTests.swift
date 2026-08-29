import Foundation
import XCTest

final class ShareExtensionParserTests: XCTestCase {
  func test_url_representation_preserves_original() async {
    let result = await parser().parse(loaders: [Loader(.values([.url("https://example.com/a")]))])

    XCTAssertEqual(
      result,
      .success(
        .url(originalText: "https://example.com/a", url: URL(string: "https://example.com/a")!))
    )
  }

  func test_plain_text_single_url_is_detected() async {
    let original = "Article https://example.com/read?x=1"
    let result = await parser().parse(loaders: [Loader(.values([.text(original)]))])

    XCTAssertEqual(
      result,
      .success(.url(originalText: original, url: URL(string: "https://example.com/read?x=1")!))
    )
  }

  func test_plain_text_without_url_is_preview_only() async {
    let result = await parser().parse(loaders: [Loader(.values([.text("Remember this")]))])

    XCTAssertEqual(result, .success(.text(originalText: "Remember this")))
  }

  func test_equivalent_representations_are_deduplicated() async {
    let result = await parser().parse(
      loaders: [
        Loader(.values([.url("https://example.com/a"), .text("https://example.com/a")]))
      ]
    )

    XCTAssertEqual(
      result,
      .success(
        .url(originalText: "https://example.com/a", url: URL(string: "https://example.com/a")!))
    )
  }

  func test_multiple_distinct_urls_are_rejected() async {
    let result = await parser().parse(
      loaders: [
        Loader(.values([.url("https://one.example/a")])),
        Loader(.values([.text("https://two.example/b")])),
      ]
    )

    XCTAssertEqual(result, .failure(.ambiguous))
  }

  func test_non_http_scheme_is_rejected() async {
    let result = await parser().parse(loaders: [Loader(.values([.text("file:///private/item")]))])

    XCTAssertEqual(result, .failure(.unsupported))
  }

  func test_oversized_text_is_rejected() async {
    let result = await parser().parse(
      loaders: [Loader(.values([.text(String(repeating: "a", count: 100_001))]))]
    )

    XCTAssertEqual(result, .failure(.oversized))
  }

  func test_provider_failure_is_visible() async {
    let result = await parser().parse(loaders: [Loader(.failure)])

    XCTAssertEqual(result, .failure(.unreadable))
  }

  func test_deadline_completes_once() async {
    let result = await parser().parse(loaders: [Loader(.never)], timeout: .milliseconds(10))

    XCTAssertEqual(result, .failure(.timedOut))
  }

  private func parser() -> ShareExtensionParser {
    ShareExtensionParser()
  }
}

private struct Loader: ShareItemLoading {
  enum Behavior: Sendable {
    case values([ShareRepresentation])
    case failure
    case never
  }

  let behavior: Behavior

  init(_ behavior: Behavior) {
    self.behavior = behavior
  }

  func loadRepresentations() async throws -> [ShareRepresentation] {
    switch behavior {
    case .values(let values): return values
    case .failure: throw ProviderFailure()
    case .never:
      try await Task.sleep(for: .seconds(60))
      return []
    }
  }
}

extension ShareRepresentation {
  fileprivate static func url(_ value: String) -> ShareRepresentation {
    ShareRepresentation(kind: .url, value: value)
  }

  fileprivate static func text(_ value: String) -> ShareRepresentation {
    ShareRepresentation(kind: .plainText, value: value)
  }
}

private struct ProviderFailure: Error {}
