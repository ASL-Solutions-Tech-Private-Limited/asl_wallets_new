package com.asl.wallet.feature.home.data

import com.asl.wallet.core.network.model.serviceRows
import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.feature.home.domain.HomeService
import com.asl.wallet.feature.home.domain.HomeSnapshot
import com.asl.wallet.feature.home.domain.LedgerEntry
import com.asl.wallet.feature.home.domain.ServiceCatalogue
import com.asl.wallet.feature.home.domain.WalletSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

fun interface WallClock { fun nowMillis(): Long }

data class ServicesResult(val services: List<HomeService>, val fromServer: Boolean)

interface HomeRepository {
    suspend fun cached(): HomeSnapshot?
    suspend fun wallet(): NetworkResult<WalletSummary>
    suspend fun recent(): NetworkResult<List<LedgerEntry>>
    /** Never fails: falls back to the built-in list when the server can't give one. */
    suspend fun services(): ServicesResult
    fun pins(regno: Long): Flow<List<String>?>
    suspend fun savePins(regno: Long, keys: List<String>)
    fun sections(regno: Long): Flow<List<String>?>
    suspend fun saveSections(regno: Long, keys: List<String>)
}

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val api: HomeApi,
    private val local: HomeLocalStore,
    private val json: Json,
    private val clock: WallClock,
) : HomeRepository {

    /** Last good server list for this process, so a later failure doesn't swap tiles under the user. */
    @Volatile private var lastServices: List<HomeService>? = null

    override suspend fun cached(): HomeSnapshot? = local.snapshot()

    override suspend fun wallet(): NetworkResult<WalletSummary> {
        val r = safeApiCall(json) { api.accountInfo() }
        return when (r) {
            is NetworkResult.Success -> {
                val d = r.data
                val balance = (d.availableBalanceAmt.amount() ?: d.available_balance_amt.amount())
                    ?: return NetworkResult.Failure.Unexpected("Balance missing")
                val commission = d.commissionReceived.amount() ?: d.commission_received.amount() ?: BigDecimal.ZERO
                val w = WalletSummary(balance, commission, clock.nowMillis())
                local.saveWallet(w)
                NetworkResult.Success(w, r.message)
            }
            is NetworkResult.Failure -> r
        }
    }

    override suspend fun recent(): NetworkResult<List<LedgerEntry>> {
        val r = safeApiCall(json) { api.ledger(page = 1, limit = RECENT_COUNT) }
        return when (r) {
            is NetworkResult.Success -> {
                val list = r.data.mapNotNull { it.toEntry() }.take(RECENT_COUNT)
                local.saveRecent(list)
                NetworkResult.Success(list, r.message)
            }
            is NetworkResult.Failure -> r
        }
    }

    override suspend fun services(): ServicesResult {
        val r = safeApiCall(json) { api.services() }
        if (r is NetworkResult.Success) {
            val rows = serviceRows(r.data).mapNotNull { runCatching { json.decodeFromJsonElement(ServiceDto.serializer(), it) }.getOrNull() }
            val list = ServiceCatalogue.fromServer(rows).takeIf { it.isNotEmpty() }?.let(ServiceCatalogue::withWalletServices).orEmpty()
            if (list.isNotEmpty()) { lastServices = list; return ServicesResult(list, fromServer = true) }
        }
        lastServices?.let { return ServicesResult(it, fromServer = true) }
        return ServicesResult(ServiceCatalogue.fallback(), fromServer = false)
    }

    override fun pins(regno: Long) = local.pins(regno)
    override suspend fun savePins(regno: Long, keys: List<String>) = local.savePins(regno, keys)
    override fun sections(regno: Long) = local.sections(regno)
    override suspend fun saveSections(regno: Long, keys: List<String>) = local.saveSections(regno, keys)

    companion object { const val RECENT_COUNT = 3 }
}

internal fun LedgerDto.toEntry(): LedgerEntry? {
    val id = id ?: return null
    val credit = creditAmt.amount() ?: BigDecimal.ZERO
    val debit = debitAmt.amount() ?: BigDecimal.ZERO
    return LedgerEntry(
        id = id,
        title = maskLongNumbers(transdesc?.trim()?.ifEmpty { null } ?: service?.trim()?.ifEmpty { null } ?: "Transaction"),
        date = transDate?.trim().orEmpty(),
        time = transTime?.trim().orEmpty(),
        amount = if (debit.signum() > 0) debit.negate() else credit,
        closing = closingBal.amount(),
    )
}

private val LONG_NUMBER = Regex("""\d[\d ]{7,22}\d""")

/**
 * The server puts account/Aadhaar/card numbers inside ledger descriptions
 * (e.g. "CHARGES FOR VERIFY (A/C : 0185104000179539)"). Any run of 9+ digits is shown as ••••last4,
 * before it is displayed or cached. Short numbers (amounts, dates, ref nos ≤ 8 digits) are left alone.
 */
internal fun maskLongNumbers(text: String): String = LONG_NUMBER.replace(text) { m ->
    val digits = m.value.filter(Char::isDigit)
    if (digits.length >= 9) Masking.account(digits) else m.value
}
