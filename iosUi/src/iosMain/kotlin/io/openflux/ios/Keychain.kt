package io.openflux.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The encryption secret where the tunnel extension reads it: the same item the
 * Swift UI writes (Secrets.setEncryptionKey) — service "openflux.encryption",
 * account the profile's UUID, the Keychain group app and extension share. The
 * VPN profile's providerConfiguration carries only the id, never the secret.
 */
@OptIn(ExperimentalForeignApi::class)
object SharedKeychain {
    private const val SERVICE = "openflux.encryption"
    private const val GROUP = "8GQH8GQ252.com.p1neapplexpress-saharev.openflux"

    fun setSecret(profileUuid: String, secret: String): Boolean = set(SERVICE, profileUuid, secret)

    /** The text of the shared item [service]/[account]; null when there is none. */
    fun get(service: String, account: String): String? {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val owned = listOf(CFBridgingRetain(service), CFBridgingRetain(account), CFBridgingRetain(GROUP))
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, owned[0])
        CFDictionaryAddValue(query, kSecAttrAccount, owned[1])
        CFDictionaryAddValue(query, kSecAttrAccessGroup, owned[2])
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val text = memScoped {
            val out = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query, out.ptr) != 0) null
            else (CFBridgingRelease(out.value) as? NSData)?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
        }
        owned.forEach { CFRelease(it) }
        CFRelease(query)
        return text
    }

    /** Replaces the shared item [service]/[account] with [text]; "" deletes it. */
    fun set(service: String, account: String, text: String): Boolean {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val owned = listOf(CFBridgingRetain(service), CFBridgingRetain(account), CFBridgingRetain(GROUP))
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, owned[0])
        CFDictionaryAddValue(query, kSecAttrAccount, owned[1])
        CFDictionaryAddValue(query, kSecAttrAccessGroup, owned[2])
        SecItemDelete(query)
        val trimmed = text.trim()
        var ok = true
        if (trimmed.isNotEmpty()) {
            val data = NSString.create(string = trimmed).dataUsingEncoding(NSUTF8StringEncoding)
            val dataRef = CFBridgingRetain(data)
            CFDictionaryAddValue(query, kSecValueData, dataRef)
            // The extension reconnects on its own after a network change, maybe locked.
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            ok = SecItemAdd(query, null) == 0
            CFRelease(dataRef)
        }
        owned.forEach { CFRelease(it) }
        CFRelease(query)
        return ok
    }

    /**
     * The UUID the extension looks the secret up by. A shared profile's id is a
     * UUID when this app made it; one that is not (made elsewhere) gets a UUID
     * of its own, remembered so the same profile always maps to the same item.
     */
    fun uuidFor(profileId: String): String {
        NSUUID(uUIDString = profileId).let { u -> if (u.UUIDString().equals(profileId, ignoreCase = true)) return u.UUIDString() }
        val defaults = NSUserDefaults.standardUserDefaults
        val key = "kmp.uuid.$profileId"
        defaults.stringForKey(key)?.let { return it }
        return NSUUID().UUIDString().also { defaults.setObject(it, key) }
    }
}
