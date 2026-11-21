import SwiftUI

/// About screen of the classic interface. No donation addresses: App Store
/// guideline 3.1.1 allows in-app tips only through In-App Purchase.
struct InfoView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("OpenFlux").font(.title2).bold()
                        Text("TCP-туннель через скрытый транспорт. Клиент поднимает локальный SOCKS5 и системный VPN, трафик идёт через exit-node.")
                            .font(.footnote).foregroundColor(.secondary)
                    }

                    Spacer(minLength: 0)
                }
                .padding()
            }
            .navigationTitle("О приложении")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Закрыть") { dismiss() }
                }
            }
        }
        .navigationViewStyle(.stack)
    }
}
