package io.openflux.ios

import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.Profile
import io.openflux.desktop.service.ProfileRepository
import io.openflux.desktop.service.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/**
 * Profiles of the shared UI, as JSON in the app's defaults. A profile's secret
 * is part of the model (as on Android and the desktop); the tunnel extension
 * gets it from the shared Keychain when it starts, never from these defaults.
 */
class DefaultsProfiles(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : ProfileRepository {
    private val key = "kmp.profiles"
    private val _profiles = MutableStateFlow(load())
    override val profiles: StateFlow<List<Profile>> = _profiles

    private fun load(): List<Profile> =
        defaults.stringForKey(key)?.let { runCatching { storeJson.decodeFromString(ListSerializer(Profile.serializer()), it) }.getOrNull() }
            ?: emptyList()

    private fun save(list: List<Profile>) {
        _profiles.value = list
        defaults.setObject(storeJson.encodeToString(ListSerializer(Profile.serializer()), list), key)
    }

    override fun upsert(profile: Profile) {
        val list = _profiles.value
        save(if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it } else list + profile)
    }

    override fun delete(id: String) = save(_profiles.value.filterNot { it.id == id })

    override fun newId(): String = NSUUID().UUIDString()
}

class DefaultsSettings(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : SettingsRepository {
    private val key = "kmp.settings"
    private val _settings = MutableStateFlow(
        defaults.stringForKey(key)?.let { runCatching { storeJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings(),
    )
    override val settings: StateFlow<AppSettings> = _settings

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        defaults.setObject(storeJson.encodeToString(AppSettings.serializer(), next), key)
    }
}
