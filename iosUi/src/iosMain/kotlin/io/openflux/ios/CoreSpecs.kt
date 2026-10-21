package io.openflux.ios

import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.TransportType
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A Session profile in the form OpenFluxStartSessionPacketTunnel takes: the
 * Android app's CoreSpecs.session for a client (io.openflux.android.core), so
 * a profile reaches the same exit the same way on both. Script carriers are not
 * offered on iOS yet.
 */
internal object IosCoreSpecs {
    fun session(profile: Profile): String {
        val transports = buildJsonArray {
            for (spec in profile.sessionSpecs()) {
                add(buildJsonObject {
                    put("name", spec.name)
                    if (spec.network.cli.isNotEmpty()) put("network", spec.network.cli)
                    put("type", spec.type.cliName)
                    put("url", if (spec.type == TransportType.DIRECT || spec.type == TransportType.ONEME) "" else spec.value)
                    put("priority", spec.priority)
                    put("params", buildJsonObject {
                        when (spec.type) {
                            TransportType.DIRECT -> put("dial", spec.value)
                            TransportType.ONEME -> {
                                put("token", spec.value)
                                put("uid", spec.uid)
                            }
                            // One lane: three take ~29 MB of the extension's 50, and a key
                            // derivation on top (32 MB for a moment) got it killed (build 99).
                            TransportType.TELEMOST -> {
                                put("stripes", "1")
                                put("stripes_max", "1")
                            }
                            else -> Unit
                        }
                    })
                })
            }
        }
        if (profile.context.isBlank()) return transports.toString()
        return buildJsonObject {
            put("context", profile.context)
            put("transports", transports)
        }.toString()
    }
}
