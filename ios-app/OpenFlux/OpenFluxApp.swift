import SwiftUI
import NetworkExtension
import WidgetKit

@main
struct OpenFluxApp: App {
    init() {
        // The shared Kotlin UI's camera, photos and QR drawing (IosHooks).
        KotlinHooks.install()
        // Control Center's OpenFlux switch (OpenFluxControls) follows the VPN whoever turns it.
        NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: nil, queue: .main) { _ in
            if #available(iOS 18.0, *) {
                ControlCenter.shared.reloadControls(ofKind: "com.pnonetwork.openflux.vpn")
            }
        }
    }

    var body: some Scene {
        // One interface: the shared Kotlin UI. The old Swift UI (ContentView) is no longer
        // switchable from the iOS Settings app; App Review read that as a hidden mode (5.6).
        WindowGroup {
            KotlinRootView()
        }
    }
}
