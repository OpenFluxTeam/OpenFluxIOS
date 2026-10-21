package io.openflux.ios

import io.openflux.desktop.model.isActive
import io.openflux.desktop.service.AppContainer
import platform.UIKit.UIPasteboard

/**
 * A test remote for the developer's own phone: Darwin notifications posted from
 * a Mac (`xcrun devicectl device notification post --name io.openflux.remote.<cmd>`)
 * drive the app where nobody can tap — connect, disconnect, the next profile, the
 * journal onto the pasteboard (which devicectl can read back). Swift listens for
 * them (KotlinHooks.swift) and hands the command here. Off unless «Режим
 * разработчика» is on in Settings: any app could post these names.
 */
object IosRemote {
    private var container: AppContainer? = null

    fun attach(c: AppContainer) { container = c }

    /** Runs [command] ("connect", "disconnect", "next", "copylog"); false when the remote is off or the command unknown. */
    fun handle(command: String): Boolean {
        val c = container ?: return false
        if (!c.settings.settings.value.developerMode) return false
        val profiles = c.profiles.profiles.value
        val selected = profiles.firstOrNull { it.id == c.settings.settings.value.selectedProfileId } ?: profiles.firstOrNull()
        when (command) {
            "connect" -> selected?.let { c.connection.connect(it, null) } ?: return false
            "disconnect" -> if (c.connection.state.value.isActive) c.connection.disconnect()
            "next" -> {
                if (profiles.isEmpty()) return false
                val i = profiles.indexOfFirst { it.id == selected?.id }
                val next = profiles[(i + 1) % profiles.size]
                c.settings.update { it.copy(selectedProfileId = next.id) }
            }
            "copylog" -> {
                val head = "state=${c.connection.state.value::class.simpleName} profile=${selected?.name} " +
                    "traffic=${c.connection.traffic.value} exit=${c.connection.exitAddress.value}"
                UIPasteboard.generalPasteboard.string =
                    (listOf(head) + c.connection.logs.value.takeLast(400).map { it.text }).joinToString("\n")
            }
            else -> return false
        }
        return true
    }
}
