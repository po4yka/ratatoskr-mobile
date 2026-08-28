package com.ratatoskr.mobile.identity

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
class IosKeychainCredentialStorage(
    private val service: String = "com.ratatoskr.mobile.device-identity",
    private val account: String = "device-credentials",
) : SecureCredentialStorage {
    @Throws(SecureCredentialStorageException::class)
    override fun load(): DeviceCredentials? =
        withQuery { query ->
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                when (status) {
                    errSecItemNotFound -> null
                    errSecSuccess -> {
                        val data = result.value ?: throw SecureCredentialStorageException()
                        try {
                            val length = CFDataGetLength(data.reinterpret())
                            val bytes =
                                CFDataGetBytePtr(data.reinterpret())
                                    ?: throw SecureCredentialStorageException()
                            CredentialRecordCodec.decode(bytes.readBytes(length.toInt()))
                        } finally {
                            CFRelease(data)
                        }
                    }
                    else -> throw SecureCredentialStorageException("load", status)
                }
            }
        }

    @Throws(SecureCredentialStorageException::class)
    override fun save(credentials: DeviceCredentials) {
        val encoded = CredentialRecordCodec.encode(credentials)
        encoded.usePinned { pinned ->
            val data =
                CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), encoded.size.convert())
                    ?: throw SecureCredentialStorageException()
            try {
                withQuery { query ->
                    val update = newDictionary()
                    try {
                        CFDictionarySetValue(update, kSecValueData, data)
                        val updateStatus = SecItemUpdate(query, update)
                        if (updateStatus == errSecSuccess) return@withQuery
                        if (updateStatus != errSecItemNotFound) {
                            throw SecureCredentialStorageException("update", updateStatus)
                        }

                        CFDictionarySetValue(query, kSecValueData, data)
                        CFDictionarySetValue(
                            query,
                            kSecAttrAccessible,
                            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        )
                        val addStatus = SecItemAdd(query, null)
                        if (addStatus != errSecSuccess && addStatus != errSecDuplicateItem) {
                            throw SecureCredentialStorageException("add", addStatus)
                        }
                        if (addStatus == errSecDuplicateItem) {
                            val duplicateUpdateStatus = SecItemUpdate(query, update)
                            if (duplicateUpdateStatus != errSecSuccess) {
                                throw SecureCredentialStorageException("duplicate update", duplicateUpdateStatus)
                            }
                        }
                    } finally {
                        CFRelease(update)
                    }
                }
            } finally {
                CFRelease(data)
            }
        }
    }

    @Throws(SecureCredentialStorageException::class)
    override fun clear() {
        withQuery { query ->
            val status = SecItemDelete(query)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw SecureCredentialStorageException("clear", status)
            }
        }
    }

    internal fun policy(): IosKeychainPolicy =
        withQuery { query ->
            CFDictionarySetValue(query, kSecReturnAttributes, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) {
                    throw SecureCredentialStorageException()
                }
                val attributes = result.value ?: throw SecureCredentialStorageException()
                try {
                    val accessible =
                        platform.CoreFoundation.CFDictionaryGetValue(
                            attributes.reinterpret(),
                            kSecAttrAccessible,
                        )
                    val synchronizable =
                        platform.CoreFoundation.CFDictionaryGetValue(
                            attributes.reinterpret(),
                            kSecAttrSynchronizable,
                        )
                    IosKeychainPolicy(
                        deviceOnly = accessible == kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        synchronizing = synchronizable == kCFBooleanTrue,
                    )
                } finally {
                    CFRelease(attributes)
                }
            }
        }

    private inline fun <T> withQuery(block: (platform.CoreFoundation.CFMutableDictionaryRef) -> T): T {
        val query = newDictionary()
        val serviceValue =
            CFStringCreateWithCString(
                kCFAllocatorDefault,
                service,
                kCFStringEncodingUTF8,
            ) ?: throw SecureCredentialStorageException()
        val accountValue =
            CFStringCreateWithCString(
                kCFAllocatorDefault,
                account,
                kCFStringEncodingUTF8,
            ) ?: throw SecureCredentialStorageException()
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceValue)
            CFDictionarySetValue(query, kSecAttrAccount, accountValue)
            CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse)
            return block(query)
        } finally {
            CFRelease(serviceValue)
            CFRelease(accountValue)
            CFRelease(query)
        }
    }

    private fun newDictionary() =
        memScoped {
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            ) ?: throw SecureCredentialStorageException()
        }
}

internal data class IosKeychainPolicy(
    val deviceOnly: Boolean,
    val synchronizing: Boolean,
)
