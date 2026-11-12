package com.asl.wallet.feature.bbps.data

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

/**
 * BBPS (Bharat Connect) bill payment, `merchant/bbps/...` (Postman "bbps" collection, 9 Nov 2026).
 * The collection has no sample answers, so every answer is read leniently, following the old app's shapes
 * (asl_new_app BBPS data classes): category list, biller list, biller MDM detail, fetched bill + `refid`/`xml`, payment.
 */
interface BbpsApi {
    @POST("merchant/bbps/get-biller-category")
    suspend fun categories(@Body body: JsonObject = JsonObject(emptyMap())): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/bbps/get-biller")
    suspend fun billers(@Body body: BillersRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/bbps/biller-detail")
    suspend fun billerDetail(@Body body: BillerDetailRequest): Response<ApiEnvelope<JsonElement>>

    /** Read-only at the biller: asks for the current bill. Its `refid` and `xml` must be sent back to pay that bill. */
    @POST("merchant/bbps/fetch-bill")
    suspend fun fetchBill(@Body body: FetchBillRequest): Response<ApiEnvelope<JsonElement>>

    /**
     * Moves money. One [ApiHeaders.IDEMPOTENCY_KEY] per confirmed payment; never retried by the app.
     * Returns the raw body because pay-bill does not use the standard envelope: on UAT it answers a flat object
     * whose `status` is a number (e.g. `{"status":200,"txnstatus":"...reason...","apiTrnID":"",...}`), which the
     * Boolean-`status` [ApiEnvelope] cannot parse. The repository reads it with [readPay], which handles both shapes.
     */
    @POST("merchant/bbps/pay-bill")
    suspend fun payBill(@Header(ApiHeaders.IDEMPOTENCY_KEY) idempotencyKey: String, @Body body: JsonObject): Response<JsonElement>

    @GET("merchant/my-wallet/account-info")
    suspend fun accountInfo(): Response<ApiEnvelope<BalanceDto>>
}

@Serializable data class BillersRequest(val category: String)

@Serializable data class BillerDetailRequest(val operator_code: String)

/** [postdata] keys are the biller's own parameter names ("Consumer ID", "Mobile Number" …). */
@Serializable data class FetchBillRequest(val operator_code: String, val category: String, val postdata: Map<String, String>)

@Serializable data class BalanceDto(val available_balance_amt: JsonPrimitive? = null, val availableBalanceAmt: JsonPrimitive? = null)
