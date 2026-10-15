package io.openflux.ios

import io.openflux.desktop.model.CoreLinks
import io.openflux.ios.core.OpenFluxFreeString
import io.openflux.ios.core.OpenFluxShareDecode
import io.openflux.ios.core.OpenFluxShareEncode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * The core's link reading and making, in the app's own copy of the Go core
 * (liboflux, the C API the Swift UI used): share.Result JSON both ways, as
 * Android's Mobile.readShareLink / makeShareLink give it.
 */
@OptIn(ExperimentalForeignApi::class)
object IosCoreLinks : CoreLinks {
    override suspend fun read(link: String): String = withContext(Dispatchers.IO) { call(link) { OpenFluxShareDecode(it) } }

    override suspend fun make(configJson: String): String = withContext(Dispatchers.IO) { call(configJson) { OpenFluxShareEncode(it) } }

    private inline fun call(arg: String, f: (kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>) -> kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>?): String =
        memScoped {
            val out = f(arg.cstr.ptr) ?: return@memScoped """{"error":"ядро не ответило"}"""
            try {
                out.toKString()
            } finally {
                OpenFluxFreeString(out)
            }
        }
}
