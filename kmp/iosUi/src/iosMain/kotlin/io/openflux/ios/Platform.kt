package io.openflux.ios

import io.openflux.desktop.service.PlatformKind
import io.openflux.desktop.service.PlatformServices
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * What only the Swift side does well (UIKit screens, CoreImage), handed to the
 * shared UI's platform: Swift sets these when the app starts (KotlinHooks.swift).
 * Each answers through its callback on the main thread; null means cancelled
 * or nothing found.
 */
object IosHooks {
    /** The camera QR scanner; the text in the code. */
    var scanQr: ((callback: (String?) -> Unit) -> Unit)? = null

    /** A photo picked from the library, read for a QR code; the text in it. */
    var pickQrPhoto: ((callback: (String?) -> Unit) -> Unit)? = null

    /** A QR code of the text: its modules row by row, "1" dark, "0" light, rows joined by "\n". */
    var qrRows: ((String) -> String?)? = null

    /** A QR code in the image on the pasteboard, read; null when there is none. */
    var qrFromPasteboardImage: (() -> String?)? = null
}

@OptIn(ExperimentalForeignApi::class)
class IosPlatformServices : PlatformServices {
    override val kind = PlatformKind.Ios
    override val appVersion: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String).orEmpty()
    override val coreVersion: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.let { "в сборке $it" }.orEmpty()
    override val clientRepo: String = "saharev1/OpenFlux"
    override val systemProxySupported = false
    override val fullTunnelSupported = true
    override val elevated = false
    // TestFlight / the App Store update the app, and buying a digital service in an
    // iOS app may go only through Apple: neither is offered here.
    override val selfUpdates = false
    override val purchasesInApp = false
    override val supportUrl = "https://t.me/+4waLgXpI46IzOThi"
    override fun restartElevated() = false

    override fun clipboardText(): String? = UIPasteboard.generalPasteboard.string
    override fun setClipboardText(text: String) { UIPasteboard.generalPasteboard.string = text }

    override val clipboardImageSupported: Boolean get() = IosHooks.qrFromPasteboardImage != null
    override fun qrFromClipboardImage(): String? = IosHooks.qrFromPasteboardImage?.invoke()

    override val cameraScanSupported: Boolean get() = IosHooks.scanQr != null
    override suspend fun scanQr(): String? = withContext(Dispatchers.Main) {
        val hook = IosHooks.scanQr ?: return@withContext null
        suspendCoroutine { c -> hook { c.resume(it) } }
    }

    /**
     * "QR из галереи": the shared screen picks a file, then reads a QR from it
     * (qrFromFile). iOS hands out photos, not paths, so the pick reads the code
     * at once and the "path" carries its text.
     */
    override suspend fun pickFile(title: String, extensions: List<String>): String? = withContext(Dispatchers.Main) {
        val hook = IosHooks.pickQrPhoto ?: return@withContext null
        suspendCoroutine<String?> { c -> hook { c.resume(it?.let { text -> PICKED + text }) } }
    }

    override fun qrFromFile(path: String): String? = path.takeIf { it.startsWith(PICKED) }?.removePrefix(PICKED)

    override fun readTextFile(path: String, maxBytes: Int): String? = null

    override fun qrMatrix(text: String): List<BooleanArray> =
        IosHooks.qrRows?.invoke(text)?.lines()?.filter { it.isNotEmpty() }?.map { row -> BooleanArray(row.length) { row[it] == '1' } }
            ?: emptyList()

    override fun openUrl(url: String) {
        val u = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(u, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    /** A node's encryption secret: 32 symbols from the system's CSPRNG, without modulo bias. */
    override fun newSecret(): String = buildString {
        while (length < 32) {
            memScoped {
                val bytes = allocArray<UByteVar>(64)
                check(SecRandomCopyBytes(kSecRandomDefault, 64u, bytes) == 0) { "SecRandomCopyBytes failed" }
                for (i in 0 until 64) {
                    val b = bytes[i].toInt()
                    // 224 = 4 * 56: bytes above it would favour the first symbols, so they are dropped.
                    if (b < 224 && length < 32) append(ALPHABET[b % ALPHABET.length])
                }
            }
        }
    }

    override fun now(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
    override suspend fun latestRelease(): String? = null

    private companion object {
        const val PICKED = "qr-text:"
        // 56 symbols, 32 of them: ~186 bits.
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    }
}
