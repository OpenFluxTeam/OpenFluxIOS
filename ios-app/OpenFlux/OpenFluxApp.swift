import SwiftUI

@main
struct OpenFluxApp: App {
    /// Settings → OpenFlux → «Классический интерфейс» (Settings.bundle): the Swift UI.
    @AppStorage("classic_ui") private var classic = false

    init() {
        // The shared Kotlin UI's camera, photos and QR drawing (IosHooks).
        KotlinHooks.install()
    }

    var body: some Scene {
        WindowGroup {
            if classic {
                ContentView()
            } else {
                KotlinRootView()
            }
        }
    }
}
