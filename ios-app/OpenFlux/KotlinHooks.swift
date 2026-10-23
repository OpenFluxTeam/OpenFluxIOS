import SwiftUI
import UIKit
import PhotosUI
import CoreImage
import CoreImage.CIFilterBuiltins
import OpenFluxUI

/// What the shared Kotlin UI asks of the Swift side (IosHooks in iosUi/):
/// the camera scanner, a photo with a QR, drawing a QR, a QR in a copied image.
/// Each answers once, on the main thread.
enum KotlinHooks {
    static func install() {
        let hooks = IosHooks.shared
        // Kotlin's (String?) -> Unit comes over as returning KotlinUnit.
        hooks.scanQr = { done in DispatchQueue.main.async { presentScanner { _ = done($0) } } }
        hooks.pickQrPhoto = { done in DispatchQueue.main.async { presentPhotoPicker { _ = done($0) } } }
        hooks.qrRows = { text in qrRows(text) }
        hooks.qrFromPasteboardImage = { UIPasteboard.general.image.flatMap(readQR) }
        hooks.lastTunnelReport = { Secrets.neDiag() }
    }

    /// The Swift UI's profiles as links for the Kotlin UI to import, once
    /// (SwiftProfileMigration): the secret travels in the link as when sharing.
    @MainActor
    static func prepareMigration(_ store: ProfileStore) {
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: "kmp.migrated") else { return }
        let entries: [[String: Any]] = store.profiles.compactMap { p in
            guard let link = ShareLink.encode(profile: p) else { return nil }
            return ["name": p.name, "link": link, "selected": p.id == store.selected?.id]
        }
        guard let data = try? JSONSerialization.data(withJSONObject: entries),
              let json = String(data: data, encoding: .utf8) else { return }
        defaults.set(json, forKey: "kmp.migrate")
    }

    // MARK: - Presenting over whatever is on screen

    private static func topController() -> UIViewController? {
        let root = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first(where: \.isKeyWindow) }
            .first?.rootViewController
        var top = root
        while let next = top?.presentedViewController { top = next }
        return top
    }

    /// Answers a sheet that can be swiped away: nil then.
    private final class SheetDelegate: NSObject, UIAdaptivePresentationControllerDelegate {
        var done: ((String?) -> Void)?
        func finish(_ text: String?) { done?(text); done = nil }
        func presentationControllerDidDismiss(_ presentationController: UIPresentationController) { finish(nil) }
    }
    private static var sheet: SheetDelegate?

    private static func presentScanner(_ done: @escaping (String?) -> Void) {
        let delegate = SheetDelegate()
        delegate.done = done
        sheet = delegate
        var host: UIViewController?
        let view = QRScannerView { text in
            host?.dismiss(animated: true)
            delegate.finish(text)
        }
        let vc = UIHostingController(rootView: view)
        host = vc
        vc.presentationController?.delegate = delegate
        guard let top = topController() else { return delegate.finish(nil) }
        top.present(vc, animated: true)
    }

    private final class PickerDelegate: NSObject, PHPickerViewControllerDelegate {
        var done: ((String?) -> Void)?
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            let finish = done
            done = nil
            guard let item = results.first?.itemProvider, item.canLoadObject(ofClass: UIImage.self) else {
                finish?(nil); return
            }
            item.loadObject(ofClass: UIImage.self) { obj, _ in
                let text = (obj as? UIImage).flatMap(KotlinHooks.readQR)
                DispatchQueue.main.async { finish?(text) }
            }
        }
    }
    private static var picker: PickerDelegate?

    private static func presentPhotoPicker(_ done: @escaping (String?) -> Void) {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let vc = PHPickerViewController(configuration: config)
        let delegate = PickerDelegate()
        delegate.done = done
        picker = delegate
        vc.delegate = delegate
        guard let top = topController() else { return done(nil) }
        top.present(vc, animated: true)
    }

    // MARK: - QR codes

    static func readQR(_ image: UIImage) -> String? {
        guard let ci = CIImage(image: image),
              let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: nil,
                                        options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])
        else { return nil }
        return detector.features(in: ci).compactMap { ($0 as? CIQRCodeFeature)?.messageString }.first
    }

    /// The code's modules, one per pixel of the unscaled generator output.
    static func qrRows(_ text: String) -> String? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let out = filter.outputImage,
              let cg = CIContext().createCGImage(out, from: out.extent) else { return nil }
        let w = cg.width, h = cg.height
        var pixels = [UInt8](repeating: 255, count: w * h)
        guard let ctx = CGContext(data: &pixels, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w,
                                  space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue)
        else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var rows: [String] = []
        rows.reserveCapacity(h)
        for y in 0..<h {
            var row = ""
            row.reserveCapacity(w)
            for x in 0..<w { row.append(pixels[y * w + x] < 128 ? "1" : "0") }
            rows.append(row)
        }
        return rows.joined(separator: "\n")
    }
}
