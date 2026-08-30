import UIKit

final class ShareViewController: UIViewController {
  private var task: Task<Void, Never>?
  private var finished = false

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .systemBackground
    let label = UILabel()
    label.text = "Saving to Ratatoskr…"
    label.textAlignment = .center
    label.numberOfLines = 0
    label.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(label)
    NSLayoutConstraint.activate([
      label.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
      label.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
      label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
    ])
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    guard task == nil else { return }
    task = Task { [weak self] in
      guard let self else { return }
      let providers =
        extensionContext?.inputItems
        .compactMap { $0 as? NSExtensionItem }
        .flatMap { $0.attachments ?? [] }
        ?? []
      let fileProviders = providers.map(ItemProviderFileStager.init(provider:)).filter(
        \.supportsFile)
      if !fileProviders.isEmpty {
        do {
          guard providers.count == 1, fileProviders.count == 1 else {
            throw ShareParseError.ambiguous
          }
          guard
            let container = FileManager.default.containerURL(
              forSecurityApplicationGroupIdentifier: Self.appGroup)
          else { throw ShareEnvelopeError.unavailable }
          let id = UUID()
          let descriptor = try await fileProviders[0].stage(
            rootURL: container.appendingPathComponent("ShareArtifacts", isDirectory: true),
            artifactID: id)
          _ = try AppGroupEnvelopeStore(
            rootURL: container.appendingPathComponent("ShareInbox", isDirectory: true)
          ).publish(ShareEnvelope(id: id, capturedAt: Date(), file: descriptor))
          finish(.success(()))
        } catch {
          finish(.failure(error))
        }
        return
      }
      switch await ShareExtensionParser().parse(
        loaders: providers.map(ItemProviderLoader.init(provider:)))
      {
      case .success(let intake):
        do {
          guard
            let container = FileManager.default.containerURL(
              forSecurityApplicationGroupIdentifier: Self.appGroup
            )
          else { throw ShareEnvelopeError.unavailable }
          let store = AppGroupEnvelopeStore(
            rootURL: container.appendingPathComponent("ShareInbox", isDirectory: true)
          )
          _ = try store.publish(
            ShareEnvelope(id: UUID(), capturedAt: Date(), intake: intake)
          )
          finish(.success(()))
        } catch {
          finish(.failure(ShareEnvelopeError.unavailable))
        }
      case .failure(let error):
        finish(.failure(error))
      }
    }
  }

  override func viewDidDisappear(_ animated: Bool) {
    task?.cancel()
    super.viewDidDisappear(animated)
  }

  @MainActor
  private func finish(_ result: Result<Void, Error>) {
    guard !finished else { return }
    finished = true
    switch result {
    case .success:
      extensionContext?.completeRequest(returningItems: nil)
    case .failure(let error):
      extensionContext?.cancelRequest(withError: error)
    }
  }

  private static let appGroup = "group.com.ratatoskr.mobile"
}
