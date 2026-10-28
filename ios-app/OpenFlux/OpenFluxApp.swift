import SwiftUI
import NetworkExtension
import WidgetKit

@main
struct OpenFluxApp: App {
    /// Settings → OpenFlux → «Классический интерфейс» (Settings.bundle): the Swift UI.
    @AppStorage("classic_ui") private var classic = false

    init() {
        // The shared Kotlin UI's camera, photos and QR drawing (IosHooks).
        KotlinHooks.install()
        // Control Center's OpenFlux switch (OpenFluxControls) follows the VPN whoever turns it.
        NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: nil, queue: .main) { _ in
            if #available(iOS 18.0, *) {
                ControlCenter.shared.reloadControls(ofKind: "com.p1neapplexpress-saharev.openflux.vpn")
            }
        }
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
