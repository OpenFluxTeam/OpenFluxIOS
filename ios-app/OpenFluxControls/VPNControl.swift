import AppIntents
import NetworkExtension
import SwiftUI
import WidgetKit

/// Control Center's OpenFlux switch (iOS 18): turns the VPN on with the last
/// connection the app made, and off. The configuration (and the profile's key in
/// the shared Keychain) is the one the app saved when it last connected; this only
/// starts and stops it, as Settings → VPN does.
@main
struct OpenFluxControls: WidgetBundle {
    var body: some Widget {
        VPNControl()
    }
}

struct VPNControl: ControlWidget {
    static let kind = "com.p1neapplexpress-saharev.openflux.vpn"

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind, provider: VPNState()) { on in
            ControlWidgetToggle("OpenFlux", isOn: on, action: SetVPNIntent()) { isOn in
                Label(isOn ? "Включён" : "Выключен", systemImage: isOn ? "lock.shield.fill" : "lock.shield")
            }
        }
        .displayName("OpenFlux VPN")
        .description("Включает VPN с последним подключением из приложения.")
    }
}

struct VPNState: ControlValueProvider {
    var previewValue: Bool { false }

    func currentValue() async throws -> Bool {
        guard let m = try await NETunnelProviderManager.loadAllFromPreferences().first else { return false }
        switch m.connection.status {
        case .connected, .connecting, .reasserting: return true
        default: return false
        }
    }
}

struct SetVPNIntent: SetValueIntent {
    static let title: LocalizedStringResource = "OpenFlux VPN"

    @Parameter(title: "Включён")
    var value: Bool

    func perform() async throws -> some IntentResult {
        guard let m = try await NETunnelProviderManager.loadAllFromPreferences().first else {
            // Nothing to start yet: the first connection is made in the app.
            throw VPNControlError.noConfiguration
        }
        if value {
            // As the app connects: iOS brings the tunnel back when it drops.
            m.isEnabled = true
            m.isOnDemandEnabled = true
            m.onDemandRules = [NEOnDemandRuleConnect()]
            try await m.saveToPreferences()
            try await m.loadFromPreferences()
            try m.connection.startVPNTunnel()
        } else {
            // On-demand off first, or iOS would bring the tunnel straight back.
            m.isOnDemandEnabled = false
            try await m.saveToPreferences()
            m.connection.stopVPNTunnel()
        }
        return .result()
    }
}

enum VPNControlError: Error, CustomLocalizedStringResourceConvertible {
    case noConfiguration

    var localizedStringResource: LocalizedStringResource {
        "Сначала подключитесь один раз в приложении OpenFlux"
    }
}
