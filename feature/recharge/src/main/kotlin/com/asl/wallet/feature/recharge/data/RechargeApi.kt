package com.asl.wallet.feature.recharge.data

import com.asl.wallet.core.network.interceptor.ApiHeaders
import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Recharge endpoints (merchant/recharge/...). Shapes checked read-only on UAT on 4 Nov 2026 where possible.
 * The operator list is empty on UAT and the do-recharge response isn't documented, so both are read leniently.
 */
interface RechargeApi {
    @POST("merchant/recharge/operators-list")
    suspend fun operators(@Body body: JsonObject = JsonObject(emptyMap())): Response<ApiEnvelope<JsonObject>>

    @POST("merchant/recharge/fetch-operator-circle")
    suspend fun detect(@Body body: DetectRequest): Response<ApiEnvelope<DetectDto>>

    @POST("merchant/recharge/mobile/plan")
    suspend fun plans(@Body body: PlansRequest): Response<ApiEnvelope<JsonObject>>

    /**
     * DTH customer details for a subscriber ID (shared by the backend 5 Nov 2026). Answers 200 with
     * `data: {error, DATA, Message}`: error "0" = found, "1" = bad ID (Message says why), "3" / "DOWN" = provider down.
     */
    @POST("merchant/recharge/dth/customer-info")
    suspend fun dthInfo(@Body body: DthInfoRequest): Response<ApiEnvelope<JsonObject>>

    @POST("merchant/recharge/do-recharge")
    suspend fun recharge(
        @Header(ApiHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
        @Body body: RechargeRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("merchant/recharge-report")
    suspend fun recent(@Query("page") page: Int = 1, @Query("limit") limit: Int = 3): Response<ApiEnvelope<List<ReportRowDto>>>

    @GET("merchant/my-wallet/account-info")
    suspend fun accountInfo(): Response<ApiEnvelope<BalanceDto>>
}

@Serializable data class DetectRequest(val Mobileno: String)

@Serializable
data class DetectDto(
    val ERROR: JsonPrimitive? = null,
    val STATUS: JsonPrimitive? = null,
    val Operator: String? = null,
    val OpCode: JsonPrimitive? = null,
    val Circle: String? = null,
    val CircleCode: JsonPrimitive? = null,
    val Message: String? = null,
)

@Serializable data class PlansRequest(val optcode: String)

@Serializable data class DthInfoRequest(val consumer_id: String, val operator_code: String)

@Serializable
data class RechargeRequest(val custmob: String, val rechopt: String, val rechamt: Int, val rtype: Int, val conname: String? = null)

/** Only the fields the app shows. The row also carries vendor logs with server IPs (reported); they are never read. */
@Serializable
data class ReportRowDto(
    val id: String? = null,
    val servid: String? = null,
    val trandate: String? = null,
    val trantime: String? = null,
    val operatorName: String? = null,
    val operatorCode: JsonPrimitive? = null,
    val rechargeNo: String? = null,
    val amount: JsonPrimitive? = null,
    val tranStatus: String? = null,
)

@Serializable
data class BalanceDto(val available_balance_amt: JsonPrimitive? = null, val availableBalanceAmt: JsonPrimitive? = null)
