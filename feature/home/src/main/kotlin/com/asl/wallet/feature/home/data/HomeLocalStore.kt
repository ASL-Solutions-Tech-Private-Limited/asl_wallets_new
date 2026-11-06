package com.asl.wallet.feature.home.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.asl.wallet.core.security.storage.StringCipher
import com.asl.wallet.feature.home.domain.HomeSnapshot
import com.asl.wallet.feature.home.domain.LedgerEntry
import com.asl.wallet.feature.home.domain.WalletSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home's small local state:
 * - last good balance + last 3 ledger rows, **encrypted** (shown offline and on cold start; wiped on sign-out),
 * - pinned services per registration number (plain keys like "aeps"; not personal data).
 */
interface HomeLocalStore {
    suspend fun snapshot(): HomeSnapshot?
    suspend fun saveWallet(wallet: WalletSummary)
    suspend fun saveRecent(recent: List<LedgerEntry>)
    fun pins(regno: Long): Flow<List<String>?>
    suspend fun savePins(regno: Long, keys: List<String>)
    suspend fun clearCache()
}

@Singleton
class DataStoreHomeLocalStore @Inject constructor(
    @HomeDataStore private val ds: DataStore<Preferences>,
    private val cipher: StringCipher,
    private val json: Json,
) : HomeLocalStore {
    private val snapshotKey = stringPreferencesKey("snapshot")
    private fun pinsKey(regno: Long) = stringPreferencesKey("pins_$regno")

    override suspend fun snapshot(): HomeSnapshot? = read()?.toDomain()

    override suspend fun saveWallet(wallet: WalletSummary) = write { (it ?: CacheDto()).copy(wallet = WalletDto.of(wallet)) }

    override suspend fun saveRecent(recent: List<LedgerEntry>) = write { (it ?: CacheDto()).copy(recent = recent.map(LedgerCacheDto::of)) }

    override fun pins(regno: Long): Flow<List<String>?> = ds.data.map { p ->
        p[pinsKey(regno)]?.split(',')?.map(String::trim)?.filter { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
    }

    override suspend fun savePins(regno: Long, keys: List<String>) {
        ds.edit { it[pinsKey(regno)] = keys.filter { k -> k.isNotEmpty() && k.all(Char::isLetterOrDigit) }.joinToString(",") }
    }

    override suspend fun clearCache() {
        ds.edit { it.remove(snapshotKey) }
    }

    private suspend fun read(): CacheDto? = ds.data.first()[snapshotKey]?.let { enc ->
        runCatching { json.decodeFromString(CacheDto.serializer(), cipher.decrypt(enc)) }.getOrNull()
    }

    private suspend fun write(update: (CacheDto?) -> CacheDto) {
        val next = update(read())
        val enc = cipher.encrypt(json.encodeToString(CacheDto.serializer(), next))
        ds.edit { it[snapshotKey] = enc }
    }
}

@Serializable
internal data class CacheDto(val wallet: WalletDto? = null, val recent: List<LedgerCacheDto> = emptyList()) {
    fun toDomain() = HomeSnapshot(wallet?.toDomain(), recent.mapNotNull { it.toDomain() })
}

@Serializable
internal data class WalletDto(val balance: String, val commission: String, val updatedAt: Long) {
    fun toDomain() = runCatching { WalletSummary(balance.toBigDecimal(), commission.toBigDecimal(), updatedAt) }.getOrNull()
    companion object { fun of(w: WalletSummary) = WalletDto(w.balance.toPlainString(), w.commission.toPlainString(), w.updatedAt) }
}

@Serializable
internal data class LedgerCacheDto(val id: Long, val title: String, val date: String, val time: String, val amount: String, val closing: String? = null) {
    fun toDomain() = runCatching { LedgerEntry(id, title, date, time, amount.toBigDecimal(), closing?.toBigDecimal()) }.getOrNull()
    companion object { fun of(e: LedgerEntry) = LedgerCacheDto(e.id, e.title, e.date, e.time, e.amount.toPlainString(), e.closing?.toPlainString()) }
}
