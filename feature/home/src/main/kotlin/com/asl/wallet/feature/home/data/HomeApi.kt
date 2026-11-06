package com.asl.wallet.feature.home.data

import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigDecimal

/** Home endpoints (API sheet; shapes verified on UAT 3 Nov 2026). Read-only. */
interface HomeApi {
    @GET("merchant/my-wallet/account-info")
    suspend fun accountInfo(): Response<ApiEnvelope<AccountInfoDto>>

    @GET("merchant/services")
    suspend fun services(): Response<ApiEnvelope<List<ServiceDto>>>

    @GET("merchant/ledger-report")
    suspend fun ledger(@Query("page") page: Int = 1, @Query("limit") limit: Int = 3): Response<ApiEnvelope<List<LedgerDto>>>
}

/** The server sends both snake_case and camelCase, and amounts as "23.00" or 23. Both are accepted. */
@Serializable
data class AccountInfoDto(
    val available_balance_amt: JsonPrimitive? = null,
    val availableBalanceAmt: JsonPrimitive? = null,
    val commission_received: JsonPrimitive? = null,
    val commissionReceived: JsonPrimitive? = null,
    val regno: Long? = null,
)

@Serializable
data class ServiceDto(
    val id: Long? = null,
    val services_name: String? = null,
    val service_desc: String? = null,
    val services_link: String? = null,
    val service_icon_url: String? = null,
    val service_order: Int? = null,
    val isKycRequired: JsonPrimitive? = null,
    val status: JsonPrimitive? = null,
)

@Serializable
data class LedgerDto(
    val id: Long? = null,
    val transdesc: String? = null,
    val transDate: String? = null,
    val transTime: String? = null,
    val creditAmt: JsonPrimitive? = null,
    val debitAmt: JsonPrimitive? = null,
    val closingBal: JsonPrimitive? = null,
    val service: String? = null,
    val referenceId: String? = null,
)

/** "1,234.50", "23.00", 23 or null to BigDecimal. Anything unreadable becomes null, never a crash. */
internal fun JsonPrimitive?.amount(): BigDecimal? =
    this?.contentOrNull?.replace(",", "")?.trim()?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

/** true / 1 / "1" / "true" / "active" are on. */
internal fun JsonPrimitive?.flag(): Boolean? {
    if (this == null) return null
    booleanOrNull?.let { return it }
    return when (contentOrNull?.trim()?.lowercase()) {
        "1", "true", "yes", "active", "y" -> true
        "0", "false", "no", "inactive", "n" -> false
        else -> null
    }
}
