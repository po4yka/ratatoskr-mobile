import Shared
import SwiftUI
import UIKit

struct ComposeRootView: UIViewControllerRepresentable {
  let controller: IosApplicationController

  func makeUIViewController(context: Context) -> UIViewController {
    MainViewControllerKt.MainViewController(controller: controller)
  }

  func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    // Shared Compose owns durable presentation state; the native shell owns lifecycle only.
  }
}
