package io.openflux.ios

import io.openflux.desktop.model.CoreShareLinkCodec
import io.openflux.desktop.model.PhpHostingException
import io.openflux.desktop.model.PhpProgress
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.ProfileSource
import io.openflux.desktop.service.PhpCallTransport
import io.openflux.desktop.service.ProfileRepository
import io.openflux.ios.core.OpenFluxFreeString
import io.openflux.ios.core.OpenFluxPhpCall
import io.openflux.ios.core.OpenFluxPhpCancel
import io.openflux.ios.core.OpenFluxPhpProgress
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import platform.Foundation.NSUserDefaults

/**
 * The hosting wizard's steps ("без сервера") through the app's copy of the core:
 * OpenFluxPhpCall, as Android's AndroidPhpTransport calls Mobile.phpCall. The
 * call blocks, so it runs off the main thread, one at a time, while a poller
 * hands the upload's progress to [onProgress].
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhpTransport : PhpCallTransport {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private fun progress(): String {
        val p = OpenFluxPhpProgress() ?: return ""
        return try { p.toKString() } finally { OpenFluxFreeString(p) }
    }

    override suspend fun call(method: String, params: JsonObject, onProgress: (PhpProgress) -> Unit): JsonObject =
        withContext(Dispatchers.IO) {
            lock.withLock {
                coroutineScope {
                    fun drain() {
                        progress().lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            runCatching { json.decodeFromString(PhpProgress.serializer(), line) }.getOrNull()?.let(onProgress)
                        }
                    }
                    val poller = launch { while (isActive) { delay(200); drain() } }
                    try {
                        val answer = memScoped {
                            val out = OpenFluxPhpCall(method.cstr.ptr, params.toString().cstr.ptr)
                                ?: throw PhpHostingException("bad_params", method, "the core did not answer")
                            try { out.toKString() } finally { OpenFluxFreeString(out) }
                        }
                        runCatching { json.parseToJsonElement(answer).jsonObject }
                            .getOrElse { throw PhpHostingException("bad_params", method, "the core's answer was not JSON") }
                    } finally {
                        poller.cancel()
                        drain()
                    }
                }
            }
        }

    override fun close() = OpenFluxPhpCancel()
}

/**
 * The Swift UI's profiles, brought over once: Swift leaves each as an
 * `openflux://` link (its own ShareLink.encode, secret included) under
 * "kmp.migrate" before it first shows this UI; they are read like any imported
 * link. MAX profiles have no link and stay behind.
 */
object SwiftProfileMigration {
    @Serializable
    private data class Entry(val name: String, val link: String, val selected: Boolean = false)

    private const val PENDING = "kmp.migrate"
    private const val DONE = "kmp.migrated"

    suspend fun run(profiles: ProfileRepository, codec: CoreShareLinkCodec, now: Long, select: (String) -> Unit) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.boolForKey(DONE)) return
        val raw = defaults.stringForKey(PENDING) ?: return
        val entries = runCatching { Json.decodeFromString(ListSerializer(Entry.serializer()), raw) }.getOrDefault(emptyList())
        val known = profiles.profiles.value.map { it.name }.toSet()
        for (e in entries) {
            val config = runCatching { codec.decode(e.link) }.getOrNull() ?: continue
            val made = runCatching { Profile.fromShare(config, profiles.newId(), now, ProfileSource.Link) }.getOrNull() ?: continue
            val p = made.copy(name = e.name.ifBlank { made.name })
            if (p.name in known) continue
            profiles.upsert(p)
            if (e.selected) select(p.id)
        }
        defaults.setBool(true, DONE)
        defaults.removeObjectForKey(PENDING)
    }
}
