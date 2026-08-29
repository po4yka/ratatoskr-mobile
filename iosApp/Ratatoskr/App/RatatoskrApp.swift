import SwiftUI

@main
struct RatatoskrApp: App {
  @StateObject private var model = IosAppModel()
  @Environment(\.scenePhase) private var scenePhase

  var body: some Scene {
    WindowGroup {
      ComposeRootView(controller: model.controller)
        .ignoresSafeArea()
        .onOpenURL { model.openLibraryURL($0) }
    }
    .onChange(of: scenePhase, initial: true) { _, phase in
      model.setSceneActive(phase == .active)
    }
  }
}
