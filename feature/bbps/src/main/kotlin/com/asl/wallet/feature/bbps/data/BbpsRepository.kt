package com.asl.wallet.feature.bbps.data

import com.asl.wallet.core.common.session.SignOutCleanup
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.core.network.safeServerMessage
import com.asl.wallet.feature.bbps.domain.BbpsOutcome
import com.asl.wallet.feature.bbps.domain.BbpsReceipt
import com.asl.wallet.feature.bbps.domain.BbpsRules
import com.asl.wallet.feature.bbps.domain.BillCategory
import com.asl.wallet.feature.bbps.domain.BillPayment
import com.asl.wallet.feature.bbps.domain.Biller
import com.asl.wallet.feature.bbps.domain.BillerInfo
import com.asl.wallet.feature.bbps.domain.BillerParam
import com.asl.wallet.feature.bbps.domain.FetchedBill
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface BbpsRepository {
    suspend fun categories(): NetworkResult<List<BillCategory>>
    suspend fun billers(category: String): NetworkResult<List<Biller>>
    suspend fun billerInfo(biller: Biller): NetworkResult<BillerInfo>
    suspend fun fetchBill(biller: Biller, inputs: List<Pair<BillerParam, String>>): NetworkResult<FetchedBill>
    /** Never throws and never retries: a timeout or 5xx is [BbpsOutcome.Pending]. */
    suspend fun pay(p: BillPayment): BbpsOutcome
    suspend fun balance(): NetworkResult<BigDecimal>
}

@Singleton
class BbpsRepositoryImpl @Inject constructor(private val api: BbpsApi, private val json: Json) : BbpsRepository {
    // Categories, billers and biller rules rarely change: kept for the session (cleared on sign-out).
    @Volatile private var categoryCache: List<BillCategory>? = null
    private val billerCache = HashMap<String, List<Biller>>()
    private val infoCache = HashMap<String, BillerInfo>()

    /** A 200 whose inner success/status is false becomes an API failure with the server's message. */
    private fun <T> NetworkResult<JsonElement>.read(parse: (JsonElement) -> T?): NetworkResult<T> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> innerRefusal(data)?.let { NetworkResult.Failure.Api(422, "INNER_STATUS", safeServerMessage(it) ?: it) }
            ?: parse(data)?.let { NetworkResult.Success(it, message) }
            ?: NetworkResult.Failure.Unexpected("Unreadable BBPS answer")
    }

    override suspend fun categories(): NetworkResult<List<BillCategory>> {
        categoryCache?.let { return NetworkResult.Success(it, null) }
        val r = safeApiCall(json) { api.categories() }.read { readCategories(it) }
        if (r is NetworkResult.Success && r.data.isNotEmpty()) categoryCache = r.data
        // Live UAT (10 Nov 2026) answers an empty list while get-biller works: show the old app's categories instead.
        return if (r is NetworkResult.Success && r.data.isEmpty()) NetworkResult.Success(BbpsRules.DEFAULT_CATEGORIES, r.message) else r
    }

    override suspend fun billers(category: String): NetworkResult<List<Biller>> {
        synchronized(billerCache) { billerCache[category.lowercase()] }?.let { return NetworkResult.Success(it, null) }
        return safeApiCall(json) { api.billers(BillersRequest(category)) }.read { readBillers(it, category) }
            .also { r -> if (r is NetworkResult.Success && r.data.isNotEmpty()) synchronized(billerCache) { billerCache[category.lowercase()] = r.data } }
    }

    override suspend fun billerInfo(biller: Biller): NetworkResult<BillerInfo> {
        synchronized(infoCache) { infoCache[biller.code] }?.let { return NetworkResult.Success(it, null) }
        return safeApiCall(json) { api.billerDetail(BillerDetailRequest(biller.code)) }.read { readBillerInfo(it, biller)?.takeIf { i -> i.params.isNotEmpty() } }
            .also { r -> if (r is NetworkResult.Success) synchronized(infoCache) { infoCache[biller.code] = r.data } }
    }

    override suspend fun fetchBill(biller: Biller, inputs: List<Pair<BillerParam, String>>): NetworkResult<FetchedBill> {
        val post = inputs.filter { it.second.isNotBlank() }.associate { (p, v) -> p.name to v.trim() }
        return safeApiCall(json) { api.fetchBill(FetchBillRequest(biller.code, biller.category, post)) }.read(::readBill)
    }

    override suspend fun pay(p: BillPayment): BbpsOutcome {
        // Same fields as the Postman sample; requestId and the xml are exactly what fetch-bill returned.
        val body = buildJsonObject {
            put("membertype", 1)
            put("category", p.biller.category)
            put("operator", p.biller.code)
            put("ca_number", p.consumerNumber)
            put("Consumer_ID", p.consumerNumber)
            put("Mobile_Number", p.customerMobile)
            put("amount", JsonPrimitive(p.amount))
            put("mode", "Online")
            put("requestId", p.bill.requestId)
            put("billPaymentRequestxml", p.bill.xml)
        }
        val r = safeApiCall(json) { api.payBill(p.key.value, body) }
        fun receipt(ref: String? = null, bbps: String? = null) = BbpsReceipt(
            p.biller.category, p.biller.name, p.inputs.firstOrNull()?.first?.name ?: "Consumer number", p.consumerNumber,
            p.bill.customerName, p.bill.billNumber, p.amount, ref, bbps, "",
        )
        return when (r) {
            is NetworkResult.Success -> {
                val a = readPay(r.data)
                val rec = receipt(a.refId, a.bbpsTxnId)
                val refused = if (a.status == null) innerRefusal(r.data) else null
                when {
                    refused != null -> BbpsOutcome.Failed(rec, safeServerMessage(refused), debited = true)
                    else -> when (BbpsRules.statusOf(a.status)) {
                        BbpsRules.Status.Success -> BbpsOutcome.Paid(rec, a.balanceAfter)
                        BbpsRules.Status.Failed -> BbpsOutcome.Failed(rec, a.message?.let(::safeServerMessage) ?: r.message?.let(::safeServerMessage), debited = true)
                        BbpsRules.Status.Pending -> BbpsOutcome.Pending(rec)
                    }
                }
            }
            NetworkResult.Failure.Timeout, NetworkResult.Failure.NoInternet -> BbpsOutcome.Pending(receipt())
            is NetworkResult.Failure.Api -> if (r.httpCode >= 500) BbpsOutcome.Pending(receipt()) else BbpsOutcome.Failed(receipt(), safeServerMessage(r.message), debited = false)
            is NetworkResult.Failure -> BbpsOutcome.Failed(receipt(), null, debited = false)
        }
    }

    override suspend fun balance(): NetworkResult<BigDecimal> = when (val r = safeApiCall(json) { api.accountInfo() }) {
        is NetworkResult.Success -> (r.data.availableBalanceAmt ?: r.data.available_balance_amt)?.contentOrNull?.replace(",", "")?.trim()?.toBigDecimalOrNull()
            ?.let { NetworkResult.Success(it, null) } ?: NetworkResult.Failure.Unexpected("Balance missing")
        is NetworkResult.Failure -> r
    }

    fun clear() {
        categoryCache = null
        synchronized(billerCache) { billerCache.clear() }
        synchronized(infoCache) { infoCache.clear() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BbpsModule {
    @Binds @Singleton abstract fun repository(impl: BbpsRepositoryImpl): BbpsRepository

    companion object {
        /** Billers can be slow: same client (pinning, auth, no auto-retry), longer timeouts. */
        @Provides @Singleton
        fun bbpsApi(retrofit: Retrofit, client: OkHttpClient): BbpsApi = retrofit.newBuilder()
            .client(client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build())
            .build().create(BbpsApi::class.java)

        @Provides @IntoSet
        fun bbpsCleanup(repo: BbpsRepositoryImpl): SignOutCleanup = SignOutCleanup { repo.clear() }
    }
}
