import SwiftUI
import OpenFluxUI

/// The shared Compose Multiplatform UI (OpenFluxClientShared, the one Android
/// and Desktop use), built from kmp/ into OpenFluxUI.framework. While the port
/// is in progress it opens from the profile menu next to the Swift UI; the
/// tunnel extension is the same in both.
struct KotlinUIScreen: View {
    let onClose: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            // Edge to edge: the shared UI pads itself for the notch and the home
            // indicator (windowInsetsPadding(safeDrawing)); held inside SwiftUI's
            // safe area it was boxed in, with bands of system background around it.
            ComposeHost().ignoresSafeArea()
            Button(action: onClose) {
                Image(systemName: "xmark.circle.fill")
                    .font(.title2)
                    .foregroundColor(.secondary)
                    .padding(12)
            }
            .accessibilityLabel("Закрыть новый интерфейс")
        }
    }
}

private struct ComposeHost: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
