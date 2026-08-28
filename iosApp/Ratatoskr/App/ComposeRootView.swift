import Shared
import SwiftUI
import UIKit

struct ComposeRootView: UIViewControllerRepresentable {
  func makeUIViewController(context: Context) -> UIViewController {
    MainViewControllerKt.MainViewController()
  }

  func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    // Shared Compose owns durable presentation state; the native shell owns lifecycle only.
  }
}
