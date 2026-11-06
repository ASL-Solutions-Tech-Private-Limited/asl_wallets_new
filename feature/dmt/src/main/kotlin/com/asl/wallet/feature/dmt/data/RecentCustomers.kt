package com.asl.wallet.feature.dmt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.asl.wallet.core.security.storage.StringCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DmtDataStore

/** A customer this retailer served recently. [last] is a short note like "₹ 100 to AJAY KUMAR". */
@Serializable
data class RecentCustomer(val mobile: String, val name: String, val at: Long, val last: String? = null)

/**
 * The retailer's last [MAX] DMT customers, so a returning customer isn't typed again (user, 5 Nov 2026).
 * The server has no customer-list API, so this lives on the phone: one Tink-encrypted (Android Keystore) entry per
 * retailer, wiped on every sign-out. Customer names and numbers are never stored in plain text.
 */
interface RecentCustomerStore {
    fun recent(regno: Long): Flow<List<RecentCustomer>>
    suspend fun remember(regno: Long, mobile: String, name: String, now: Long)
    suspend fun noteTransfer(regno: Long, mobile: String, note: String, now: Long)
    suspend fun clear(regno: Long)
    suspend fun clearAll()

    companion object { const val MAX = 10 }
}

class EncryptedRecentCustomerStore @Inject constructor(
    @DmtDataStore private val ds: DataStore<Preferences>,
    private val cipher: StringCipher,
    private val json: Json,
) : RecentCustomerStore {
    private val serializer = ListSerializer(RecentCustomer.serializer())
    private fun key(regno: Long) = stringPreferencesKey("recent_$regno")

    private fun decode(enc: String?): List<RecentCustomer> =
        enc?.let { runCatching { json.decodeFromString(serializer, cipher.decrypt(it)) }.getOrNull() }.orEmpty()

    override fun recent(regno: Long): Flow<List<RecentCustomer>> = ds.data.map { decode(it[key(regno)]) }

    private suspend fun update(regno: Long, f: (List<RecentCustomer>) -> List<RecentCustomer>) {
        ds.edit { p -> p[key(regno)] = cipher.encrypt(json.encodeToString(serializer, f(decode(p[key(regno)])))) }
    }

    override suspend fun remember(regno: Long, mobile: String, name: String, now: Long) = update(regno) { list ->
        val old = list.firstOrNull { it.mobile == mobile }
        (listOf(RecentCustomer(mobile, name.ifBlank { old?.name.orEmpty() }, now, old?.last)) + list.filter { it.mobile != mobile }).take(RecentCustomerStore.MAX)
    }

    override suspend fun noteTransfer(regno: Long, mobile: String, note: String, now: Long) = update(regno) { list ->
        list.map { if (it.mobile == mobile) it.copy(at = now, last = note) else it }.sortedByDescending { it.at }
    }

    override suspend fun clear(regno: Long) { ds.edit { it.remove(key(regno)) } }

    override suspend fun clearAll() { ds.edit { it.clear() } }
}
