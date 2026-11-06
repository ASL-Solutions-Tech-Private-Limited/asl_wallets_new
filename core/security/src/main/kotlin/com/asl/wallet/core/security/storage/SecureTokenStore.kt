package com.asl.wallet.core.security.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.asl.wallet.core.common.session.SessionTokens
import com.asl.wallet.core.common.session.TokenStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Base64

/** Encrypts and decrypts small strings. Production uses Tink AES-256-GCM with a key that lives in the Android Keystore. */
interface StringCipher {
    fun encrypt(plain: String): String
    fun decrypt(cipher: String): String
}

class TinkCipher(context: Context) : StringCipher {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plain: String): String =
        Base64.encodeToString(aead.encrypt(plain.toByteArray(Charsets.UTF_8), AAD), Base64.NO_WRAP)

    override fun decrypt(cipher: String): String =
        String(aead.decrypt(Base64.decode(cipher, Base64.NO_WRAP), AAD), Charsets.UTF_8)

    private companion object {
        const val KEYSET_NAME = "asl_session_keyset"
        const val KEYSET_PREFS = "asl_session_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://asl_session_master_key"
        val AAD = "asl-session-v1".toByteArray()
    }
}

@Serializable
private data class StoredTokens(val a: String, val r: String)

/**
 * [TokenStore] backed by DataStore, with every value encrypted by [StringCipher].
 * A cached copy is kept in memory so the network layer doesn't hit disk on every request.
 * If decryption ever fails (key lost after an OS reset), the session is cleared: the user simply signs in again.
 */
class SecureTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: StringCipher,
    private val json: Json = Json,
) : TokenStore {

    private val key = stringPreferencesKey("session")
    private val mutex = Mutex()
    @Volatile private var cache: SessionTokens? = null
    @Volatile private var loaded = false

    override val isSignedIn: Flow<Boolean> = dataStore.data.map { it[key] != null }

    override suspend fun read(): SessionTokens? {
        if (loaded) return cache
        return mutex.withLock {
            if (!loaded) {
                val enc = dataStore.data.first()[key]
                cache = enc?.let { decode(it) }
                if (enc != null && cache == null) dataStore.edit { it.remove(key) }
                loaded = true
            }
            cache
        }
    }

    override suspend fun save(tokens: SessionTokens) = mutex.withLock {
        val plain = json.encodeToString(StoredTokens.serializer(), StoredTokens(tokens.accessToken, tokens.refreshToken))
        dataStore.edit { it[key] = cipher.encrypt(plain) }
        cache = tokens
        loaded = true
    }

    override suspend fun clear() = mutex.withLock {
        dataStore.edit { it.remove(key) }
        cache = null
        loaded = true
    }

    private fun decode(enc: String): SessionTokens? = runCatching {
        val s = json.decodeFromString(StoredTokens.serializer(), cipher.decrypt(enc))
        SessionTokens(s.a, s.r)
    }.getOrNull()
}
