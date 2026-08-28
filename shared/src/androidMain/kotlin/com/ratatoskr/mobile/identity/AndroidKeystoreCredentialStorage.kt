package com.ratatoskr.mobile.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreCredentialStorage(
    context: Context,
    namespace: String = DEFAULT_NAMESPACE,
) : SecureCredentialStorage {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            preferencesName(namespace.requireValidNamespace()),
            Context.MODE_PRIVATE,
        )
    private val keyAlias = "com.ratatoskr.mobile.device-identity.$namespace"

    override fun load(): DeviceCredentials? {
        val record = preferences.getString(RECORD_KEY, null) ?: return null
        return secureStorageCall {
            val parts = record.split('.', limit = 3)
            require(parts.size == 3 && parts[0] == RECORD_VERSION)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(TAG_BITS, iv))
            CredentialRecordCodec.decode(cipher.doFinal(ciphertext))
        }
    }

    override fun save(credentials: DeviceCredentials) {
        secureStorageCall {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, existingOrNewKey())
            val ciphertext = cipher.doFinal(CredentialRecordCodec.encode(credentials))
            val record =
                listOf(
                    RECORD_VERSION,
                    Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                    Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                ).joinToString(".")
            check(preferences.edit().putString(RECORD_KEY, record).commit())
        }
    }

    override fun clear() {
        secureStorageCall {
            check(preferences.edit().remove(RECORD_KEY).commit())
            val keyStore = keyStore()
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        }
    }

    private fun existingOrNewKey(): SecretKey =
        runCatching { existingKey() }.getOrElse {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            generator.init(
                KeyGenParameterSpec
                    .Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generator.generateKey()
        }

    private fun existingKey(): SecretKey = keyStore().getKey(keyAlias, null) as? SecretKey ?: error("missing secure-storage key")

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun String.requireValidNamespace(): String =
        apply {
            require(matches(Regex("[A-Za-z0-9._-]{1,64}")))
        }

    private inline fun <T> secureStorageCall(block: () -> T): T =
        try {
            block()
        } catch (_: SecureCredentialStorageException) {
            throw SecureCredentialStorageException()
        } catch (_: Throwable) {
            throw SecureCredentialStorageException()
        }

    internal companion object {
        const val DEFAULT_NAMESPACE = "production"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val RECORD_KEY = "encrypted-device-credentials"
        private const val RECORD_VERSION = "1"
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun preferencesName(namespace: String) = "com.ratatoskr.mobile.credentials.$namespace"
    }
}
