package io.openflux.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import io.openflux.desktop.service.AppContainer
import io.openflux.desktop.ui.NoScrollbars
import io.openflux.desktop.ui.Shortcuts
import io.openflux.desktop.ui.shell.OpenFluxApp
import platform.UIKit.UIViewController

/**
 * The shared OpenFlux UI for the iOS app. Swift presents this controller; the
 * VPN itself stays in the Swift packet-tunnel extension.
 */
fun MainViewController(): UIViewController {
    val container = AppContainer(
        profiles = InMemoryProfiles(),
        settings = InMemorySettings(),
        connection = IdleConnection(),
        platform = IosPlatformServices(),
        shareCodec = NoShareCodec,
        nodeWizard = NoNodeWizard(),
    )
    return ComposeUIViewController {
        OpenFluxApp(container, NoScrollbars, remember { Shortcuts() })
    }
}
