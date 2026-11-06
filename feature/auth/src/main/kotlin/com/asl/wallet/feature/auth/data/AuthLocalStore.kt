package com.asl.wallet.feature.auth.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asl.wallet.core.security.storage.StringCipher
import com.asl.wallet.feature.auth.domain.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authStore by preferencesDataStore("auth_local")

/**
 * Small local state for sign-in:
 * - cached profile (encrypted) so Unlock and Home can greet the user offline,
 * - last login ID (encrypted) to prefill Login,
 * - first-launch flags (plain).
 */
interface AuthLocalStore {
    suspend fun cachedUser(): User?
    suspend fun saveUser(user: User)
    suspend fun lastLoginId(): String?
    suspend fun saveLastLoginId(id: String)
    suspend fun isLanguageChosen(): Boolean
    suspend fun setLanguageChosen()
    suspend fun isAppLockSetupDone(): Boolean
    suspend fun setAppLockSetupDone()
    /** Removes the profile. Keeps last login ID and flags so the next sign-in is quick. */
    suspend fun clearSession()
}

@Singleton
class DataStoreAuthLocalStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: StringCipher,
    private val json: Json,
) : AuthLocalStore {
    private val userKey = stringPreferencesKey("user")
    private val lastIdKey = stringPreferencesKey("last_id")
    private val langKey = booleanPreferencesKey("language_chosen")
    private val lockKey = booleanPreferencesKey("app_lock_setup")
    private val ds get() = context.authStore

    override suspend fun cachedUser(): User? = ds.data.first()[userKey]?.let { enc ->
        runCatching { json.decodeFromString(User.serializer(), cipher.decrypt(enc)) }.getOrNull()
    }

    override suspend fun saveUser(user: User) {
        ds.edit { it[userKey] = cipher.encrypt(json.encodeToString(User.serializer(), user)) }
    }

    override suspend fun lastLoginId(): String? = ds.data.map { it[lastIdKey] }.first()?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
    override suspend fun saveLastLoginId(id: String) { ds.edit { it[lastIdKey] = cipher.encrypt(id) } }
    override suspend fun isLanguageChosen() = ds.data.first()[langKey] == true
    override suspend fun setLanguageChosen() { ds.edit { it[langKey] = true } }
    override suspend fun isAppLockSetupDone() = ds.data.first()[lockKey] == true
    override suspend fun setAppLockSetupDone() { ds.edit { it[lockKey] = true } }
    override suspend fun clearSession() { ds.edit { it.remove(userKey) } }
}
