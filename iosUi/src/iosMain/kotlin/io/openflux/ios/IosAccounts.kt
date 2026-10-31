package io.openflux.ios

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import io.openflux.app.platform.BrowserPage
import io.openflux.app.platform.BrowserViews
import io.openflux.desktop.model.AccountCookies
import io.openflux.desktop.model.AccountKind
import io.openflux.desktop.model.AccountSession
import io.openflux.desktop.model.Profile
import io.openflux.desktop.service.AccountBrowser
import io.openflux.desktop.service.AccountException
import io.openflux.desktop.service.AccountRepository
import io.openflux.desktop.service.ProbeResult
import io.openflux.desktop.service.SessionProbe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieSecure
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.timeIntervalSinceDate
import platform.Foundation.setValue
import platform.WebKit.WKContentWorld
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val accountJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/**
 * Sign-ins (Yandex, Mail.ru) in the shared Keychain: a session's cookies are
 * as good as a password, so they never go to the defaults.
 */
class KeychainAccountRepository : AccountRepository {
    private val serializer = ListSerializer(AccountSession.serializer())
    private val state = MutableStateFlow(load())
    override val sessions: StateFlow<Map<AccountKind, AccountSession>> = state.asStateFlow()

    private fun load(): Map<AccountKind, AccountSession> =
        SharedKeychain.get(SERVICE, ACCOUNT)
            ?.let { runCatching { accountJson.decodeFromString(serializer, it) }.getOrNull() }
            ?.associateBy { it.kind }.orEmpty()

    private fun store(next: Map<AccountKind, AccountSession>) {
        SharedKeychain.set(SERVICE, ACCOUNT, if (next.isEmpty()) "" else accountJson.encodeToString(serializer, next.values.toList()))
        state.value = next
    }

    override fun save(session: AccountSession) = store(state.value + (session.kind to session))
    override fun remove(kind: AccountKind) = store(state.value - kind)

    private companion object {
        const val SERVICE = "openflux.accounts"
        const val ACCOUNT = "sessions"
    }
}

/**
 * The cookies the tunnel extension starts with: the core's cookie store
 * ({"type URL": {name: value}}, transport/cookiestore.go) holding the
 * signed-in services' cookies for the profile's carriers. The extension
 * merges it into its own store file before the core starts
 * (PacketTunnelProvider, OpenFluxSetCookieStore).
 */
object TunnelCookies {
    private const val SERVICE = "openflux.accounts"
    private const val ACCOUNT = "cookiestore"
    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

    fun hand(profile: Profile, sessions: Map<AccountKind, AccountSession>) {
        // The core keys a carrier's jar "type URL" (mobile/session.go, attachCaptcha).
        val store = AccountCookies.seed(emptyMap(), profile, sessions, key = { "${it.type.cliName} ${it.value.trim()}" })
        SharedKeychain.set(SERVICE, ACCOUNT, if (store.isEmpty()) "" else accountJson.encodeToString(serializer, store))
    }
}

private class WebPage(val view: WKWebView) : BrowserPage

/** Shows the built-in browser's page (a WKWebView) inside the Compose UI. */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
object IosBrowserViews : BrowserViews {
    @Composable
    override fun Page(page: BrowserPage, modifier: Modifier) {
        val web = (page as? WebPage)?.view ?: return
        UIKitView(
            factory = { web },
            modifier = modifier,
            // The page takes its touches straight away: it scrolls and has fields of its own.
            properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative),
        )
    }
}

/**
 * The built-in browser for signing in and making documents: a WKWebView
 * with a data store of its own that lives only while the page is open, so
 * nothing of the service stays in the app but the session [Accounts] saves.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAccountBrowser : AccountBrowser {
    private val _page = MutableStateFlow<BrowserPage?>(null)
    override val page: StateFlow<BrowserPage?> = _page
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var web: WKWebView? = null
    private var watch: Job? = null

    // Read off the main thread by Accounts: kept here by [watch], which reads the view on it.
    private var currentUrl = ""
    private var isLoading = false
    private var visited = mutableListOf<String>()

    override val url: String get() = currentUrl
    override val loading: Boolean get() = isLoading
    override val closed: Boolean get() = web == null
    override val history: List<String> get() = visited.toList()

    override suspend fun open(kind: AccountKind, url: String, cookies: Map<String, String>, onStep: (String) -> Unit) = withContext(Dispatchers.Main) {
        closeNow()
        val config = WKWebViewConfiguration()
        config.websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        val view = WKWebView(frame = CGRectZero.readValue(), configuration = config)
        val domain = kind.cookieDomain
        for ((name, value) in cookies) {
            val cookie = NSHTTPCookie.cookieWithProperties(
                mapOf<Any?, Any?>(
                    NSHTTPCookieName to name, NSHTTPCookieValue to value,
                    NSHTTPCookieDomain to domain, NSHTTPCookiePath to "/", NSHTTPCookieSecure to "TRUE",
                ),
            ) ?: continue
            suspendCoroutine { c -> config.websiteDataStore.httpCookieStore.setCookie(cookie) { c.resume(Unit) } }
        }
        web = view
        currentUrl = url
        isLoading = true
        visited = mutableListOf(url)
        val target = NSURL.URLWithString(url) ?: throw AccountException("Неверный адрес страницы")
        view.loadRequest(NSURLRequest.requestWithURL(target))
        _page.value = WebPage(view)
        onStep("Открываю ${target.host ?: url}…")
        watch = main.launch {
            while (isActive) {
                val w = web ?: break
                val now = w.URL?.absoluteString
                if (now != null && now != currentUrl) {
                    currentUrl = now
                    if (visited.lastOrNull() != now) visited += now
                }
                isLoading = w.loading
                delay(250)
            }
        }
    }

    override suspend fun evaluate(script: String): String = withContext(Dispatchers.Main) {
        val view = web ?: throw IllegalStateException("Страница закрыта")
        // As the desktop browser: the script may be a promise; its failure comes back as {"state":"fail"}.
        val body = """
            return await Promise.resolve().then(() => ($script))
              .then(r => String(r), e => JSON.stringify({state: 'fail', error: String((e && e.message) || e)}));
        """.trimIndent()
        withTimeoutOrNull(90_000) {
            suspendCoroutine { c ->
                view.callAsyncJavaScript(body, arguments = emptyMap<Any?, Any?>(), inFrame = null, inContentWorld = WKContentWorld.pageWorld) { result, error ->
                    c.resume(if (error != null) null else result?.toString().orEmpty())
                }
            }
        } ?: throw IllegalStateException("Страница не ответила")
    }

    override fun load(url: String) {
        main.launch {
            val target = NSURL.URLWithString(url) ?: return@launch
            web?.loadRequest(NSURLRequest.requestWithURL(target))
        }
    }

    override suspend fun cookies(kind: AccountKind): Map<String, String> = withContext(Dispatchers.Main) {
        val view = web ?: return@withContext emptyMap()
        val all: List<NSHTTPCookie> = suspendCoroutine { c ->
            view.configuration.websiteDataStore.httpCookieStore.getAllCookies { list -> c.resume(list.orEmpty().filterIsInstance<NSHTTPCookie>()) }
        }
        val hosts = kind.cookieUrls.mapNotNull { NSURL.URLWithString(it)?.host }
        val now = NSDate()
        // A cookie any of the service's pages would get; of the same name the most specific domain wins.
        all.filter { c -> c.expiresDate?.let { it.timeIntervalSinceDate(now) > 0 } ?: true }
            .filter { c -> val d = c.domain.trimStart('.'); hosts.any { it == d || it.endsWith(".$d") } }
            .sortedBy { it.domain.trimStart('.').length }
            .associate { it.name to it.value }
    }

    override fun close() {
        main.launch { closeNow() }
    }

    private fun closeNow() {
        watch?.cancel()
        watch = null
        web?.stopLoading()
        web?.removeFromSuperview()
        web = null
        isLoading = false
        _page.value = null
    }
}

/**
 * Asks the service's own page, with the saved cookies, whether the session
 * still works (HttpSessionProbe of the desktop and Android): a page that
 * ends at a sign-in means it is gone.
 */
class IosSessionProbe : SessionProbe {
    override suspend fun probe(kind: AccountKind, cookies: Map<String, String>): ProbeResult {
        val url = URLS[kind] ?: return ProbeResult.Offline
        val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
        config.HTTPShouldSetCookies = false
        config.HTTPCookieStorage = null
        config.timeoutIntervalForRequest = 15.0
        val session = NSURLSession.sessionWithConfiguration(config)
        val request = NSMutableURLRequest.requestWithURL(NSURL.URLWithString(url)!!)
        request.setValue(io.openflux.desktop.model.YandexDisk.USER_AGENT, forHTTPHeaderField = "User-Agent")
        request.setValue(AccountCookies.header(cookies), forHTTPHeaderField = "Cookie")
        return suspendCoroutine { c ->
            session.dataTaskWithRequest(request) { data, response, error ->
                val http = response as? NSHTTPURLResponse
                c.resume(
                    if (error != null || http == null) ProbeResult.Offline
                    else classify(kind, http.statusCode.toInt(), http.URL?.absoluteString.orEmpty(),
                        data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }.orEmpty()),
                )
            }.resume()
            session.finishTasksAndInvalidate()
        }
    }

    // Redirects are followed here, so the page it ends at tells a sign-in (the desktop reads the Location).
    private fun classify(kind: AccountKind, code: Int, finalUrl: String, body: String): ProbeResult = when {
        "showcaptcha" in finalUrl || "showcaptcha" in body -> ProbeResult.NeedsCheck
        SIGN_IN_HOSTS.any { it in finalUrl } -> ProbeResult.Expired
        kind == AccountKind.Yandex && code == 200 && "\"sk\":\"" in body -> ProbeResult.SignedIn
        kind == AccountKind.Yandex && code == 200 -> ProbeResult.Expired
        kind == AccountKind.Mailru && code == 200 -> ProbeResult.SignedIn
        else -> ProbeResult.Offline
    }

    private companion object {
        val URLS = mapOf(
            AccountKind.Yandex to "https://disk.yandex.ru/client/disk",
            AccountKind.Mailru to "https://e.mail.ru/inbox/",
        )
        val SIGN_IN_HOSTS = listOf("passport.yandex", "account.mail.ru", "e.mail.ru/login", "login.vk.com", "id.vk.ru", "id.vk.com")
    }
}
