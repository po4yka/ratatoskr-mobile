import SwiftUI

@main
struct RatatoskrApp: App {
  @StateObject private var model = IosAppModel()
  @Environment(\.scenePhase) private var scenePhase

  var body: some Scene {
    WindowGroup {
      ComposeRootView(controller: model.controller)
        .ignoresSafeArea()
    }
    .onChange(of: scenePhase, initial: true) { _, phase in
      model.setSceneActive(phase == .active)
    }
  }
}
